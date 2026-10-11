-- Road-following route lines and drive times (OpenRouteService), computed by the `route-shape` edge function
-- whenever stops change. Riders' phones only read this table, so routing API usage doesn't grow with riders.
create table public.route_shapes (
  route_id uuid not null references public.routes on delete cascade,
  shift public.shift_kind not null,
  stops_sig text not null,          -- which stop list (ids + pins, in order) this shape was computed for
  coords jsonb not null,            -- [[lng, lat], ...] along the road
  way_points jsonb not null,        -- index into coords of each stop
  leg_seconds jsonb not null,       -- drive time between consecutive stops
  leg_meters jsonb not null,
  updated_at timestamptz not null default now(),
  primary key (route_id, shift)
);
alter table public.route_shapes enable row level security;
create policy read_all on public.route_shapes for select to authenticated using (true);
-- Writes only by the edge function (service role).

-- Fingerprint of a shift's current stop list; the edge function compares it to skip routes that haven't changed.
create function public.route_stops_sig(p_route uuid, p_shift public.shift_kind) returns text
language sql stable set search_path = public as $$
  select md5(coalesce(string_agg(id::text || ':' || round(lat::numeric, 6) || ',' || round(lng::numeric, 6), '|' order by seq), ''))
  from stops where route_id = p_route and shift = p_shift and not archived
$$;
