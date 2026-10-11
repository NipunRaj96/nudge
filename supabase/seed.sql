-- LOCAL DEV SEED ONLY (supabase db reset). Catalog (routes, stops, plans, UPI ids) comes from the migrations.
-- Demo logins (OTP 123456, see config.toml [auth.sms.test_otp]): 9876543210 rider, 7000000001 driver, 7000000002 admin.

insert into staff_invites (phone, name, role) values
  ('917000000001', 'George W.', 'driver'),
  ('917000000003', 'Marcus J.', 'driver'),
  ('917000000002', 'Admin', 'admin');

-- Auth users for the demo accounts (normally created by the first OTP login). Trigger creates profiles.
do $$
declare ph text;
begin
  foreach ph in array array['919876543210', '917000000001', '917000000002', '917000000003',
                            '919800000011', '919700000045', '919900000008', '919800000072'] loop
    insert into auth.users (instance_id, id, aud, role, phone, phone_confirmed_at, created_at, updated_at,
      raw_app_meta_data, raw_user_meta_data, confirmation_token, recovery_token, email_change_token_new,
      email_change, phone_change, phone_change_token, email_change_token_current, reauthentication_token)
    values ('00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated', ph, now(), now(), now(),
      '{"provider":"phone","providers":["phone"]}', '{}', '', '', '', '', '', '', '', '');
    insert into auth.identities (id, user_id, provider_id, provider, identity_data, created_at, updated_at, last_sign_in_at)
    select gen_random_uuid(), u.id, u.id::text, 'phone', jsonb_build_object('sub', u.id::text, 'phone', ph), now(), now(), now()
    from auth.users u where u.phone = ph;
  end loop;
end $$;

update profiles p set name = v.name from (values
  ('919876543210', 'Priyanka Sharma'), ('919800000011', 'Rahul Verma'), ('919700000045', 'Anita Rao'),
  ('919900000008', 'Sameer Khan'), ('919800000072', 'Neha Gupta')) v(phone, name)
where p.phone = v.phone;

update routes set driver_id = (select id from profiles where phone = '917000000001') where name = 'Route 1';
update routes set driver_id = (select id from profiles where phone = '917000000003') where name = 'Route 2';

-- Payments: demo rider's approved 24-ride pack, three pending, one approved, one rejected.
insert into payments (rider_id, plan_id, amount, utr, upi_vpa, status, created_at, resolved_at)
select p.id, v.plan, v.amt, v.utr, 'operator@upi', v.st::payment_status, now() - v.ago::interval,
       case when v.st <> 'pending' then now() - v.ago::interval + interval '1 hour' end
from (values
  ('919876543210', 'pack24', 2400, '408271800001', 'approved', '3 days'),
  ('919800000011', 'pack24', 2400, '408271829102', 'pending', '2 hours'),
  ('919700000045', 'pack16', 1800, '408271829555', 'pending', '3 hours'),
  ('919900000008', 'monthly', 2500, '408271830011', 'pending', '4 hours'),
  ('919800000072', 'pack24', 2400, '408271827790', 'approved', '1 day'),
  ('919800000072', 'oneway', 120, '000000000000', 'rejected', '2 days')
) v(phone, plan, amt, utr, st, ago) join profiles p on p.phone = v.phone;

insert into passes (rider_id, plan_id, payment_id, rides_total, rides_left, starts_at, expires_at, status)
select pay.rider_id, pay.plan_id, pay.id, case pl.kind when 'single' then 1 else pl.rides end,
       case pl.kind when 'rides' then pl.rides - 7 when 'single' then 1 end,
       pay.created_at, (date_trunc('month', ist_today()) + interval '1 month')::timestamp at time zone 'Asia/Kolkata',
       case pay.status when 'approved' then 'active' when 'pending' then 'pending' else 'rejected' end
from payments pay join plans pl on pl.id = pay.plan_id;

-- Demo rider history: past morning trips on route 1 from Vaishali.
do $$
declare
  r routes := (select r from routes r where name = 'Route 1');
  rider uuid := (select id from profiles where phone = '919876543210');
  pass uuid := (select id from passes where rider_id = rider);
  stop uuid := (select id from stops where route_id = r.id and shift = 'morning' and name like 'Vaishali Metro%');
  dep time := (select min(pickup_time) from stops where route_id = r.id and shift = 'morning');
  t trips;
  d int;
  st booking_status;
begin
  foreach d in array array[1, 2, 4, 7] loop
    insert into trips (route_id, service_date, shift, departs_at, vehicle_id, driver_id, status, started_at, ended_at)
    values (r.id, ist_today() - d, 'morning', ((ist_today() - d) + dep) at time zone 'Asia/Kolkata',
            r.vehicle_id, r.driver_id, 'done', now() - make_interval(days => d), now() - make_interval(days => d) + interval '95 min')
    returning * into t;
    st := case d when 4 then 'no_show' when 7 then 'cancelled' else 'boarded' end;
    insert into bookings (trip_id, rider_id, pass_id, stop_id, status, created_at, boarded_at)
    values (t.id, rider, pass, stop, st, t.departs_at - interval '1 day', case when st = 'boarded' then t.departs_at end);
  end loop;
end $$;
