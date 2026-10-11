-- Operator route editor: replace a shift's stop list in one call (keeps ids so existing bookings stay linked).
-- p_stops: [{id?: uuid, name, lat, lng, time: "HH:MM"}] in travel order. Stops left out are deleted (fails if booked).
create or replace function public.save_stops(p_route uuid, p_shift public.shift_kind, p_stops jsonb) returns void
language plpgsql security definer set search_path = public as $$
declare
  s jsonb;
  i int := 0;
  keep uuid[] := '{}';
  sid uuid;
begin
  if not is_admin() then perform fail('FORBIDDEN'); end if;
  if jsonb_array_length(p_stops) < 2 then perform fail('BAD_INPUT'); end if;
  -- park existing seqs out of the way so the unique (route, shift, seq) never collides while reordering
  update stops set seq = -seq - 1000 where route_id = p_route and shift = p_shift;
  for s in select * from jsonb_array_elements(p_stops) loop
    if coalesce(trim(s->>'name'), '') = '' or (s->>'time') is null then perform fail('BAD_INPUT'); end if;
    sid := nullif(s->>'id', '')::uuid;
    if sid is not null and exists (select 1 from stops where id = sid and route_id = p_route and shift = p_shift) then
      update stops set seq = i, name = trim(s->>'name'), lat = (s->>'lat')::float8, lng = (s->>'lng')::float8, pickup_time = (s->>'time')::time
      where id = sid;
    else
      insert into stops (route_id, shift, seq, name, lat, lng, pickup_time)
      values (p_route, p_shift, i, trim(s->>'name'), (s->>'lat')::float8, (s->>'lng')::float8, (s->>'time')::time)
      returning id into sid;
    end if;
    keep := keep || sid;
    i := i + 1;
  end loop;
  begin
    delete from stops where route_id = p_route and shift = p_shift and not (id = any(keep));
  exception when foreign_key_violation then
    perform fail('STOP_HAS_BOOKINGS');
  end;
end $$;

-- Driver's next trip: skip scheduled trips whose departure is long past (never started).
create or replace function public.driver_next_trip() returns public.trips
language plpgsql security definer set search_path = public as $$
declare
  r record;
  t trips;
begin
  for r in select id from routes where driver_id = auth.uid() and active loop
    if exists (select 1 from stops where route_id = r.id and shift = 'morning') then perform get_or_create_trip(r.id, ist_today(), 'morning'); end if;
    if exists (select 1 from stops where route_id = r.id and shift = 'evening') then perform get_or_create_trip(r.id, ist_today(), 'evening'); end if;
  end loop;
  select * into t from trips
  where driver_id = auth.uid() and service_date = ist_today()
    and (status = 'live' or (status = 'scheduled' and departs_at > now() - interval '3 hours'))
  order by status = 'live' desc, departs_at
  limit 1;
  return t;
end $$;
