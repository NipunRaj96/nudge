-- usable_pass takes any rider id: internal only (book_ride runs it as owner).
revoke execute on function public.usable_pass(uuid, timestamptz) from public, anon, authenticated;
revoke execute on function public.stop_index(uuid, public.shift_kind) from public, anon, authenticated;
revoke execute on function public.fail(text) from public, anon, authenticated;

-- Riders only need their driver's name/phone; the operator's own number stays private.
drop policy staff_visible on public.profiles;
create policy drivers_visible on public.profiles for select to authenticated using (role = 'driver');
