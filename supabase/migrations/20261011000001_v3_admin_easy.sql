-- v3: make the operator side forgiving.
--  * Cash/offline passes (grant_pass): payments.utr becomes optional, payments get a method + note.
--  * Removing a booked stop or a route with history archives it instead of failing (bookings keep their stop/trip).
--  * One-call route save (save_route) / copy, atomic plan save (save_plan), primary UPI swap.
--  * Driver: idempotent next/previous stop, trips left live overnight close themselves.

-- ── Payments: cash has no UTR
alter table public.payments drop constraint payments_utr_check;
alter table public.payments alter column utr drop not null;
alter table public.payments add constraint payments_utr_check check (utr is null or utr ~ '^[0-9]{12}$');
alter table public.payments add column method text not null default 'upi' check (method in ('upi', 'cash'));
alter table public.payments add column note text;

-- ── Archive flags. Archived stops keep a negative seq so they never collide with the live order.
alter table public.stops add column archived boolean not null default false;
alter table public.routes add column archived boolean not null default false;

-- Admin hands a rider an active pass for cash (or for testing). The rider must have logged in once.
create function public.grant_pass(p_phone text, p_plan text, p_amount int default null, p_valid_on date default null) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  ph text := '91' || right(regexp_replace(p_phone, '\D', '', 'g'), 10);
  rider uuid;
  pl plans;
  pay uuid;
  w record;
begin
  if not is_admin() then perform fail('FORBIDDEN'); end if;
  if p_amount < 0 then perform fail('BAD_INPUT'); end if;
  select id into rider from profiles where phone = ph;
  if rider is null then perform fail('NO_RIDER'); end if;
  select * into pl from plans where id = p_plan;
  if not found then perform fail('NO_PLAN'); end if;
  insert into payments (rider_id, plan_id, amount, method, status, resolved_at, resolved_by)
  values (rider, p_plan, coalesce(p_amount, plan_price(p_plan, ist_today())), 'cash', 'approved', now(), auth.uid())
  returning id into pay;
  select * into w from pass_window(p_plan, case when pl.validity = 'same_day' then coalesce(p_valid_on, ist_today()) else ist_today() end);
  insert into passes (rider_id, plan_id, payment_id, rides_total, rides_left, starts_at, expires_at, status)
  values (rider, p_plan, pay, case pl.kind when 'single' then 1 else pl.rides end,
          case pl.kind when 'single' then 1 else pl.rides end, w.starts_at, w.expires_at, 'active');
  return pay;
end $$;

-- Same as v2, but the travel-day window follows settings.booking_window_days.
create or replace function public.submit_payment(p_plan text, p_utr text, p_vpa text default null, p_valid_on date default null) returns uuid
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
  if on_day < ist_today() or on_day > ist_today() + (select booking_window_days from settings where id = 1) then
    perform fail('DATE_OUT_OF_WINDOW');
  end if;
  insert into payments (rider_id, plan_id, amount, utr, upi_vpa)
  values (auth.uid(), p_plan, plan_price(p_plan, ist_today()), p_utr, p_vpa) returning id into pay;
  select * into w from pass_window(p_plan, case when pl.validity = 'same_day' then on_day else ist_today() end);
  insert into passes (rider_id, plan_id, payment_id, rides_total, rides_left, starts_at, expires_at, status)
  values (auth.uid(), p_plan, pay, case pl.kind when 'single' then 1 else pl.rides end,
          case pl.kind when 'single' then 1 else pl.rides end, w.starts_at, w.expires_at, 'pending');
  return pay;
end $$;

-- ── Stops: removed stops that riders booked are archived (history keeps them), the rest are deleted.
-- An empty list clears the shift; exactly one stop is not a route. Pickup times must not go backwards.
create or replace function public.save_stops(p_route uuid, p_shift public.shift_kind, p_stops jsonb) returns void
language plpgsql security definer set search_path = public as $$
declare
  s jsonb;
  i int := 0;
  keep uuid[] := '{}';
  sid uuid;
  t time;
  prev time;
  base int;
begin
  if not is_admin() then perform fail('FORBIDDEN'); end if;
  if jsonb_typeof(p_stops) is distinct from 'array' then perform fail('BAD_INPUT'); end if;
  if jsonb_array_length(p_stops) = 1 then perform fail('BAD_INPUT'); end if;
  -- park live seqs out of the way so the unique (route, shift, seq) never collides while reordering
  update stops set seq = -seq - 1000 where route_id = p_route and shift = p_shift and not archived;
  for s in select * from jsonb_array_elements(p_stops) loop
    if coalesce(trim(s->>'name'), '') = '' or (s->>'time') is null then perform fail('BAD_INPUT'); end if;
    t := (s->>'time')::time;
    if t < prev then perform fail('BAD_TIMES'); end if;
    prev := t;
    sid := nullif(s->>'id', '')::uuid;
    if sid is not null and exists (select 1 from stops where id = sid and route_id = p_route and shift = p_shift) then
      update stops set seq = i, archived = false, name = trim(s->>'name'), lat = (s->>'lat')::float8, lng = (s->>'lng')::float8, pickup_time = t
      where id = sid;
    else
      insert into stops (route_id, shift, seq, name, lat, lng, pickup_time)
      values (p_route, p_shift, i, trim(s->>'name'), (s->>'lat')::float8, (s->>'lng')::float8, t)
      returning id into sid;
    end if;
    keep := keep || sid;
    i := i + 1;
  end loop;
  -- archived seqs live below -1,000,000 (parked live seqs are -1000..-1000-n), each one lower than the last
  select least(-1000000, min(seq)) into base from stops where route_id = p_route and shift = p_shift;
  update stops st set archived = true, seq = base - x.n
  from (select id, row_number() over (order by seq) n from stops
        where route_id = p_route and shift = p_shift and not archived and not (id = any(keep))
          and exists (select 1 from bookings b where b.stop_id = stops.id)) x
  where st.id = x.id;
  delete from stops where route_id = p_route and shift = p_shift and not archived and not (id = any(keep));
end $$;

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
  select min(pickup_time) into first_pickup from stops where route_id = p_route and shift = p_shift and not archived;
  if first_pickup is null then perform fail('NO_STOPS'); end if;
  insert into trips (route_id, service_date, shift, departs_at, vehicle_id, driver_id)
  values (p_route, p_date, p_shift, (p_date + first_pickup) at time zone 'Asia/Kolkata', r.vehicle_id, r.driver_id)
  on conflict (route_id, service_date, shift) do nothing;
  select * into t from trips where route_id = p_route and service_date = p_date and shift = p_shift;
  return t;
end $$;
revoke execute on function public.get_or_create_trip from public, anon, authenticated;

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
  if not exists (select 1 from routes where id = p_route and active and not archived) then perform fail('ROUTE_INACTIVE'); end if;
  if p_date < ist_today() or p_date > ist_today() + cfg.booking_window_days then perform fail('DATE_OUT_OF_WINDOW'); end if;
  select * into s from stops where id = p_stop and route_id = p_route and shift = p_shift and not archived;
  -- the last stop of a shift is only a drop point
  if not found or s.seq = (select max(seq) from stops where route_id = p_route and shift = p_shift and not archived) then
    perform fail('BAD_STOP');
  end if;

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

-- ── Routes
-- Never-used route: gone for good. Route with any booking: archived (hidden, unbookable), history intact.
create function public.delete_route(p_route uuid) returns text
language plpgsql security definer set search_path = public as $$
begin
  if not is_admin() then perform fail('FORBIDDEN'); end if;
  perform 1 from routes where id = p_route for update;
  if not found then perform fail('NOT_FOUND'); end if;
  if exists (select 1 from bookings b join trips t on t.id = b.trip_id where t.route_id = p_route) then
    update routes set archived = true, active = false where id = p_route;
    return 'archived';
  end if;
  delete from trips where route_id = p_route; -- positions cascade
  delete from routes where id = p_route;      -- stops cascade
  return 'deleted';
end $$;

-- Route editor "Save": route fields + both shifts' stops in one transaction, then re-point not-yet-run trips.
-- A missing/null "morning" or "evening" key leaves that shift's stops unchanged.
create function public.save_route(p_route jsonb) returns uuid
language plpgsql security definer set search_path = public as $$
declare rid uuid := nullif(p_route->>'id', '')::uuid;
begin
  if not is_admin() then perform fail('FORBIDDEN'); end if;
  if coalesce(trim(p_route->>'name'), '') = '' or coalesce(trim(p_route->>'from_label'), '') = ''
     or coalesce(trim(p_route->>'to_label'), '') = '' then
    perform fail('BAD_INPUT');
  end if;
  if rid is null then
    insert into routes (name, from_label, to_label, active, vehicle_id, driver_id)
    values (trim(p_route->>'name'), trim(p_route->>'from_label'), trim(p_route->>'to_label'),
            coalesce((p_route->>'active')::boolean, true), nullif(p_route->>'vehicle_id', '')::uuid, nullif(p_route->>'driver_id', '')::uuid)
    returning id into rid;
  else
    update routes set name = trim(p_route->>'name'), from_label = trim(p_route->>'from_label'), to_label = trim(p_route->>'to_label'),
                      active = coalesce((p_route->>'active')::boolean, active),
                      vehicle_id = nullif(p_route->>'vehicle_id', '')::uuid, driver_id = nullif(p_route->>'driver_id', '')::uuid
    where id = rid;
    if not found then perform fail('NOT_FOUND'); end if;
  end if;
  if coalesce(p_route->'morning', 'null') <> 'null' then perform save_stops(rid, 'morning', p_route->'morning'); end if;
  if coalesce(p_route->'evening', 'null') <> 'null' then perform save_stops(rid, 'evening', p_route->'evening'); end if;
  update trips t set driver_id = r.driver_id, vehicle_id = r.vehicle_id,
    departs_at = coalesce((t.service_date + (select min(pickup_time) from stops s
                                             where s.route_id = rid and s.shift = t.shift and not s.archived)) at time zone 'Asia/Kolkata',
                          t.departs_at) -- shift emptied: keep the old time
  from routes r
  where r.id = rid and t.route_id = rid and t.service_date >= ist_today() and t.status = 'scheduled';
  return rid;
end $$;

-- New route from an existing one (e.g. a later departure): same ends/vehicle, live stops shifted by N minutes.
-- ponytail: times wrap past midnight (time arithmetic); fine for daytime shuttles.
create function public.copy_route(p_route uuid, p_name text, p_shift_minutes int) returns uuid
language plpgsql security definer set search_path = public as $$
declare nid uuid;
begin
  if not is_admin() then perform fail('FORBIDDEN'); end if;
  if coalesce(trim(p_name), '') = '' then perform fail('BAD_INPUT'); end if;
  insert into routes (name, from_label, to_label, vehicle_id)
  select trim(p_name), from_label, to_label, vehicle_id from routes where id = p_route
  returning id into nid;
  if nid is null then perform fail('NOT_FOUND'); end if;
  insert into stops (route_id, shift, seq, name, lat, lng, pickup_time)
  select nid, shift, seq, name, lat, lng, pickup_time + make_interval(mins => coalesce(p_shift_minutes, 0))
  from stops where route_id = p_route and not archived;
  return nid;
end $$;

-- ── Driver
-- p_from: the stop the driver's screen showed; a retry or double tap that already moved on does nothing.
-- p_step: +1 next, -1 previous. Clamped to the shift's live stops.
drop function public.advance_stop(uuid);
create function public.advance_stop(p_trip uuid, p_from int default null, p_step int default 1) returns void
language plpgsql security definer set search_path = public as $$
declare t trips := assert_trip_driver(p_trip);
begin
  if t.status <> 'live' then perform fail('BAD_STATE'); end if;
  if p_from is not null and t.current_stop <> p_from then return; end if;
  update trips set current_stop = greatest(0, least(current_stop + coalesce(p_step, 1),
    (select coalesce(max(seq), 0) from stops where route_id = t.route_id and shift = t.shift and not archived)))
  where id = p_trip;
end $$;

create or replace function public.driver_next_trip() returns public.trips
language plpgsql security definer set search_path = public as $$
declare
  r record;
  t trips;
begin
  -- a trip the driver forgot to end closes itself after 12 h (waiting riders become no-shows, like end_trip)
  with stale as (
    update trips set status = 'done', ended_at = now()
    where driver_id = auth.uid() and status = 'live' and started_at < now() - interval '12 hours'
    returning id)
  update bookings set status = 'no_show' where trip_id in (select id from stale) and status = 'booked';
  for r in select id from routes where driver_id = auth.uid() and active and not archived loop
    if exists (select 1 from stops where route_id = r.id and shift = 'morning' and not archived) then perform get_or_create_trip(r.id, ist_today(), 'morning'); end if;
    if exists (select 1 from stops where route_id = r.id and shift = 'evening' and not archived) then perform get_or_create_trip(r.id, ist_today(), 'evening'); end if;
  end loop;
  -- a live trip wins whatever its date (e.g. started just before midnight)
  select * into t from trips
  where driver_id = auth.uid()
    and (status = 'live'
         or (status = 'scheduled' and service_date = ist_today() and departs_at > now() - interval '3 hours'
             and exists (select 1 from routes where id = trips.route_id and active and not archived)))
  order by status = 'live' desc, departs_at
  limit 1;
  return t;
end $$;

-- ── Staff
create or replace function public.upsert_staff(p_phone text, p_name text, p_role public.user_role) returns void
language plpgsql security definer set search_path = public as $$
declare ph text := '91' || right(regexp_replace(p_phone, '\D', '', 'g'), 10);
begin
  if not is_admin() then perform fail('FORBIDDEN'); end if;
  if length(ph) <> 12 or p_role = 'rider' or trim(p_name) = '' then perform fail('BAD_INPUT'); end if;
  -- an admin demoting themselves could leave nobody to run the app
  if ph = (select phone from profiles where id = auth.uid()) and p_role <> 'admin' then perform fail('CANT_REMOVE_SELF'); end if;
  insert into staff_invites (phone, name, role) values (ph, trim(p_name), p_role)
  on conflict (phone) do update set name = excluded.name, role = excluded.role;
  update profiles set role = p_role, name = trim(p_name) where phone = ph;
end $$;

create or replace function public.remove_staff(p_phone text) returns void
language plpgsql security definer set search_path = public as $$
declare
  ph text := '91' || right(regexp_replace(p_phone, '\D', '', 'g'), 10);
  who uuid := (select id from profiles where phone = ph);
begin
  if not is_admin() then perform fail('FORBIDDEN'); end if;
  if who = auth.uid() then perform fail('CANT_REMOVE_SELF'); end if;
  delete from staff_invites where phone = ph;
  update routes set driver_id = null where driver_id = who;
  -- upcoming trips already created must not stay assigned to them either
  update trips set driver_id = null where driver_id = who and status = 'scheduled' and service_date >= ist_today();
  update profiles set role = 'rider' where phone = ph;
end $$;

-- ── UPI
create function public.set_primary_upi(p_id uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not is_admin() then perform fail('FORBIDDEN'); end if;
  -- two statements: the one_primary_upi index is checked row by row
  update upi_accounts set is_primary = false where is_primary and id <> p_id;
  update upi_accounts set is_primary = true, active = true where id = p_id;
  if not found then perform fail('NOT_FOUND'); end if;
end $$;

-- The first UPI id added becomes primary, so riders always have one to pay to.
create function public.upi_default_primary() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if not exists (select 1 from upi_accounts where is_primary) then new.is_primary := true; end if;
  return new;
end $$;
create trigger upi_default_primary before insert on public.upi_accounts
  for each row execute function public.upi_default_primary();
revoke execute on function public.upi_default_primary() from public, anon, authenticated;

-- ── Plans: plan + price tiers saved together (no half-saved tier table).
create function public.save_plan(p_plan jsonb) returns void
language plpgsql security definer set search_path = public as $$
declare
  pid text := trim(p_plan->>'id');
  k text := p_plan->>'kind';
  v text := p_plan->>'validity';
  tiers jsonb := case when jsonb_typeof(p_plan->'tiers') = 'array' then p_plan->'tiers' else '[]' end;
begin
  if not is_admin() then perform fail('FORBIDDEN'); end if;
  if k = 'single' then v := 'same_day'; tiers := '[]'; end if; -- one-way: valid on its travel day, flat price
  if coalesce(pid, '') = '' or coalesce(trim(p_plan->>'title'), '') = '' or coalesce((p_plan->>'price')::int, 0) <= 0
     or coalesce(k, '') not in ('unlimited', 'rides', 'single') or coalesce(v, '') not in ('month_end', 'same_day', 'days')
     or (k = 'rides' and coalesce((p_plan->>'rides')::int, 0) <= 0)
     or coalesce((p_plan->>'validity_days')::int, 30) <= 0 then
    perform fail('BAD_INPUT');
  end if;
  -- each tier inside 1..31 with from <= to; sorted by from_day, any overlap shows up between neighbours
  if exists (select 1 from jsonb_to_recordset(tiers) x(from_day int, to_day int, price int)
             where not coalesce(from_day between 1 and to_day and to_day <= 31 and price > 0, false))
     or exists (select 1 from (select from_day, lag(to_day) over (order by from_day) prev_to
                               from jsonb_to_recordset(tiers) x(from_day int, to_day int)) y
                where from_day <= prev_to) then
    perform fail('BAD_TIERS');
  end if;
  insert into plans (id, title, kind, rides, price, validity, validity_days, description, popular, active, sort)
  values (pid, trim(p_plan->>'title'), k, case when k = 'rides' then (p_plan->>'rides')::int end, (p_plan->>'price')::int, v,
          coalesce((p_plan->>'validity_days')::int, 30), coalesce(p_plan->>'description', ''),
          coalesce((p_plan->>'popular')::boolean, false), coalesce((p_plan->>'active')::boolean, true), coalesce((p_plan->>'sort')::int, 0))
  on conflict (id) do update set title = excluded.title, kind = excluded.kind, rides = excluded.rides, price = excluded.price,
    validity = excluded.validity, validity_days = excluded.validity_days, description = excluded.description,
    popular = excluded.popular, active = excluded.active, sort = excluded.sort;
  delete from plan_price_tiers where plan_id = pid;
  insert into plan_price_tiers (plan_id, from_day, to_day, price)
  select pid, from_day, to_day, price from jsonb_to_recordset(tiers) x(from_day int, to_day int, price int);
end $$;

revoke execute on all functions in schema public from anon;
