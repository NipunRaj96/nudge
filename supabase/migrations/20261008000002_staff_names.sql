-- Riders see their driver's name on routes and live tracking. Staff (driver/admin) profiles are readable by
-- everyone signed in; rider profiles stay private.
create policy staff_visible on public.profiles for select to authenticated using (role <> 'rider');
