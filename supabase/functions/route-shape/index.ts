// Computes road-following route lines + stop-to-stop drive times with OpenRouteService and stores them in
// `route_shapes`. Called by the operator app after routes change; only shifts whose stops changed hit the API.
// Secrets: ORS_API_KEY (free key from openrouteservice.org). ORS_BASE_URL only overrides the host for tests.
import { createClient } from "jsr:@supabase/supabase-js@2";

const orsKey = Deno.env.get("ORS_API_KEY") ?? "";
const orsBase = Deno.env.get("ORS_BASE_URL") || "https://api.openrouteservice.org";
const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
const MAX_WAYPOINTS = 50; // free-plan limit per directions request

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

type Leg = { coords: number[][]; wayPoints: number[]; secs: number[]; meters: number[] };

async function directions(points: number[][]): Promise<Leg> {
  const res = await fetch(`${orsBase}/v2/directions/driving-car/geojson`, {
    method: "POST",
    headers: { Authorization: orsKey, "Content-Type": "application/json" },
    // radiuses -1: snap each stop to the nearest road however far (some pins sit inside campuses).
    body: JSON.stringify({ coordinates: points, radiuses: points.map(() => -1), instructions: false }),
  });
  if (!res.ok) throw new Error(`ORS ${res.status}: ${(await res.text()).slice(0, 200)}`);
  const f = (await res.json()).features[0];
  const segs = f.properties.segments as { distance: number; duration: number }[];
  return {
    coords: f.geometry.coordinates.map(([lng, lat]: number[]) => [+lng.toFixed(5), +lat.toFixed(5)]),
    wayPoints: f.properties.way_points,
    secs: segs.map((s) => Math.round(s.duration)),
    meters: segs.map((s) => Math.round(s.distance)),
  };
}

/** Whole stop list, split into overlapping chunks when it exceeds the per-request waypoint limit. */
async function route(points: number[][]): Promise<Leg> {
  const out: Leg = { coords: [], wayPoints: [0], secs: [], meters: [] };
  for (let i = 0; i < points.length - 1; i += MAX_WAYPOINTS - 1) {
    const part = await directions(points.slice(i, i + MAX_WAYPOINTS));
    const offset = out.coords.length ? out.coords.length - 1 : 0; // chunks share their joining stop
    out.coords.push(...(out.coords.length ? part.coords.slice(1) : part.coords));
    out.wayPoints.push(...part.wayPoints.slice(1).map((w) => w + offset));
    out.secs.push(...part.secs);
    out.meters.push(...part.meters);
  }
  return out;
}

Deno.serve(async (req) => {
  const jwt = req.headers.get("Authorization")?.replace("Bearer ", "") ?? "";
  const { data: auth } = await db.auth.getUser(jwt);
  const { data: me } = await db.from("profiles").select("role").eq("id", auth.user?.id ?? "").maybeSingle();
  if (me?.role !== "admin") return json({ error: "admins only" }, 403);
  if (!orsKey) return json({ updated: 0, skipped: "ORS_API_KEY not set" });

  const { data: stops, error } = await db.from("stops").select("route_id,shift,seq,lat,lng")
    .eq("archived", false).order("seq");
  if (error) return json({ error: error.message }, 500);
  const groups = new Map<string, { route_id: string; shift: string; points: number[][] }>();
  for (const s of stops) {
    const k = `${s.route_id}/${s.shift}`;
    if (!groups.has(k)) groups.set(k, { route_id: s.route_id, shift: s.shift, points: [] });
    groups.get(k)!.points.push([s.lng, s.lat]);
  }
  const { data: have } = await db.from("route_shapes").select("route_id,shift,stops_sig");
  const known = new Map((have ?? []).map((h) => [`${h.route_id}/${h.shift}`, h.stops_sig]));

  let updated = 0;
  const failed: string[] = [];
  for (const [k, g] of groups) {
    if (g.points.length < 2) continue;
    const { data: sig } = await db.rpc("route_stops_sig", { p_route: g.route_id, p_shift: g.shift });
    if (known.get(k) === sig) continue;
    try {
      const r = await route(g.points);
      const { error } = await db.from("route_shapes").upsert({
        route_id: g.route_id, shift: g.shift, stops_sig: sig, coords: r.coords, way_points: r.wayPoints,
        leg_seconds: r.secs, leg_meters: r.meters, updated_at: new Date().toISOString(),
      });
      if (error) throw new Error(error.message);
      updated++;
    } catch (e) {
      console.error("route-shape", k, e);
      failed.push(k);
    }
  }
  return json({ updated, failed });
});
