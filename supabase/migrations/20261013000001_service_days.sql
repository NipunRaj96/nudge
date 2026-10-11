-- Service days (the shuttle doesn't run every day) and per-day driver changes.

-- ISO weekdays the shuttle runs on (1 = Monday ... 7 = Sunday). Editable in the app (Rules).
alter table public.settings add column service_days int[] not null default '{1,2,3,4,5}'
  check (cardinality(service_days) > 0 and service_days <@ '{1,2,3,4,5,6,7}');

create function public.is_service_day(d date) returns boolean
language sql stable security definer set search_path = public as $$
  select extract(isodow from d)::int = any(service_days) from settings where id = 1
$$;

-- Trips created on days the shuttle doesn't run, with nobody booked, are dropped (they showed as "delayed").
delete from public.trips t
where t.status = 'scheduled' and t.service_date >= ist_today() and not is_service_day(t.service_date)
  and not exists (select 1 from public.bookings b where b.trip_id = t.id);

-- No seats on days without service (book_ride creates the trip first, so check at the booking itself).
create function public.bookings_service_day() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if (select service_date >= ist_today() and not is_service_day(service_date) from trips where id = new.trip_id) then
    perform fail('NO_SERVICE');
  end if;
  return new;
end $$;
create trigger bookings_service_day before insert on public.bookings
  for each row execute function public.bookings_service_day();
revoke execute on function public.bookings_service_day() from public, anon, authenticated;

-- A driver set for one day (set_trip_driver) stays when the route's usual driver changes.
alter table public.trips add column driver_override boolean not null default false;

create or replace function public.save_route(p_route jsonb) returns uuid
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
  update trips t set driver_id = case when t.driver_override then t.driver_id else r.driver_id end, vehicle_id = r.vehicle_id,
    departs_at = coalesce((t.service_date + (select min(pickup_time) from stops s
                                             where s.route_id = rid and s.shift = t.shift and not s.archived)) at time zone 'Asia/Kolkata',
                          t.departs_at) -- shift emptied: keep the old time
  from routes r
  where r.id = rid and t.route_id = rid and t.service_date >= ist_today() and t.status = 'scheduled';
  return rid;
end $$;

-- Operator: who drives a route on one day and shift (null = nobody). Creates the trip if it doesn't exist yet.
create function public.set_trip_driver(p_route uuid, p_date date, p_shift public.shift_kind, p_driver uuid) returns void
language plpgsql security definer set search_path = public as $$
declare t trips;
begin
  if not is_admin() then perform fail('FORBIDDEN'); end if;
  if p_date < ist_today() then perform fail('BAD_INPUT'); end if;
  if not is_service_day(p_date) then perform fail('NO_SERVICE'); end if;
  if p_driver is not null and not exists (select 1 from profiles where id = p_driver and role = 'driver') then perform fail('BAD_INPUT'); end if;
  t := get_or_create_trip(p_route, p_date, p_shift);
  if t.status <> 'scheduled' then perform fail('BAD_STATE'); end if;
  update trips set driver_id = p_driver,
    driver_override = p_driver is distinct from (select driver_id from routes where id = p_route)
  where id = t.id;
end $$;

-- The driver's next trip: a live one, else the next scheduled trip on a service day (today's if it hasn't long
-- passed, otherwise the next service day's), so the app always shows what's coming instead of an empty screen.
create or replace function public.driver_next_trip() returns public.trips
language plpgsql security definer set search_path = public as $$
declare
  r record;
  t trips;
  d date;
  days int := 0;
begin
  -- a trip the driver forgot to end closes itself after 12 h (waiting riders become no-shows, like end_trip)
  with stale as (
    update trips set status = 'done', ended_at = now()
    where driver_id = auth.uid() and status = 'live' and started_at < now() - interval '12 hours'
    returning id)
  update bookings set status = 'no_show' where trip_id in (select id from stale) and status = 'booked';
  -- trips for the routes they usually drive, on the next two service days (today included)
  for d in select ist_today() + i from generate_series(0, 14) i loop
    continue when not is_service_day(d);
    for r in select id from routes where driver_id = auth.uid() and active and not archived loop
      if exists (select 1 from stops where route_id = r.id and shift = 'morning' and not archived) then perform get_or_create_trip(r.id, d, 'morning'); end if;
      if exists (select 1 from stops where route_id = r.id and shift = 'evening' and not archived) then perform get_or_create_trip(r.id, d, 'evening'); end if;
    end loop;
    days := days + 1;
    exit when days = 2;
  end loop;
  -- a live trip wins whatever its date (e.g. started just before midnight)
  select * into t from trips
  where driver_id = auth.uid()
    and (status = 'live'
         or (status = 'scheduled' and service_date >= ist_today() and departs_at > now() - interval '3 hours'
             and is_service_day(service_date)
             and exists (select 1 from routes where id = trips.route_id and active and not archived)))
  order by status = 'live' desc, departs_at
  limit 1;
  return t;
end $$;

-- A trip can only be started on its own day.
create or replace function public.start_trip(p_trip uuid) returns void
language plpgsql security definer set search_path = public as $$
declare t trips := assert_trip_driver(p_trip);
begin
  if t.status <> 'scheduled' then perform fail('BAD_STATE'); end if;
  if t.service_date <> ist_today() then perform fail('NOT_TODAY'); end if;
  update trips set status = 'live', started_at = now(), current_stop = 0 where id = p_trip;
end $$;

revoke execute on all functions in schema public from anon;
