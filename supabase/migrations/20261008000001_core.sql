-- Nudge core schema: users/roles, routes, plans, payments (manual UPI), passes, trips, bookings, GPS positions.
-- Business rules live in SQL functions so every client gets the same checks. Times are IST (Asia/Kolkata).
-- Clients only read tables (RLS) and write through the RPC functions below, except positions (driver insert) and
-- admin edits to routes/plans/settings/vehicles.

create type public.user_role as enum ('rider', 'driver', 'admin');
create type public.payment_status as enum ('pending', 'approved', 'rejected');
create type public.shift_kind as enum ('morning', 'evening');
create type public.trip_status as enum ('scheduled', 'live', 'done', 'cancelled');
create type public.booking_status as enum ('booked', 'boarded', 'no_show', 'cancelled');

-- ── Config (one row). Owner edits these in the Supabase table editor.
create table public.settings (
  id int primary key default 1 check (id = 1),
  walk_on_price int not null default 129,
  upi_id text not null default 'nudge@upi',
  payee_name text not null default 'Nudge Transport',
  pass_validity_days int not null default 30,
  cancel_cutoff_min int not null default 60,
  booking_window_days int not null default 7
);

-- ── People. Phone is E.164 without '+', as Supabase Auth stores it (e.g. 919876543210).
create table public.profiles (
  id uuid primary key references auth.users on delete cascade,
  phone text unique not null,
  name text not null default '',
  role public.user_role not null default 'rider',
  created_at timestamptz not null default now()
);

-- Owner pre-registers drivers/admins here; their first OTP login picks up the role and name.
create table public.staff_invites (
  phone text primary key,
  name text not null,
  role public.user_role not null
);

create function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into profiles (id, phone, name, role)
  select new.id, coalesce(new.phone, ''), coalesce(i.name, ''), coalesce(i.role, 'rider')
  from (select 1) one left join staff_invites i on i.phone = new.phone;
  return new;
end $$;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

create function public.my_role() returns public.user_role
language sql stable security definer set search_path = public as $$
  select role from profiles where id = auth.uid()
$$;

create function public.is_admin() returns boolean
language sql stable as $$ select coalesce(public.my_role() = 'admin', false) $$;

-- ── Fleet & routes
create table public.vehicles (
  id uuid primary key default gen_random_uuid(),
  code text unique not null,
  seats int not null default 16 check (seats > 0),
  active boolean not null default true
);

create table public.routes (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  from_label text not null,
  to_label text not null,
  morning_time time not null,
  evening_time time not null,
  duration_mins int not null,
  active boolean not null default true,
  vehicle_id uuid references public.vehicles,
  driver_id uuid references public.profiles
);

-- Stops in morning travel order (seq 0 = first pickup, last = destination). Evening runs them in reverse.
create table public.stops (
  id uuid primary key default gen_random_uuid(),
  route_id uuid not null references public.routes on delete cascade,
  seq int not null,
  name text not null,
  lat double precision not null,
  lng double precision not null,
  minutes_from_start int not null default 0,
  unique (route_id, seq)
);

-- ── Plans, payments, passes
create table public.plans (
  id text primary key,
  title text not null,
  rides int check (rides > 0), -- null = unlimited
  price int not null check (price > 0),
  per_ride_note text not null default '',
  popular boolean not null default false,
  active boolean not null default true,
  sort int not null default 0
);

create table public.payments (
  id uuid primary key default gen_random_uuid(),
  rider_id uuid not null references public.profiles on delete cascade,
  plan_id text references public.plans, -- null = walk-on single ride
  amount int not null,
  utr text not null unique check (utr ~ '^[0-9]{12}$'), -- a UTR can only be claimed once
  status public.payment_status not null default 'pending',
  created_at timestamptz not null default now(),
  resolved_at timestamptz,
  resolved_by uuid references public.profiles
);
create index on public.payments (rider_id);
create index on public.payments (status, created_at desc);

create table public.passes (
  id uuid primary key default gen_random_uuid(),
  rider_id uuid not null references public.profiles on delete cascade,
  plan_id text references public.plans,
  payment_id uuid unique references public.payments,
  rides_total int, -- null = unlimited
  rides_left int check (rides_left >= 0),
  starts_at timestamptz not null default now(),
  expires_at timestamptz not null
);
create index on public.passes (rider_id, expires_at);

-- ── Trips (one per route/date/shift, created on demand) and bookings
create table public.trips (
  id uuid primary key default gen_random_uuid(),
  route_id uuid not null references public.routes,
  service_date date not null,
  shift public.shift_kind not null,
  departs_at timestamptz not null,
  vehicle_id uuid references public.vehicles,
  driver_id uuid references public.profiles,
  status public.trip_status not null default 'scheduled',
  current_stop int not null default 0, -- index in travel order
  started_at timestamptz,
  ended_at timestamptz,
  unique (route_id, service_date, shift)
);
create index on public.trips (driver_id, service_date);

create table public.bookings (
  id uuid primary key default gen_random_uuid(),
  trip_id uuid not null references public.trips,
  rider_id uuid not null references public.profiles on delete cascade,
  pass_id uuid not null references public.passes,
  stop_id uuid not null references public.stops,
  status public.booking_status not null default 'booked',
  created_at timestamptz not null default now(),
  boarded_at timestamptz,
  cancelled_at timestamptz
);
create unique index one_live_booking_per_trip on public.bookings (trip_id, rider_id) where status <> 'cancelled';
create index on public.bookings (rider_id, created_at desc);

-- ── Live GPS from the driver phone. Also the source for route playback.
-- ponytail: ~1 row / 5 s per live shuttle (~15k rows/day for 10 shuttles); add a cleanup cron if storage gets tight.
create table public.positions (
  id bigint generated always as identity primary key,
  trip_id uuid not null references public.trips on delete cascade,
  lat double precision not null,
  lng double precision not null,
  speed_kmh real,
  heading real,
  recorded_at timestamptz not null default now()
);
create index on public.positions (trip_id, recorded_at);

-- ── RLS
alter table public.settings enable row level security;
alter table public.profiles enable row level security;
alter table public.staff_invites enable row level security;
alter table public.vehicles enable row level security;
alter table public.routes enable row level security;
alter table public.stops enable row level security;
alter table public.plans enable row level security;
alter table public.payments enable row level security;
alter table public.passes enable row level security;
alter table public.trips enable row level security;
alter table public.bookings enable row level security;
alter table public.positions enable row level security;

-- Shared catalogue: everyone signed in reads, admins edit.
create policy read_all on public.settings for select to authenticated using (true);
create policy read_all on public.vehicles for select to authenticated using (true);
create policy read_all on public.routes for select to authenticated using (true);
create policy read_all on public.stops for select to authenticated using (true);
create policy read_all on public.plans for select to authenticated using (true);
create policy read_all on public.trips for select to authenticated using (true);
create policy read_all on public.positions for select to authenticated using (true);
create policy admin_write on public.settings for all to authenticated using (public.is_admin()) with check (public.is_admin());
create policy admin_write on public.vehicles for all to authenticated using (public.is_admin()) with check (public.is_admin());
create policy admin_write on public.routes for all to authenticated using (public.is_admin()) with check (public.is_admin());
create policy admin_write on public.stops for all to authenticated using (public.is_admin()) with check (public.is_admin());
create policy admin_write on public.plans for all to authenticated using (public.is_admin()) with check (public.is_admin());
create policy admin_write on public.staff_invites for all to authenticated using (public.is_admin()) with check (public.is_admin());

-- Own rows (admins see all). Writes go through the RPC functions.
create policy own_or_admin on public.profiles for select to authenticated using (id = auth.uid() or public.is_admin());
create policy update_self on public.profiles for update to authenticated using (id = auth.uid()) with check (id = auth.uid());
revoke update on public.profiles from authenticated;
grant update (name) on public.profiles to authenticated; -- riders can set their name, never their role
create policy own_or_admin on public.payments for select to authenticated using (rider_id = auth.uid() or public.is_admin());
create policy own_or_admin on public.passes for select to authenticated using (rider_id = auth.uid() or public.is_admin());
create policy own_or_admin on public.bookings for select to authenticated using (rider_id = auth.uid() or public.is_admin());

-- Only the trip's driver, while the trip is live, can add GPS points.
create policy driver_insert on public.positions for insert to authenticated with check (
  exists (select 1 from public.trips t where t.id = trip_id and t.driver_id = auth.uid() and t.status = 'live')
);

alter publication supabase_realtime add table public.positions, public.trips;

-- ── Helpers
create function public.ist_today() returns date language sql stable as $$
  select (now() at time zone 'Asia/Kolkata')::date
$$;

create function public.fail(code text) returns void language plpgsql as $$
begin
  raise exception using message = code, errcode = 'P0001';
end $$;

-- Pass that pays for a ride departing at `at`: soonest-expiring pass that is valid then and has rides left.
create function public.usable_pass(p_rider uuid, at timestamptz) returns public.passes
language sql stable security definer set search_path = public as $$
  select * from passes
  where rider_id = p_rider and starts_at <= now() and expires_at >= at and (rides_left is null or rides_left > 0)
  order by expires_at, rides_left nulls last
  limit 1
$$;

create function public.get_or_create_trip(p_route uuid, p_date date, p_shift public.shift_kind) returns public.trips
language plpgsql security definer set search_path = public as $$
declare
  r routes;
  t trips;
begin
  select * into t from trips where route_id = p_route and service_date = p_date and shift = p_shift;
  if found then return t; end if;
  select * into r from routes where id = p_route;
  if not found then perform fail('NO_ROUTE'); end if;
  insert into trips (route_id, service_date, shift, departs_at, vehicle_id, driver_id)
  values (p_route, p_date, p_shift,
          (p_date + case p_shift when 'morning' then r.morning_time else r.evening_time end) at time zone 'Asia/Kolkata',
          r.vehicle_id, r.driver_id)
  on conflict (route_id, service_date, shift) do nothing;
  select * into t from trips where route_id = p_route and service_date = p_date and shift = p_shift;
  return t;
end $$;
revoke execute on function public.get_or_create_trip from public, anon, authenticated;

-- Position of a stop in travel order for a shift.
create function public.stop_index(p_stop uuid, p_shift public.shift_kind) returns int
language sql stable security definer set search_path = public as $$
  select case p_shift when 'morning' then s.seq
         else (select max(seq) from stops where route_id = s.route_id) - s.seq end
  from stops s where s.id = p_stop
$$;

-- ── Rider RPCs
create function public.book_ride(p_route uuid, p_date date, p_shift public.shift_kind, p_stop uuid) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  cfg settings;
  t trips;
  p passes;
  seats int;
  taken int;
  last_idx int;
  booking uuid;
begin
  if me is null then perform fail('NOT_SIGNED_IN'); end if;
  select * into cfg from settings where id = 1;
  if not exists (select 1 from routes where id = p_route and active) then perform fail('ROUTE_INACTIVE'); end if;
  if p_date < ist_today() or p_date > ist_today() + cfg.booking_window_days then perform fail('DATE_OUT_OF_WINDOW'); end if;
  select max(seq) into last_idx from stops where route_id = p_route;
  if not exists (select 1 from stops where id = p_stop and route_id = p_route) or stop_index(p_stop, p_shift) = last_idx then
    perform fail('BAD_STOP');
  end if;

  t := get_or_create_trip(p_route, p_date, p_shift);
  select * into t from trips where id = t.id for update; -- serialises bookings on this trip (capacity)
  if t.status <> 'scheduled' or t.departs_at <= now() then perform fail('TRIP_CLOSED'); end if;
  if exists (select 1 from bookings where trip_id = t.id and rider_id = me and status <> 'cancelled') then
    perform fail('ALREADY_BOOKED');
  end if;
  select coalesce(v.seats, 16) into seats from (select 1) one left join vehicles v on v.id = t.vehicle_id;
  select count(*) into taken from bookings where trip_id = t.id and status <> 'cancelled';
  if taken >= seats then perform fail('TRIP_FULL'); end if;

  p := usable_pass(me, t.departs_at);
  if p.id is null then perform fail('NO_PASS'); end if;
  update passes set rides_left = rides_left - 1 where id = p.id and rides_left is not null;
  insert into bookings (trip_id, rider_id, pass_id, stop_id) values (t.id, me, p.id, p_stop) returning id into booking;
  return booking;
end $$;

create function public.cancel_booking(p_booking uuid) returns void
language plpgsql security definer set search_path = public as $$
declare
  b bookings;
  dep timestamptz;
  cutoff int;
begin
  select * into b from bookings where id = p_booking and rider_id = auth.uid() for update;
  if not found then perform fail('NOT_FOUND'); end if;
  if b.status <> 'booked' then perform fail('NOT_CANCELLABLE'); end if;
  select departs_at into dep from trips where id = b.trip_id;
  select cancel_cutoff_min into cutoff from settings where id = 1;
  if now() > dep - make_interval(mins => cutoff) then perform fail('TOO_LATE'); end if;
  update bookings set status = 'cancelled', cancelled_at = now() where id = p_booking;
  update passes set rides_left = rides_left + 1 where id = b.pass_id and rides_left is not null;
end $$;

-- p_plan null = walk-on single ride. Amount always comes from the server price.
create function public.submit_payment(p_plan text, p_utr text) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  amt int;
  pay uuid;
begin
  if auth.uid() is null then perform fail('NOT_SIGNED_IN'); end if;
  if p_utr !~ '^[0-9]{12}$' then perform fail('BAD_UTR'); end if;
  if exists (select 1 from payments where rider_id = auth.uid() and status = 'pending') then perform fail('PENDING_EXISTS'); end if;
  if exists (select 1 from payments where utr = p_utr) then perform fail('UTR_USED'); end if;
  if p_plan is null then
    select walk_on_price into amt from settings where id = 1;
  else
    select price into amt from plans where id = p_plan and active;
    if amt is null then perform fail('NO_PLAN'); end if;
  end if;
  insert into payments (rider_id, plan_id, amount, utr) values (auth.uid(), p_plan, amt, p_utr) returning id into pay;
  return pay;
end $$;

-- ── Admin RPCs
create function public.resolve_payment(p_payment uuid, p_approve boolean) returns void
language plpgsql security definer set search_path = public as $$
declare
  pay payments;
  n int;
  days int;
begin
  if not is_admin() then perform fail('FORBIDDEN'); end if;
  select * into pay from payments where id = p_payment for update;
  if not found then perform fail('NOT_FOUND'); end if;
  if pay.status <> 'pending' then perform fail('ALREADY_RESOLVED'); end if;
  update payments set status = case when p_approve then 'approved'::payment_status else 'rejected' end,
                      resolved_at = now(), resolved_by = auth.uid()
  where id = p_payment;
  if p_approve then
    select pass_validity_days into days from settings where id = 1;
    n := case when pay.plan_id is null then 1 else (select rides from plans where id = pay.plan_id) end;
    insert into passes (rider_id, plan_id, payment_id, rides_total, rides_left, expires_at)
    values (pay.rider_id, pay.plan_id, pay.id, n, n, now() + make_interval(days => days));
  end if;
end $$;

-- ── Driver RPCs
create function public.assert_trip_driver(p_trip uuid) returns public.trips
language plpgsql security definer set search_path = public as $$
declare t trips;
begin
  select * into t from trips where id = p_trip for update;
  if not found then perform fail('NOT_FOUND'); end if;
  if t.driver_id is distinct from auth.uid() and not is_admin() then perform fail('FORBIDDEN'); end if;
  return t;
end $$;
revoke execute on function public.assert_trip_driver from public, anon, authenticated;

-- Today's next trip for the signed-in driver (creates today's trips for their routes if needed).
create function public.driver_next_trip() returns public.trips
language plpgsql security definer set search_path = public as $$
declare
  r record;
  t trips;
begin
  for r in select id from routes where driver_id = auth.uid() and active loop
    perform get_or_create_trip(r.id, ist_today(), 'morning');
    perform get_or_create_trip(r.id, ist_today(), 'evening');
  end loop;
  select * into t from trips
  where driver_id = auth.uid() and service_date = ist_today() and status in ('scheduled', 'live')
  order by status = 'live' desc, departs_at
  limit 1;
  return t;
end $$;

create function public.trip_manifest(p_trip uuid)
returns table (booking_id uuid, rider_name text, stop_id uuid, stop_name text, stop_index int, status public.booking_status, boarded_at timestamptz)
language plpgsql stable security definer set search_path = public as $$
declare t trips;
begin
  select * into t from trips where id = p_trip;
  if t.driver_id is distinct from auth.uid() and not is_admin() then perform fail('FORBIDDEN'); end if;
  return query
    select b.id, coalesce(nullif(p.name, ''), 'Rider ' || right(p.phone, 4)), s.id, s.name, stop_index(s.id, t.shift), b.status, b.boarded_at
    from bookings b join profiles p on p.id = b.rider_id join stops s on s.id = b.stop_id
    where b.trip_id = p_trip and b.status <> 'cancelled'
    order by 5, 2;
end $$;

create function public.start_trip(p_trip uuid) returns void
language plpgsql security definer set search_path = public as $$
declare t trips := assert_trip_driver(p_trip);
begin
  if t.status <> 'scheduled' then perform fail('BAD_STATE'); end if;
  update trips set status = 'live', started_at = now(), current_stop = 0 where id = p_trip;
end $$;

create function public.set_boarding(p_booking uuid, p_status public.booking_status) returns void
language plpgsql security definer set search_path = public as $$
declare
  b bookings;
  t trips;
begin
  if p_status = 'cancelled' then perform fail('BAD_STATE'); end if;
  select * into b from bookings where id = p_booking;
  if not found then perform fail('NOT_FOUND'); end if;
  t := assert_trip_driver(b.trip_id);
  if t.status <> 'live' or b.status = 'cancelled' then perform fail('BAD_STATE'); end if;
  update bookings set status = p_status, boarded_at = case when p_status = 'boarded' then now() end where id = p_booking;
end $$;

create function public.advance_stop(p_trip uuid) returns void
language plpgsql security definer set search_path = public as $$
declare t trips := assert_trip_driver(p_trip);
begin
  if t.status <> 'live' then perform fail('BAD_STATE'); end if;
  update trips set current_stop = least(current_stop + 1, (select max(seq) from stops where route_id = t.route_id))
  where id = p_trip;
end $$;

-- Riders still waiting when the trip ends are no-shows (their ride stays used).
create function public.end_trip(p_trip uuid) returns void
language plpgsql security definer set search_path = public as $$
declare t trips := assert_trip_driver(p_trip);
begin
  if t.status <> 'live' then perform fail('BAD_STATE'); end if;
  update trips set status = 'done', ended_at = now() where id = p_trip;
  update bookings set status = 'no_show' where trip_id = p_trip and status = 'booked';
end $$;

revoke execute on all functions in schema public from anon;
