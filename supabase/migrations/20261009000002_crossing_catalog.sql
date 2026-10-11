-- Sample catalog for a fresh project: 2 routes, plans and placeholder UPI ids.
-- Runs once (only when no routes exist). Everything here is editable from the app's operator screens afterwards.
-- Replace it from the operator screens (routes can be imported from a PDF timetable).
do $$
begin
  if exists (select 1 from public.routes) then return; end if;

  update public.settings set support_name = 'Shuttle Office', support_phone = '910000000000', cancel_cutoff_min = 15 where id = 1;
  insert into public.settings (id, support_name, support_phone) values (1, 'Shuttle Office', '910000000000') on conflict (id) do nothing;

  insert into public.upi_accounts (vpa, payee_name, is_primary) values
    ('operator@upi', 'Shuttle Operator', true),
    ('operator2@upi', 'Second Account', false);

  insert into public.plans (id, title, kind, rides, price, validity, description, popular, sort) values
    ('monthly', 'Monthly Unlimited', 'unlimited', null, 3000, 'month_end', 'Unlimited rides till month end. Cheaper later in the month.', false, 1),
    ('pack24', '24 Rides', 'rides', 24, 2400, 'month_end', '24 rides till month end', true, 2),
    ('pack16', '16 Rides', 'rides', 16, 1800, 'month_end', '16 rides till month end', false, 3),
    ('oneway', 'One-way ride', 'single', null, 120, 'same_day', 'One ride on the day you travel', false, 4)
  on conflict (id) do nothing;
  insert into public.plan_price_tiers (plan_id, from_day, to_day, price) values
    ('monthly', 1, 10, 3000), ('monthly', 11, 20, 2500), ('monthly', 21, 31, 2000)
  on conflict do nothing;

  insert into public.vehicles (code, seats) values ('SH-1', 16), ('SH-2', 16) on conflict (code) do nothing;
  insert into public.routes (name, from_label, to_label, vehicle_id) values
    ('Route 1', 'Indirapuram', 'Noida Sector 62', (select id from public.vehicles where code = 'SH-1')),
    ('Route 2', 'Noida City Centre', 'Noida Sector 135', (select id from public.vehicles where code = 'SH-2'));

  insert into public.stops (route_id, shift, seq, name, lat, lng, pickup_time)
  select r.id, v.shift::public.shift_kind, v.seq, v.name, v.lat, v.lng, v.t::time
  from (values
    (1, 'morning', 0, 'Shipra Mall Indirapuram', 28.6345, 77.3700, '08:00'),
    (1, 'morning', 1, 'Vaishali Metro Station', 28.6499, 77.3396, '08:10'),
    (1, 'morning', 2, 'Kaushambi Metro Station', 28.6450, 77.3245, '08:15'),
    (1, 'morning', 3, 'Electronic City Metro Station', 28.6280, 77.3726, '08:30'),
    (1, 'morning', 4, 'Sector 62 Noida', 28.6188, 77.3730, '08:35'),
    (1, 'evening', 0, 'Sector 62 Noida', 28.6188, 77.3730, '18:00'),
    (1, 'evening', 1, 'Electronic City Metro Station', 28.6280, 77.3726, '18:05'),
    (1, 'evening', 2, 'Kaushambi Metro Station', 28.6450, 77.3245, '18:20'),
    (1, 'evening', 3, 'Vaishali Metro Station', 28.6499, 77.3396, '18:25'),
    (1, 'evening', 4, 'Shipra Mall Indirapuram', 28.6345, 77.3700, '18:35'),
    (2, 'morning', 0, 'Noida City Centre Metro Station', 28.5747, 77.3560, '08:30'),
    (2, 'morning', 1, 'Botanical Garden Metro Station', 28.5640, 77.3340, '08:40'),
    (2, 'morning', 2, 'Sector 125 Noida', 28.5440, 77.3290, '08:50'),
    (2, 'morning', 3, 'Sector 132 Noida', 28.5140, 77.3815, '09:00'),
    (2, 'morning', 4, 'Sector 135 Noida', 28.4995, 77.4020, '09:10'),
    (2, 'evening', 0, 'Sector 135 Noida', 28.4995, 77.4020, '18:30'),
    (2, 'evening', 1, 'Sector 132 Noida', 28.5140, 77.3815, '18:40'),
    (2, 'evening', 2, 'Sector 125 Noida', 28.5440, 77.3290, '18:50'),
    (2, 'evening', 3, 'Botanical Garden Metro Station', 28.5640, 77.3340, '19:00'),
    (2, 'evening', 4, 'Noida City Centre Metro Station', 28.5747, 77.3560, '19:10')
  ) v(route_no, shift, seq, name, lat, lng, t)
  join public.routes r on r.name = 'Route ' || v.route_no;
end $$;
