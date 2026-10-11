// End-to-end check of auth + RLS + RPC rules against the LOCAL stack. Needs a fresh seed:
//   supabase db reset && node supabase/tests/smoke.mjs
import assert from "node:assert/strict";

const URL = "http://127.0.0.1:54321";
const KEY = process.env.SUPABASE_KEY ?? "sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH";

async function call(path, { token, method = "GET", body } = {}) {
  const res = await fetch(URL + path, {
    method,
    headers: { apikey: KEY, Authorization: `Bearer ${token ?? KEY}`, "Content-Type": "application/json", Prefer: "return=representation" },
    body: body && JSON.stringify(body),
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  return { ok: res.ok, status: res.status, data };
}

async function login(phone) {
  assert.ok((await call("/auth/v1/otp", { method: "POST", body: { phone } })).ok);
  const r = await call("/auth/v1/verify", { method: "POST", body: { phone, token: "123456", type: "sms" } });
  assert.ok(r.ok, JSON.stringify(r.data));
  return r.data.access_token;
}

const rpc = (fn, token, body = {}) => call(`/rest/v1/rpc/${fn}`, { token, method: "POST", body });
const fails = async (p, code) => {
  const r = await p;
  assert.equal(r.ok, false, `expected ${code}`);
  assert.equal(r.data.message, code);
};
const istDate = (addDays) => new Date(Date.now() + 5.5 * 3600e3 + addDays * 86400e3).toISOString().slice(0, 10);

const rider = await login("919876543210");
const fresh = await login("919000000099");
const driver = await login("917000000001");
const admin = await login("917000000002");
// Run every day while testing, so results don't depend on today's weekday (service days are tested at the end).
const setDays = (days) => call("/rest/v1/settings?id=eq.1", { token: admin, method: "PATCH", body: { service_days: days } });
assert.ok((await setDays([1, 2, 3, 4, 5, 6, 7])).ok);

// Roles come from staff_invites.
const me = async (t, phone) => (await call(`/rest/v1/profiles?select=role,name&phone=eq.${phone}`, { token: t })).data;
assert.equal((await me(driver, "917000000001"))[0].role, "driver");
assert.equal((await me(admin, "917000000002"))[0].role, "admin");
assert.deepEqual(await me(fresh, "919000000099"), [{ role: "rider", name: "" }]);
assert.equal((await call("/rest/v1/profiles?select=id", { token: admin })).data.length, 9);

// Riders cannot promote themselves or see other riders' payments.
assert.equal((await call("/rest/v1/profiles?phone=eq.919000000099", { token: fresh, method: "PATCH", body: { role: "admin" } })).ok, false);
assert.ok((await call("/rest/v1/profiles?phone=eq.919000000099", { token: fresh, method: "PATCH", body: { name: "Test Rider" } })).ok);
assert.equal((await call("/rest/v1/payments?select=id", { token: fresh })).data.length, 0);
assert.equal((await rpc("usable_pass", fresh, { p_rider: "00000000-0000-0000-0000-000000000000", at: "2026-01-01T00:00:00Z" })).status, 403);
assert.deepEqual((await call("/rest/v1/profiles?select=role&role=eq.admin", { token: fresh })).data, []); // operator stays private

// Booking rules (stops are per shift; the last stop of a shift is drop-only).
const route = (await call("/rest/v1/routes?select=id,stops(id,seq,shift)&name=eq.Route%201", { token: rider })).data[0];
const stop = (seq, shift = "morning") => route.stops.find((s) => s.seq === seq && s.shift === shift).id;
const lastSeq = Math.max(...route.stops.filter((s) => s.shift === "morning").map((s) => s.seq));
const ridesLeft = async (t = rider) => (await call("/rest/v1/passes?select=rides_left&status=neq.rejected&order=expires_at", { token: t })).data[0].rides_left;
const before = await ridesLeft();
const tomorrow = istDate(1);
const book = (t, date = tomorrow, s = stop(0), shift = "morning") => rpc("book_ride", t, { p_route: route.id, p_date: date, p_shift: shift, p_stop: s });

const b1 = await book(rider);
assert.ok(b1.ok, JSON.stringify(b1.data));
assert.equal(await ridesLeft(), before - 1);
await fails(book(rider), "ALREADY_BOOKED");
await fails(book(rider, istDate(2), stop(lastSeq)), "BAD_STOP");
await fails(book(rider, istDate(2), stop(0), "evening"), "BAD_STOP"); // morning stop id used for evening
await fails(book(rider, istDate(30)), "DATE_OUT_OF_WINDOW");
await fails(book(fresh), "NO_PASS");
assert.ok((await rpc("cancel_booking", rider, { p_booking: b1.data })).ok);
assert.equal(await ridesLeft(), before); // ride credited back, no money refund
await fails(rpc("cancel_booking", rider, { p_booking: b1.data }), "NOT_CANCELLABLE");

// Pricing: tier by day of month; one-way valid only on its travel day.
const q = (await rpc("plan_quote", fresh, { p_plan: "monthly" })).data[0];
const day = Number(istDate(0).slice(8, 10));
assert.equal(q.price, day <= 10 ? 3000 : day <= 20 ? 2500 : 2000);

// One-way: pay for tomorrow, seat is held right away on the pending pass.
await fails(rpc("submit_payment", fresh, { p_plan: "oneway", p_utr: "123" }), "BAD_UTR");
await fails(rpc("submit_payment", fresh, { p_plan: "oneway", p_utr: "408271829102" }), "UTR_USED");
const ow = await rpc("submit_payment", fresh, { p_plan: "oneway", p_utr: "555566667777", p_vpa: "operator@upi", p_valid_on: tomorrow });
assert.ok(ow.ok, JSON.stringify(ow.data));
await fails(book(fresh, istDate(2)), "NO_PASS"); // not valid the day after
const owBooking = await book(fresh);
assert.ok(owBooking.ok, JSON.stringify(owBooking.data));
await fails(rpc("resolve_payment", fresh, { p_payment: ow.data, p_approve: true }), "FORBIDDEN");
assert.ok((await rpc("resolve_payment", admin, { p_payment: ow.data, p_approve: false })).ok); // fake UTR
assert.equal((await call(`/rest/v1/bookings?select=status&id=eq.${owBooking.data}`, { token: fresh })).data[0].status, "cancelled");
await fails(rpc("resolve_payment", admin, { p_payment: ow.data, p_approve: true }), "ALREADY_RESOLVED");

// Ride pack: approve -> active, month-end validity.
const pay = await rpc("submit_payment", fresh, { p_plan: "pack16", p_utr: "555566667778" });
assert.ok(pay.ok, JSON.stringify(pay.data));
assert.ok((await rpc("resolve_payment", admin, { p_payment: pay.data, p_approve: true })).ok);
const freshPass = (await call("/rest/v1/passes?select=rides_left,status,expires_at,payments(amount)&plan_id=eq.pack16", { token: fresh })).data[0];
assert.equal(freshPass.rides_left, 16);
assert.equal(freshPass.status, "active");
assert.equal(freshPass.payments.amount, 1800);

// Operator: staff management.
await fails(rpc("upsert_staff", fresh, { p_phone: "9111111111", p_name: "X", p_role: "driver" }), "FORBIDDEN");
assert.ok((await rpc("upsert_staff", admin, { p_phone: "+91 91111 11111", p_name: "New Driver", p_role: "driver" })).ok);
assert.ok((await rpc("staff_list", admin)).data.some((s) => s.phone === "919111111111" && !s.joined));
await fails(rpc("remove_staff", admin, { p_phone: "7000000002" }), "CANT_REMOVE_SELF");
assert.ok((await rpc("remove_staff", admin, { p_phone: "9111111111" })).ok);

// Driver: today's trip, manifest, boarding, GPS, end.
let t0 = (await rpc("driver_next_trip", driver)).data;
if (!t0.id || t0.service_date !== istDate(0)) {
  // Late in the day (the next trip is another day's): give the driver a route that departs in an hour (also exercises the route editor RPC).
  // After 22:00 IST use an hour ago instead, so both pickups stay before midnight (times must not go backwards).
  const off = new Date(Date.now() + 5.5 * 3600e3).getUTCHours() >= 22 ? -60 : 60;
  const hhmm = (addMin) => new Date(Date.now() + 5.5 * 3600e3 + (addMin - 60 + off) * 60e3).toISOString().slice(11, 16);
  const george = (await rpc("staff_list", admin)).data.find((s) => s.phone === "917000000001").id;
  const r = await call("/rest/v1/routes", { token: admin, method: "POST", body: { name: "Smoke Route", from_label: "A", to_label: "B", driver_id: george } });
  const rid = r.data[0].id;
  assert.ok((await rpc("save_stops", admin, { p_route: rid, p_shift: "morning", p_stops: [
    { name: "Smoke A", lat: 28.6, lng: 77.4, time: hhmm(60) }, { name: "Smoke B", lat: 28.5, lng: 77.3, time: hhmm(90) },
  ] })).ok);
  t0 = (await rpc("driver_next_trip", driver)).data;
}
assert.ok(t0.id, JSON.stringify(t0));
const today = istDate(0);
if (new Date(t0.departs_at) > new Date()) {
  const first = (await call(`/rest/v1/stops?select=id&route_id=eq.${t0.route_id}&shift=eq.${t0.shift}&seq=eq.0`, { token: fresh })).data[0].id;
  const b = await rpc("book_ride", fresh, { p_route: t0.route_id, p_date: today, p_shift: t0.shift, p_stop: first });
  assert.ok(b.ok, JSON.stringify(b.data));
}
await fails(rpc("start_trip", rider, { p_trip: t0.id }), "FORBIDDEN");
assert.ok((await rpc("start_trip", driver, { p_trip: t0.id })).ok);
const manifest = (await rpc("trip_manifest", driver, { p_trip: t0.id })).data;
assert.ok(Array.isArray(manifest));
await fails(rpc("trip_manifest", rider, { p_trip: t0.id }), "FORBIDDEN");
assert.ok((await call("/rest/v1/positions", { token: driver, method: "POST", body: { trip_id: t0.id, lat: 28.6, lng: 77.3, speed_kmh: 30 } })).ok);
assert.equal((await call("/rest/v1/positions", { token: rider, method: "POST", body: { trip_id: t0.id, lat: 1, lng: 1 } })).ok, false);
if (manifest.length) assert.ok((await rpc("set_boarding", driver, { p_booking: manifest[0].booking_id, p_status: "boarded" })).ok);
assert.ok((await rpc("advance_stop", driver, { p_trip: t0.id })).ok);
assert.ok((await rpc("end_trip", driver, { p_trip: t0.id })).ok);
assert.equal((await call(`/rest/v1/trips?select=status&id=eq.${t0.id}`, { token: rider })).data[0].status, "done");

// ── v3 operator helpers
const adminGet = async (path) => (await call(path, { token: admin })).data;

// Plans: atomic save with tiers; overlapping tiers rejected.
const smokePlan = { id: "smoke10", title: "Smoke 10", kind: "rides", rides: 10, price: 500, validity: "days", validity_days: 10,
  description: "", popular: false, active: true, sort: 9, tiers: [{ from_day: 1, to_day: 15, price: 600 }, { from_day: 16, to_day: 31, price: 400 }] };
await fails(rpc("save_plan", admin, { p_plan: { ...smokePlan, tiers: [{ from_day: 1, to_day: 10, price: 1 }, { from_day: 10, to_day: 20, price: 1 }] } }), "BAD_TIERS");
await fails(rpc("save_plan", rider, { p_plan: smokePlan }), "FORBIDDEN");
assert.ok((await rpc("save_plan", admin, { p_plan: smokePlan })).ok);
assert.equal((await adminGet("/rest/v1/plan_price_tiers?select=price&plan_id=eq.smoke10")).length, 2);

// Cash pass: admin only, rider must exist, pass is active right away, no UTR.
await fails(rpc("grant_pass", rider, { p_phone: "9000000099", p_plan: "smoke10" }), "FORBIDDEN");
await fails(rpc("grant_pass", admin, { p_phone: "9555555555", p_plan: "smoke10" }), "NO_RIDER");
await fails(rpc("grant_pass", admin, { p_phone: "9000000099", p_plan: "nope" }), "NO_PLAN");
const gp = await rpc("grant_pass", admin, { p_phone: "+91 90000 00099", p_plan: "smoke10" });
assert.ok(gp.ok, JSON.stringify(gp.data));
const gpay = (await adminGet(`/rest/v1/payments?select=method,utr,status,amount,passes(status,rides_left)&id=eq.${gp.data}`))[0];
assert.equal(gpay.method, "cash");
assert.equal(gpay.utr, null);
assert.equal(gpay.status, "approved");
assert.equal(gpay.amount, day <= 15 ? 600 : 400);
assert.deepEqual(gpay.passes, { status: "active", rides_left: 10 });

// Route editor: one-call save (fixed morning times, always bookable tomorrow).
const st = (name, time, id) => ({ id, name, lat: 28.6, lng: 77.4, time });
const smokeRoute = { id: null, name: "Smoke Route 2", from_label: "A", to_label: "B", active: true, vehicle_id: null, driver_id: null,
  morning: [st("S1", "07:00"), st("S2", "07:10"), st("S3", "07:20")], evening: [] };
await fails(rpc("save_route", admin, { p_route: { ...smokeRoute, morning: [st("S1", "07:00")] } }), "BAD_INPUT");
await fails(rpc("save_route", admin, { p_route: { ...smokeRoute, morning: [st("S1", "07:10"), st("S2", "07:00")] } }), "BAD_TIMES");
await fails(rpc("save_route", admin, { p_route: { ...smokeRoute, name: " " } }), "BAD_INPUT");
const rid = (await rpc("save_route", admin, { p_route: smokeRoute })).data;
assert.ok(rid);
const liveStops = async (r = rid) => adminGet(`/rest/v1/stops?select=id,name,seq,pickup_time&route_id=eq.${r}&archived=eq.false&order=shift,seq`);
let sStops = await liveStops();
assert.deepEqual(sStops.map((s) => s.name), ["S1", "S2", "S3"]);

// Booking S1 tomorrow creates the trip (no driver yet).
const sb = await rpc("book_ride", fresh, { p_route: rid, p_date: tomorrow, p_shift: "morning", p_stop: sStops[0].id });
assert.ok(sb.ok, JSON.stringify(sb.data));

// Update: drop booked S1 (archived, not deleted), new first stop, assign the driver -> the trip follows.
const georgeId = (await rpc("staff_list", admin)).data.find((s) => s.phone === "917000000001").id;
const upd = await rpc("save_route", admin, { p_route: { ...smokeRoute, id: rid, name: "Smoke Route 2b", driver_id: georgeId,
  morning: [st("S0", "06:50"), st("S2", "07:10", sStops[1].id), st("S3", "07:20", sStops[2].id)] } });
assert.equal(upd.data, rid);
assert.equal((await adminGet(`/rest/v1/routes?select=name&id=eq.${rid}`))[0].name, "Smoke Route 2b");
const s1Id = sStops[0].id;
const s1 = (await adminGet(`/rest/v1/stops?select=archived,seq&id=eq.${s1Id}`))[0];
assert.ok(s1.archived && s1.seq < 0, JSON.stringify(s1));
sStops = await liveStops();
assert.deepEqual(sStops.map((s) => [s.name, s.seq]), [["S0", 0], ["S2", 1], ["S3", 2]]);
await fails(rpc("book_ride", rider, { p_route: rid, p_date: tomorrow, p_shift: "morning", p_stop: s1Id }), "BAD_STOP");
const sTrip = (await adminGet(`/rest/v1/trips?select=id,driver_id,departs_at&route_id=eq.${rid}&service_date=eq.${tomorrow}`))[0];
assert.equal(sTrip.driver_id, georgeId);
assert.equal(new Date(sTrip.departs_at).toISOString(), new Date(`${tomorrow}T06:50:00+05:30`).toISOString());

// Copy with a 30 min later timetable.
const cid = (await rpc("copy_route", admin, { p_route: rid, p_name: "Smoke Route 3", p_shift_minutes: 30 })).data;
assert.deepEqual((await liveStops(cid)).map((s) => s.pickup_time), ["07:20:00", "07:40:00", "07:50:00"]);

// Driver: a trip starts only on its own day; a live trip from another date is still "next"; next/previous stop is
// idempotent and clamped.
await fails(rpc("start_trip", driver, { p_trip: sTrip.id }), "NOT_TODAY");
// Today's trip on this route (assigned for the day), started even though its time has passed: live wins as "next".
assert.ok((await rpc("set_trip_driver", admin, { p_route: rid, p_date: today, p_shift: "morning", p_driver: georgeId })).ok);
const tTrip = (await adminGet(`/rest/v1/trips?select=id&route_id=eq.${rid}&service_date=eq.${today}`))[0];
assert.ok((await rpc("start_trip", driver, { p_trip: tTrip.id })).ok);
assert.equal((await rpc("driver_next_trip", driver)).data.id, tTrip.id);
const cur = async () => (await adminGet(`/rest/v1/trips?select=current_stop&id=eq.${tTrip.id}`))[0].current_stop;
const adv = (body) => rpc("advance_stop", driver, { p_trip: tTrip.id, ...body });
assert.ok((await adv({ p_from: 0 })).ok);
assert.ok((await adv({ p_from: 0 })).ok); // retry of the same tap: no double move
assert.equal(await cur(), 1);
assert.ok((await adv({ p_from: 1, p_step: -1 })).ok);
assert.equal(await cur(), 0);
assert.ok((await adv({ p_step: -1 })).ok);
assert.equal(await cur(), 0);
assert.ok((await adv({ p_step: 5 })).ok);
assert.equal(await cur(), 2);
assert.ok((await rpc("end_trip", driver, { p_trip: tTrip.id })).ok);

// Delete: route with bookings is archived (unbookable), unused copy is really deleted.
await fails(rpc("delete_route", rider, { p_route: cid }), "FORBIDDEN");
assert.equal((await rpc("delete_route", admin, { p_route: rid })).data, "archived");
await fails(rpc("book_ride", rider, { p_route: rid, p_date: istDate(2), p_shift: "morning", p_stop: sStops[0].id }), "ROUTE_INACTIVE");
assert.equal((await rpc("delete_route", admin, { p_route: cid })).data, "deleted");
assert.deepEqual(await adminGet(`/rest/v1/routes?select=id&id=eq.${cid}`), []);

// UPI: primary swap.
const upis = await adminGet("/rest/v1/upi_accounts?select=id,is_primary");
const other = upis.find((u) => !u.is_primary);
assert.ok((await rpc("set_primary_upi", admin, { p_id: other.id })).ok);
assert.deepEqual((await adminGet("/rest/v1/upi_accounts?select=id&is_primary=eq.true")).map((u) => u.id), [other.id]);

// Staff: an admin cannot demote themselves.
await fails(rpc("upsert_staff", admin, { p_phone: "7000000002", p_name: "Admin", p_role: "driver" }), "CANT_REMOVE_SELF");

// Road paths: operator-only edge function; with ORS_API_KEY set (a mock in tests) it fills route_shapes, and a
// second call skips everything because no stops changed. Without a key it reports it skipped.
const shapes = (t) => call("/functions/v1/route-shape", { token: t, method: "POST", body: {} });
assert.equal((await shapes(rider)).status, 403);
const sync = (await shapes(admin)).data;
if (sync.skipped) console.log("route-shape:", sync.skipped);
else {
  assert.ok(sync.updated >= 2 && sync.failed.length === 0, JSON.stringify(sync));
  assert.equal((await shapes(admin)).data.updated, 0);
  const sh = (await call(`/rest/v1/route_shapes?select=way_points,leg_seconds&route_id=eq.${route.id}&shift=eq.morning`, { token: rider })).data[0];
  assert.equal(sh.way_points.length, route.stops.filter((s) => s.shift === "morning").length);
  assert.equal(sh.leg_seconds.length, sh.way_points.length - 1);
}

// Service days: no seats and no driver changes on a day the shuttle doesn't run.
const dayAfter = istDate(2);
const isoDow = (d) => ((new Date(`${d}T12:00:00Z`).getUTCDay() + 6) % 7) + 1;
const r1 = (await call("/rest/v1/routes?select=id,driver_id,stops(id,seq,shift)&name=eq.Route%201", { token: admin })).data[0];
const r1Stop = r1.stops.find((x) => x.shift === "morning" && x.seq === 0).id;
assert.ok((await setDays([1, 2, 3, 4, 5, 6, 7].filter((d) => d !== isoDow(dayAfter)))).ok);
await fails(rpc("book_ride", fresh, { p_route: r1.id, p_date: dayAfter, p_shift: "morning", p_stop: r1Stop }), "NO_SERVICE");
await fails(rpc("set_trip_driver", admin, { p_route: r1.id, p_date: dayAfter, p_shift: "morning", p_driver: null }), "NO_SERVICE");
assert.ok((await setDays([1, 2, 3, 4, 5, 6, 7])).ok);

// Driver for one day: Marcus drives Route 1 the day after tomorrow, and keeps it when the route is saved again.
const marcus = (await rpc("staff_list", admin)).data.find((x) => x.phone === "917000000003").id;
await fails(rpc("set_trip_driver", rider, { p_route: r1.id, p_date: dayAfter, p_shift: "morning", p_driver: marcus }), "FORBIDDEN");
await fails(rpc("set_trip_driver", admin, { p_route: r1.id, p_date: istDate(-1), p_shift: "morning", p_driver: marcus }), "BAD_INPUT");
assert.ok((await rpc("set_trip_driver", admin, { p_route: r1.id, p_date: dayAfter, p_shift: "morning", p_driver: marcus })).ok);
const dayTrip = async () => (await adminGet(`/rest/v1/trips?select=driver_id,driver_override&route_id=eq.${r1.id}&service_date=eq.${dayAfter}&shift=eq.morning`))[0];
assert.deepEqual(await dayTrip(), { driver_id: marcus, driver_override: true });
assert.ok((await rpc("save_route", admin, { p_route: { id: r1.id, name: "Route 1", from_label: "Indirapuram", to_label: "Noida Sector 62", driver_id: r1.driver_id } })).ok);
assert.equal((await dayTrip()).driver_id, marcus);
// back to the usual driver: no longer an override
assert.ok((await rpc("set_trip_driver", admin, { p_route: r1.id, p_date: dayAfter, p_shift: "morning", p_driver: r1.driver_id })).ok);
assert.deepEqual(await dayTrip(), { driver_id: r1.driver_id, driver_override: false });

console.log("smoke OK");
