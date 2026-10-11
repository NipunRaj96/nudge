-- v2: operator model (routes with morning and evening stop lists, UPI accounts, plans).
--  * Each shift has its own ordered stop list with a scheduled pickup time (timetables differ morning/evening).
--  * Plans: unlimited / ride packs / one-way; price can depend on the day of month; validity = month end, same day or N days.
--  * Several UPI accounts, managed by the operator.
--  * A pass exists from the moment a rider submits a UTR (status pending); riders can book on a pending pass
--    (setting), exactly like today's "pay + share screenshot + add name to the list". Rejecting the payment
--    cancels that pass's upcoming bookings.
--  * Operator manages staff, plans, UPI ids, routes, stops and rules from the app.

-- ── Stops per shift
alter table public.stops add column shift public.shift_kind not null default 'morning';
alter table public.stops add column pickup_time time;
alter table public.stops drop constraint stops_route_id_seq_key;
alter table public.stops add constraint stops_route_shift_seq unique (route_id, shift, seq);
alter table public.stops drop column minutes_from_start;
-- Departure = first pickup of the shift; duration = last - first. Derived, so no separate columns to drift.
alter table public.routes drop column morning_time, drop column evening_time, drop column duration_mins;

-- ── Plans
alter table public.plans add column kind text not null default 'rides' check (kind in ('unlimited', 'rides', 'single'));
alter table public.plans add column validity text not null default 'month_end' check (validity in ('month_end', 'same_day', 'days'));
alter table public.plans add column validity_days int not null default 30 check (validity_days > 0);
alter table public.plans add column description text not null default '';
alter table public.plans drop column per_ride_note;

-- Price by day of month of the purchase (e.g. 1-10 ₹3499, 11-20 ₹2499, 21-31 ₹1999). No tier = plans.price.
create table public.plan_price_tiers (
  plan_id text not null references public.plans on delete cascade,
  from_day int not null check (from_day between 1 and 31),
  to_day int not null check (to_day between 1 and 31),
  price int not null check (price > 0),
  primary key (plan_id, from_day),
  check (to_day >= from_day)
);
alter table public.plan_price_tiers enable row level security;
create policy read_all on public.plan_price_tiers for select to authenticated using (true);
create policy admin_write on public.plan_price_tiers for all to authenticated using (public.is_admin()) with check (public.is_admin());

-- ── UPI accounts
create table public.upi_accounts (
  id uuid primary key default gen_random_uuid(),
  vpa text not null unique check (vpa ~ '^[A-Za-z0-9._-]+@[A-Za-z0-9.-]+$'),
  payee_name text not null,
  active boolean not null default true,
  is_primary boolean not null default false,
  created_at timestamptz not null default now()
);
create unique index one_primary_upi on public.upi_accounts (is_primary) where is_primary;
alter table public.upi_accounts enable row level security;
create policy read_all on public.upi_accounts for select to authenticated using (true);
create policy admin_write on public.upi_accounts for all to authenticated using (public.is_admin()) with check (public.is_admin());

-- ── Settings
alter table public.settings drop column walk_on_price, drop column upi_id, drop column payee_name, drop column pass_validity_days;
alter table public.settings add column support_name text not null default 'Shuttle office';
alter table public.settings add column support_phone text not null default '';
alter table public.settings add column allow_pending_booking boolean not null default true;
alter table public.settings alter column cancel_cutoff_min set default 15;

-- ── Payments & passes
delete from public.payments where plan_id is null; -- old walk-on rows (dev data only); one-way is a plan now
alter table public.payments alter column plan_id set not null;
alter table public.payments add column upi_vpa text;
alter table public.passes add column status text not null default 'active' check (status in ('pending', 'active', 'rejected'));
create index on public.passes (payment_id);

-- ── Pricing helpers
create or replace function public.plan_price(p_plan text, p_on date) returns int
language sql stable security definer set search_path = public as $$
  select coalesce(
    (select price from plan_price_tiers where plan_id = p_plan and extract(day from p_on) between from_day and to_day order by from_day limit 1),
    (select price from plans where id = p_plan))
$$;

-- Start/end of a pass bought on p_on (IST). One-way passes are valid on the travel day p_on.
create or replace function public.pass_window(p_plan text, p_on date, out starts_at timestamptz, out expires_at timestamptz)
language plpgsql stable security definer set search_path = public as $$
declare pl plans;
begin
  select * into pl from plans where id = p_plan;
  case pl.validity
    when 'month_end' then
      starts_at := now();
      expires_at := (date_trunc('month', p_on) + interval '1 month')::timestamp at time zone 'Asia/Kolkata';
    when 'same_day' then
      starts_at := least(now(), p_on::timestamp at time zone 'Asia/Kolkata');
      expires_at := (p_on + 1)::timestamp at time zone 'Asia/Kolkata';
    else
      starts_at := now();
      expires_at := now() + make_interval(days => pl.validity_days);
  end case;
end $$;

-- What the rider pays today and until when it is valid (shown before paying).
create or replace function public.plan_quote(p_plan text, p_on date default null)
returns table (price int, valid_until timestamptz)
language sql stable security definer set search_path = public as $$
  select plan_price(p_plan, ist_today()), (pass_window(p_plan, coalesce(p_on, ist_today()))).expires_at
$$;

create or replace function public.usable_pass(p_rider uuid, at timestamptz) returns public.passes
language sql stable security definer set search_path = public as $$
  select p.* from passes p, settings s
  where s.id = 1 and p.rider_id = p_rider and p.starts_at <= greatest(now(), at) and p.expires_at >= at
    and (p.rides_left is null or p.rides_left > 0)
    and (p.status = 'active' or (p.status = 'pending' and s.allow_pending_booking))
  order by p.expires_at, p.rides_left nulls last
  limit 1
$$;
revoke execute on function public.usable_pass(uuid, timestamptz) from public, anon, authenticated;

-- ── Trips use the shift's first pickup as departure
create or replace function public.get_or_create_trip(p_route uuid, p_date date, p_shift public.shift_kind) returns public.trips
language plpgsql security definer set search_path = public as $$
declare
  r routes;
  t trips;
  first_pickup time;
begin
  select * into t from trips where route_id = p_route and service_date = p_date and shift = p_shift;
  if found then return t; end if;
  select * into r from routes where id = p_route;
  if not found then perform fail('NO_ROUTE'); end if;
  select min(pickup_time) into first_pickup from stops where route_id = p_route and shift = p_shift;
  if first_pickup is null then perform fail('NO_STOPS'); end if;
  insert into trips (route_id, service_date, shift, departs_at, vehicle_id, driver_id)
  values (p_route, p_date, p_shift, (p_date + first_pickup) at time zone 'Asia/Kolkata', r.vehicle_id, r.driver_id)
  on conflict (route_id, service_date, shift) do nothing;
  select * into t from trips where route_id = p_route and service_date = p_date and shift = p_shift;
  return t;
end $$;
revoke execute on function public.get_or_create_trip from public, anon, authenticated;

drop function public.stop_index(uuid, public.shift_kind);

create or replace function public.book_ride(p_route uuid, p_date date, p_shift public.shift_kind, p_stop uuid) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  cfg settings;
  t trips;
  p passes;
  s stops;
  seats int;
  taken int;
  booking uuid;
begin
  if me is null then perform fail('NOT_SIGNED_IN'); end if;
  select * into cfg from settings where id = 1;
  if not exists (select 1 from routes where id = p_route and active) then perform fail('ROUTE_INACTIVE'); end if;
  if p_date < ist_today() or p_date > ist_today() + cfg.booking_window_days then perform fail('DATE_OUT_OF_WINDOW'); end if;
  select * into s from stops where id = p_stop and route_id = p_route and shift = p_shift;
  -- the last stop of a shift is only a drop point
  if not found or s.seq = (select max(seq) from stops where route_id = p_route and shift = p_shift) then perform fail('BAD_STOP'); end if;

  t := get_or_create_trip(p_route, p_date, p_shift);
  select * into t from trips where id = t.id for update; -- serialises bookings on this trip (capacity)
  if t.status <> 'scheduled' or (p_date + s.pickup_time) at time zone 'Asia/Kolkata' <= now() then perform fail('TRIP_CLOSED'); end if;
  if exists (select 1 from bookings where trip_id = t.id and rider_id = me and status <> 'cancelled') then perform fail('ALREADY_BOOKED'); end if;
  select coalesce(v.seats, 16) into seats from (select 1) one left join vehicles v on v.id = t.vehicle_id;
  select count(*) into taken from bookings where trip_id = t.id and status <> 'cancelled';
  if taken >= seats then perform fail('TRIP_FULL'); end if;

  p := usable_pass(me, t.departs_at);
  if p.id is null then perform fail('NO_PASS'); end if;
  update passes set rides_left = rides_left - 1 where id = p.id and rides_left is not null;
  insert into bookings (trip_id, rider_id, pass_id, stop_id) values (t.id, me, p.id, p_stop) returning id into booking;
  return booking;
end $$;

-- Cancelling never refunds money; a counted ride goes back to the pass.
create or replace function public.cancel_booking(p_booking uuid) returns void
language plpgsql security definer set search_path = public as $$
declare
  b bookings;
  pickup timestamptz;
  cutoff int;
begin
  select * into b from bookings where id = p_booking and rider_id = auth.uid() for update;
  if not found then perform fail('NOT_FOUND'); end if;
  if b.status <> 'booked' then perform fail('NOT_CANCELLABLE'); end if;
  select (t.service_date + s.pickup_time) at time zone 'Asia/Kolkata' into pickup
  from trips t, stops s where t.id = b.trip_id and s.id = b.stop_id;
  select cancel_cutoff_min into cutoff from settings where id = 1;
  if now() > pickup - make_interval(mins => cutoff) then perform fail('TOO_LATE'); end if;
  update bookings set status = 'cancelled', cancelled_at = now() where id = p_booking;
  update passes set rides_left = rides_left + 1 where id = b.pass_id and rides_left is not null;
end $$;

drop function public.submit_payment(text, text);
-- p_valid_on: travel day for one-way plans (default today). Returns the payment id.
create function public.submit_payment(p_plan text, p_utr text, p_vpa text default null, p_valid_on date default null) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  pl plans;
  pay uuid;
  w record;
  on_day date := coalesce(p_valid_on, ist_today());
begin
  if auth.uid() is null then perform fail('NOT_SIGNED_IN'); end if;
  if p_utr !~ '^[0-9]{12}$' then perform fail('BAD_UTR'); end if;
  if exists (select 1 from payments where utr = p_utr) then perform fail('UTR_USED'); end if;
  select * into pl from plans where id = p_plan and active;
  if not found then perform fail('NO_PLAN'); end if;
  if on_day < ist_today() or on_day > ist_today() + 7 then perform fail('DATE_OUT_OF_WINDOW'); end if;
  insert into payments (rider_id, plan_id, amount, utr, upi_vpa)
  values (auth.uid(), p_plan, plan_price(p_plan, ist_today()), p_utr, p_vpa) returning id into pay;
  select * into w from pass_window(p_plan, case when pl.validity = 'same_day' then on_day else ist_today() end);
  insert into passes (rider_id, plan_id, payment_id, rides_total, rides_left, starts_at, expires_at, status)
  values (auth.uid(), p_plan, pay, case pl.kind when 'single' then 1 else pl.rides end,
          case pl.kind when 'single' then 1 else pl.rides end, w.starts_at, w.expires_at, 'pending');
  return pay;
end $$;

create or replace function public.resolve_payment(p_payment uuid, p_approve boolean) returns void
language plpgsql security definer set search_path = public as $$
declare pay payments;
begin
  if not is_admin() then perform fail('FORBIDDEN'); end if;
  select * into pay from payments where id = p_payment for update;
  if not found then perform fail('NOT_FOUND'); end if;
  if pay.status <> 'pending' then perform fail('ALREADY_RESOLVED'); end if;
  update payments set status = case when p_approve then 'approved'::payment_status else 'rejected' end,
                      resolved_at = now(), resolved_by = auth.uid()
  where id = p_payment;
  update passes set status = case when p_approve then 'active' else 'rejected' end where payment_id = p_payment;
  if not p_approve then
    update bookings b set status = 'cancelled', cancelled_at = now()
    from passes p, trips t
    where p.payment_id = p_payment and b.pass_id = p.id and b.trip_id = t.id and b.status = 'booked' and t.status <> 'done';
  end if;
end $$;

-- ── Driver side: stop order is the shift's own seq
drop function public.trip_manifest(uuid);
create function public.trip_manifest(p_trip uuid)
returns table (booking_id uuid, rider_name text, rider_phone text, stop_id uuid, stop_name text, stop_index int,
               status public.booking_status, boarded_at timestamptz, pass_status text, plan_kind text)
language plpgsql stable security definer set search_path = public as $$
declare t trips;
begin
  select * into t from trips where id = p_trip;
  if t.driver_id is distinct from auth.uid() and not is_admin() then perform fail('FORBIDDEN'); end if;
  return query
    select b.id, coalesce(nullif(pr.name, ''), 'Rider ' || right(pr.phone, 4)), pr.phone, s.id, s.name, s.seq, b.status, b.boarded_at,
           pa.status, coalesce(pl.kind, 'rides')
    from bookings b
    join profiles pr on pr.id = b.rider_id
    join stops s on s.id = b.stop_id
    join passes pa on pa.id = b.pass_id
    left join plans pl on pl.id = pa.plan_id
    where b.trip_id = p_trip and b.status <> 'cancelled'
    order by 6, 2;
end $$;

create or replace function public.advance_stop(p_trip uuid) returns void
language plpgsql security definer set search_path = public as $$
declare t trips := assert_trip_driver(p_trip);
begin
  if t.status <> 'live' then perform fail('BAD_STATE'); end if;
  update trips set current_stop = least(current_stop + 1, (select max(seq) from stops where route_id = t.route_id and shift = t.shift))
  where id = p_trip;
end $$;

-- ── Operator: staff
-- Add or update a driver/admin by phone (10 digits or 91XXXXXXXXXX). Applies now if they already signed up.
create or replace function public.upsert_staff(p_phone text, p_name text, p_role public.user_role) returns void
language plpgsql security definer set search_path = public as $$
declare ph text := '91' || right(regexp_replace(p_phone, '\D', '', 'g'), 10);
begin
  if not is_admin() then perform fail('FORBIDDEN'); end if;
  if length(ph) <> 12 or p_role = 'rider' or trim(p_name) = '' then perform fail('BAD_INPUT'); end if;
  insert into staff_invites (phone, name, role) values (ph, trim(p_name), p_role)
  on conflict (phone) do update set name = excluded.name, role = excluded.role;
  update profiles set role = p_role, name = trim(p_name) where phone = ph;
end $$;

create or replace function public.remove_staff(p_phone text) returns void
language plpgsql security definer set search_path = public as $$
declare ph text := '91' || right(regexp_replace(p_phone, '\D', '', 'g'), 10);
begin
  if not is_admin() then perform fail('FORBIDDEN'); end if;
  if ph = (select phone from profiles where id = auth.uid()) then perform fail('CANT_REMOVE_SELF'); end if;
  delete from staff_invites where phone = ph;
  update routes set driver_id = null where driver_id = (select id from profiles where phone = ph);
  update profiles set role = 'rider' where phone = ph;
end $$;

-- Staff list for the operator: signed-up staff plus invites not used yet.
create or replace function public.staff_list()
returns table (id uuid, phone text, name text, role public.user_role, joined boolean)
language plpgsql stable security definer set search_path = public as $$
begin
  if not is_admin() then perform fail('FORBIDDEN'); end if;
  return query
    select p.id, i.phone, coalesce(nullif(p.name, ''), i.name), i.role, p.id is not null
    from staff_invites i left join profiles p on p.phone = i.phone
    union
    select p.id, p.phone, p.name, p.role, true from profiles p
    where p.role <> 'rider' and not exists (select 1 from staff_invites i where i.phone = p.phone)
    order by 4, 3;
end $$;

revoke execute on function public.pass_window(text, date) from public, anon, authenticated;
revoke execute on all functions in schema public from anon;
