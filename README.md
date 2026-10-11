# Nudge

Nudge is an Android platform for running scheduled office shuttles. It covers
the full operation in a single app: riders buy passes and book seats, drivers
run trips with a live manifest, and operators manage routes, pricing and
payments from an admin console.

Built for small and mid-sized operators, it runs entirely on free-tier
infrastructure and is designed to support 500–1,000 daily riders without
changes.

## Features

### Riders
- Phone number login with OTP
- Monthly passes, ride packs and one-way tickets, paid by UPI
- Seat booking for morning and evening shifts within a configurable booking window
- Live shuttle position on the map, with road-based arrival estimates for the rider's pickup
- Boarding pass, ride history and pass balance

### Drivers
- Today's trip with a stop-by-stop passenger manifest
- Board, no-show and undo actions per passenger
- Background GPS sharing for the duration of the trip, with automatic shutdown on trip end

### Operators
- Payment review queue with approve and reject actions, plus CSV export
- Manual pass issuance for cash or offline payments
- Route management: import from a PDF timetable, duplicate with a time offset, or edit by hand with map-based stop placement
- Configurable plans, tiered monthly pricing, UPI accounts, vehicles, staff and booking rules
- Fleet overview and GPS playback of past trips

## Architecture

The app is a single Android client. Users see the rider, driver or operator
interface depending on their role. The backend is Supabase.

- **Business logic lives in the database.** Booking, cancellation, payment
  resolution and trip progression are Postgres functions. Row-level security
  blocks direct writes, so every rule is enforced server-side regardless of
  the client.
- **Real-time location** is published by a foreground service on the
  driver's device and delivered to riders through Supabase Realtime.
- **Authentication** uses Supabase phone auth. OTP delivery goes through
  an Edge Function hook that works with any HTTP-based SMS gateway.
- **Road routing** uses OpenRouteService through the `route-shape` Edge
  Function. Road paths and stop-to-stop drive times are computed only when a
  route's stops change and stored in `route_shapes`, so routing API usage
  does not grow with the number of riders. They are used for arrival times
  only; the map shows just the shuttle and the rider's pickup. The API key
  stays server-side.
- **No Google Play Services dependency.** Location uses the platform
  `LocationManager` and maps use MapLibre with OpenFreeMap tiles.

## Tech stack

| Layer | Technology |
|---|---|
| Client | Kotlin, Jetpack Compose, Material 3 |
| Backend | Supabase (Postgres, PostgREST, Realtime, Auth, Edge Functions) |
| Data access | supabase-kt |
| Maps | MapLibre Android, OpenFreeMap |
| Geocoding | Nominatim (OpenStreetMap) |
| Routing | OpenRouteService (road paths and drive times, computed server-side) |
| Document parsing | pdfbox-android |

Minimum Android version: 8.0 (API 26).

## Project structure

```
app/src/main/java/com/nudge/app/
  data/        Backend client, view model, models, location service, timetable import
  nav/         Navigation and screen wiring
  ui/          Screens by area (passenger, booking, payment, driver, admin, ...)
supabase/
  migrations/  Schema, row-level security and server-side functions
  functions/   Edge Functions: send-sms (OTP delivery hook), route-shape (road routing)
  tests/       End-to-end smoke test
tools/         Icon font subsetting script
```

## Getting started

Prerequisites: Android SDK, JDK 17+, Docker and the Supabase CLI.

```bash
# Start the local backend and load demo data
supabase start
supabase db reset

# Verify auth, row-level security and all server functions
node supabase/tests/smoke.mjs

# Build and install against the local backend
NUDGE_SUPABASE_URL=http://10.0.2.2:54321 \
NUDGE_SUPABASE_KEY=<publishable key from `supabase start`> \
./gradlew installDebug
```

If the environment variables are not set, the build reads `nudge.supabaseUrl`
and `nudge.supabaseKey` from `local.properties`.

## Deployment

1. Link a Supabase project and apply the migrations:
   `supabase link --project-ref <ref>` then `supabase db push`.
   Demo data is not sent.
2. Add the first operator's phone number to `staff_invites` with the role `admin`.
3. Configure an SMS gateway: set `SEND_SMS_HOOK_SECRET` and
   `SMS_URL_TEMPLATE`, then deploy the `send-sms` function. Until then,
   Supabase test phone numbers can be used.
4. Enable road-based arrival times: set `ORS_API_KEY` (free key from
   openrouteservice.org) and deploy the `route-shape` function. Without it,
   arrival times use straight-line distance.
5. Set the production URL and publishable key in `local.properties` and build
   a signed release.

All operational data (routes, stops, pricing, payment accounts, staff and
rules) is managed from the operator console after deployment.

## License

Proprietary. All rights reserved.
