# SmartRoute

Traffic route optimization for Bhubaneswar Smart City. Java 21 / Spring Boot 3.5 backend and React 19 / TypeScript / Vite frontend. Demo routing runs in Java without API keys or a database. Optional live traffic uses TomTom through the Java backend.

## Run locally

Prerequisites: Java 21, Maven 3.9+, Node 22+ and npm. In two terminals:

```powershell
cd backend
mvn spring-boot:run
```

```powershell
cd frontend
npm install
npm run dev
```

Open http://127.0.0.1:5173. Vite proxies `/api` and `/actuator` to port 8080. Start both processes; the frontend never fabricates routes when the backend is unavailable.

## Verify

```powershell
cd backend
mvn test
mvn package
```

```powershell
cd frontend
npm test
npm run build
```

The backend tests cover all-pairs ALT/CH correctness against Dijkstra under three traffic scenarios and both objectives, closures, fuel feasibility, full-journey recommendations, stop dwell time, validation, alternatives, FIFO and REST validation. Frontend tests cover route rendering, swapping endpoints, fuel settings, emergency routing, algorithm comparison, methodology and infeasible journeys.

## Features and scope

- Route planning with fastest / shortest objectives, date and time, swapping endpoints, automatic recalculation and up to two distinct edge-deviation alternatives.
- 23 synthetic nodes and 38 undirected edges around approximate Bhubaneswar landmarks. Roads are schematic straight lines, not street-level navigation geometry.
- Morning / evening simulated traffic, normal conditions, and a closure of Jaydev Vihar–Acharya Vihar. Peak profiles are centered at 09:00 and 18:00 and fade away from those hours.
- Static Dijkstra, FIFO time-dependent Dijkstra, ALT with three exact landmark tables, and an exact contraction-hierarchy implementation with upward queries and shortcut unpacking. CH intentionally omits witness pruning, which is practical only for the small demo graph.
- Petrol, restaurant or hotel stop selection, or full-journey optimization across all eligible stops. Stop duration changes onward departure and arrival.
- Fuel reachability uses remaining driving range minus reserve. It checks the leg to the pump and assumes refuelling covers the onward leg; it does not model tank capacity or consumption.
- Emergency demo forces minimum travel time. It is not operational emergency navigation.
- Algorithm lab shows measured execution time, discovered and settled nodes, preprocessing, distance and ETA. CH search counts aggregate both searches; stop journeys aggregate both legs. Execution includes preprocessing and route reconstruction, but excludes alternative-candidate generation. Repeated route requests may be cached; the evaluation endpoint bypasses that route cache. Microbenchmarks are single runs and are affected by JIT warm-up.
- OpenStreetMap basemap needs internet. A tile failure leaves the synthetic graph visible. Demo routes remain simulated. The separate Live traffic page supports TomTom provider routes when a backend API key is configured.

For fastest routes, static Dijkstra, ALT and CH optimize departure-time snapshot weights. Time-dependent Dijkstra integrates speed along each edge at quarter-minute intervals, changing costs as the journey progresses. These different models need not produce identical travel times. Shortest mode minimizes distance for all algorithms.

## REST API

- `GET /api/network` — places and synthetic roads
- `GET /api/scenarios` — available traffic scenarios
- `GET /api/capabilities` — demo and provider availability
- `POST /api/routes` — routes and stop recommendations
- `POST /api/alternatives`, `/api/stops`, `/api/fuel` — same validated planning contract
- `POST /api/evaluate` — uncached primary-route evaluation for every algorithm
- `GET /actuator/health` — application health

Example request:

```json
{
  "source": "p0",
  "destination": "p9",
  "departure": "2026-11-15T09:00:00",
  "scenario": "MORNING",
  "objective": "FASTEST",
  "algorithm": "TIME_DEPENDENT",
  "category": null,
  "stopId": null,
  "stopMinutes": 10,
  "fuelRange": null,
  "reserve": 2,
  "emergency": false
}
```

Scenario values: `NORMAL`, `MORNING`, `EVENING`, `CLOSURE`. Algorithms: `DIJKSTRA`, `TIME_DEPENDENT`, `ALT`, `CH`. Objectives: `FASTEST`, `SHORTEST`. Category: `PETROL`, `RESTAURANT`, `HOTEL` or null. `stopId: null` chooses the best complete journey. Dates are local Bhubaneswar time (Asia/Kolkata), without a timezone offset.

Caffeine caches up to 1,000 planning requests for five minutes. Spring Data JPA is included for future integration; datasource and ORM auto-configuration are disabled, so PostgreSQL is not required. To add persistence, configure a datasource, enable those auto-configurations and implement entities/repositories.

For production, serve `frontend/dist` with an `/api` reverse proxy to the Spring Boot service, or copy the built files to Spring Boot's static resources. Never put provider credentials into the frontend.

If ports are already occupied, use `mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"` and set `$env:SMARTROUTE_API_URL='http://127.0.0.1:8081'` before `npm run dev`. Vite prints its chosen available port.

## Single-JAR build

Run `powershell -ExecutionPolicy Bypass -File .\build.ps1` from the project root. It installs locked npm dependencies, runs frontend tests/build, bundles the frontend into Spring Boot, and runs Maven tests/package. Use `-SkipInstall` after dependencies are installed. Then run:

```powershell
java -jar backend/target/smartroute-1.0.0.jar --server.port=8081
```

Open http://127.0.0.1:8081. Both the UI and REST API are served by the same Java process; Vite is only required for development.

## TomTom live traffic setup

A separate **Live traffic** page now uses TomTom Calculate Route v1 and Traffic Flow Segment Data v4. Calls happen only on the backend; the browser never receives the API key. Provider routes use real road geometry, departure now, available live/historical traffic, fastest/shortest objectives and up to two provider alternatives. The flow sample is the nearest provider road segment to the chosen source, not every road on the journey.

Set the key in the same terminal that starts Java:

```powershell
$env:TOMTOM_API_KEY = 'YOUR_TOMTOM_KEY'
java -jar backend/target/smartroute-1.0.0.jar --server.port=8081
```

The key is read from the environment; it is not persisted in this repository. Enable Routing API and Traffic API access in your TomTom account if the key receives a product-access error. Keep an eye on provider usage/quota. Route and flow responses are cached separately for 60 seconds; failure responses are not cached. The fetched timestamp remains unchanged for cached results. No periodic polling occurs. Timeouts, rejected credentials, unavailable coverage and quota errors are displayed without substituting simulated traffic.

- `GET /api/tomtom/status` — configured flag (never the key)
- `POST /api/tomtom/routes` — `{ "source": "p0", "destination": "p9", "objective": "FASTEST" }`
- `GET /api/tomtom/flow?placeId=p0` — current/free-flow speed, confidence, closure and sampled segment

The academic graph, sample stops, demo fuel planning and Java algorithm lab remain synthetic and separate from TomTom routes. This integration does not map live speeds onto the synthetic edges or claim that Java algorithms computed provider routes.

Provider documentation: https://docs.tomtom.com/routing-api/documentation/tomtom-maps/v1/calculate-route and https://docs.tomtom.com/traffic-api/documentation/tomtom-maps/v1/traffic-flow/flow-segment-data.


You can also run `.\start.ps1` (default port 8081). If no key is in the terminal environment, it prompts for the key with hidden input. Press Enter to start demo mode without a provider key. No key is written to disk.


## Expanded journey planning

Open **Live traffic** to use the new journey controls:

- Search a place or address in India (Bhubaneswar-biased). Press Search or Enter, then explicitly choose a result. TomTom Search API access is required for search; map selection and landmarks do not require a search request.
- Press a field's **Map** button, then click the OpenStreetMap map to set that point. A point may be a start, destination or intermediate stop.
- Add up to five intermediate stops, move them up/down, or remove them. Stops are visited in your listed order; no automatic travelling-salesperson optimization or dwell time is implied.
- Request avoidance of toll roads and highways. TomTom treats these as provider preferences. In Java OSM mode, tagged toll and motorway/trunk edges are excluded from the graph query.
- Expand turn-by-turn directions and click a step to center the map at that instruction. TomTom supplies turn instructions. Java OSM mode shows road-name sequences, not operational turn guidance.
- Compare the selected route's traffic-aware duration with its provider-supplied free-flow duration. Missing baseline values are shown as unavailable, never fabricated. The reported incident delay may differ from the overall free-flow difference.

With intermediate stops TomTom alternatives are disabled, and one full multi-leg route is requested. Without stops, up to two alternatives are requested. All provider requests still go through Spring Boot, with the key held only in the process environment.

### Java routing on actual OSM roads

Choose **Routing engine → Java + OSM** on the same page. It runs Java Dijkstra, FIFO time-dependent Dijkstra, or directed ALT on the bundled Bhubaneswar road extract. The **Compare Java algorithms** button evaluates these three algorithms on that graph. The simpler contraction-hierarchy teaching implementation remains confined to the small synthetic graph; it is not used on the city extract.

The imported graph preserves original road-node geometry, handles basic motor-vehicle access and one-way/roundabout directions, and uses `maxspeed` where parsable or explicit speed defaults. It does not fully model turn restrictions, lane rules, barriers, conditional access, or roadworks. Default speeds are motorway 70, trunk 60, primary 50, secondary 40, tertiary 35, local 25, service/living street 15 km/h. Traffic in this engine is **estimated**, with optional simulated peak profiles. TomTom live speeds are not assigned to these edges.

Locations must lie within 20.22–20.38 N and 85.75–85.90 E. They snap to a nearby eligible road node within 750 m; the response reports each snap distance. Off-road connector distance and travel time are not added. Disconnected paths are reported explicitly. Full OSM ways can extend beyond the bounding box.

Dataset provenance, snapshot date and ODbL attribution are in `data/README.md`. The original Overpass JSON is stored in `data/bhubaneswar-osm.json` and bundled as a classpath resource for the single JAR. Runtime prefers the configured `smartroute.osm-file` (default `data/bhubaneswar-osm.json`), then the bundled resource. To refresh the dataset, run `scripts/download-osm.ps1`, rebuild, and restart. There are no background Overpass downloads.

Additional endpoints:

- `GET /api/tomtom/search?q=...`
- `POST /api/tomtom/flow` with `{ "label": "Selected point", "lat": 20.3, "lng": 85.82 }`
- `POST /api/tomtom/routes` accepts `origin`, `destinationPoint`, ordered `stops`, `avoidTolls`, `avoidHighways`, and `objective`. Legacy landmark `source` / `destination` IDs still work.
- `GET /api/osm/status`
- `POST /api/osm/routes` or `/api/osm/evaluate` accepts `{ "journey": { "source": "p0", "destination": "p9", "objective": "FASTEST", "stops": [], "avoidTolls": false, "avoidHighways": false }, "algorithm": "ALT", "scenario": "NORMAL" }`.

All modes are academic demonstrations, not operational navigation.

## Unified real-road planner: stops, fuel and main-road preference

The **Live traffic** view now also includes departure scheduling (now or within the next 30 days), configurable dwell time at every stop, real petrol/restaurant/hotel discovery, a manually selected or automatically recommended category stop, fuel-range/reserve checks, and an academic emergency demonstration. These features use `/api/journeys/plan`; they work with TomTom routing or the Java OSM engine. Nearby places are provider-listed businesses, not sample POIs. Opening hours, fuel stock and business status are not independently verified. Nearby search needs the backend TomTom key even with the Java route engine.

A category recommendation evaluates up to three of the returned nearby candidates, routing both legs of each full journey. It does not claim a global optimum across all city businesses. Fuel checks compare the actual routed first-leg kilometres against remaining range minus reserve. If none fits, an explicit infeasible message is returned. Refilling is assumed sufficient for the onward leg. Category recommendations and manual waypoint lists are separate choices; remove manual stops to enable a category recommendation.

Stop dwell time changes onward departure, not just the final displayed ETA. Driving time is reported separately from total stop seconds; route cards include both. Traffic/free-flow comparisons remain comparisons of driving time on the selected geometry. With dwell enabled, sequential legs are calculated at their respective departure times. Future TomTom travel times are predictions using available traffic information, not future live measurements.

**Prefer busier / main roads** addresses the user's preference for less isolated journeys using an explicit proxy. OSM motorway, trunk, primary, secondary, tertiary and related link roads are classified as main roads. This does not measure vehicle counts, pedestrian presence, lighting, crime, accident risk or personal safety. Congestion is not interpreted as proof that a route is safe.

- TomTom mode ranks only the available provider alternatives using main-road share, requiring at least 70% approximate OSM coverage. Candidates more than 30% slower than the fastest returned route are excluded.
- Java OSM mode penalizes local-road generalized costs by 2.5, preserving measured distance/travel-time outputs. It reverts to the baseline if the preferred route exceeds a 30% travel-time allowance. This option supports static Dijkstra and directed ALT; time-dependent Dijkstra is used for pure travel-time routing instead.
- Approximate coverage uses nearest OSM segments within 60 m and can be ambiguous on parallel roads. Main-road share is calculated only over matched distance. Outside the imported area, insufficient coverage is reported and no main-road preference is claimed.
- This feature is **not a safety rating or a guarantee of a busier road**. Emergency demonstration prioritizes minimum estimated time and disables main-road ranking.

`POST /api/journeys/plan` / `/api/journeys/nearby` accepts:

```json
{
  "journey": {"source":"p0","destination":"p9","objective":"FASTEST","stops":[],"avoidTolls":false,"avoidHighways":false,"departure":null},
  "engine":"TOMTOM", "algorithm":"DIJKSTRA", "scenario":"NORMAL",
  "stopMinutes":10, "fuelRange":8, "reserve":2,
  "category":"PETROL", "selectedPoiId":null,
  "emergency":false, "preferBusierRoads":true
}
```

Set category to `RESTAURANT`, `HOTEL`, or null. Set fuelRange to null to disable fuel checks. `journey.origin` / `destinationPoint` coordinate objects still work. `departure` is null for now, or a local Asia/Kolkata ISO date/time. `/api/journeys/evaluate` compares the applicable Java algorithms with the same extended settings. Planning responses are cached for up to 60 seconds. The UI makes provider requests only on explicit actions; a recommended stop may need one nearby lookup and up to seven routing calls before caching.
