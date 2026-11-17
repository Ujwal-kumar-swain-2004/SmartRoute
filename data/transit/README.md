# Bhubaneswar transport data sources

Checked and collected on **17 November 2026** for SmartRoute. These files are a research/data-collection package; they have not been imported into the app's routing database or used to claim live public-transit routing.

## Files collected

| File | Content | Status |
|---|---|---|
| `bus-stops.csv` | 394 GIS stop records with source IDs, names, WGS84 latitude/longitude, source URL and collection date | Static snapshot |
| `bus-stops.geojson` | Original stop features, attributes and geometry | Static snapshot |
| `bus-terminals.geojson` | 4 terminal features | Static snapshot |
| `bus-depots.geojson` | 3 depot features | Static snapshot |
| `gis-route-lines.geojson` | 9 selected route-line features | Partial GIS network; not a verified current complete Ama Bus network |
| `route-layer-11.geojson` through `route-layer-19.geojson` | Individual source route layers | Original source names preserved |
| `raw/*-metadata.json` | Dataset schemas and service metadata | Source revision date not provided in the checked metadata |
| `raw/crut-network-map.pdf` | Map linked by CRUT's publication hub | Version not independently verified; original filename refers to MO BUS |
| `collection-manifest.json` | Source URLs, counts, successes/failures and collection timestamp | Provenance |
| `sources.csv` | Broader source/access register | Includes sources not downloaded and live feeds not obtained |

All coordinates in the collected GeoJSON/CSV are requested in EPSG:4326. CSV columns are named latitude/longitude; GeoJSON coordinate order is longitude, latitude. Opposite-direction stops can share a name: retain source IDs rather than deduplicating only by stop name.

## Where to obtain data

### 1. CRUT / Ama Bus: city-bus operations

- Official transit information and maps: https://www.comprehensiveregiontransport.in/transit-services/ama-bus
- Publication hub: https://linktr.ee/crut_bbsr
- The hub currently lists a Capital Region timetable effective **13 November 2026** and detailed stoppages effective **5 August 2026**. Those two timetable/stoppage PDFs were not downloaded in this collection; use their effective dates when selecting publications. The network map PDF was downloaded separately.
- Official app/service description: https://www.comprehensiveregiontransport.in/riders-services
- Contact: https://comprehensiveregiontransport.in/contact-us — official email **crut.state@odisha.gov.in**.

Ask CRUT for route IDs, stop IDs and coordinates, ordered stop sequences, trip calendars, stop times and fares. Ideally request GTFS Schedule. For live GPS and arrivals, request an approved API or GTFS-Realtime vehicle positions/trip updates and service alerts. An official app having live tracking does not establish that a reusable public feed is available. No public GTFS/GTFS-Realtime feed was verified during this research.

### 2. BhubaneswarOne GIS: directly queryable geometry

- Stops/terminals/depots: https://bhubaneswarone.in/arcgis/rest/services/BhubaneswarOne/UpdatedBusStops/FeatureServer
- Selected route lines: https://bhubaneswarone.in/arcgis/rest/services/BhubaneswarOne/UpdatedBusRouteNetwork/MapServer
- All service groups: https://bhubaneswarone.in/arcgis/rest/services/BhubaneswarOne

The collector used stop layer 3, terminal layer 1, depot layer 2, and route-line layers 11–19. It checks pagination and preserves source metadata. “Updated” in a layer name is not proof that the data matches today's Ama Bus routes. The checked layer metadata has no last-edit date and empty copyright text; public query access alone does not establish an explicit redistribution licence. Confirm reuse and currency with the city before publishing a derived transit service.

To repeat the bounded collection from the project root:

```powershell
node scripts/collect-transit-data.mjs
```

The script records unavailable sources instead of fabricating replacement records. Reconcile route names, stop sequences and service dates with CRUT before merging these GIS layers into schedules.

### 3. redBus: commercial bus inventory

- Official partner platform: https://partner.redbus.com/loginauth/
- Operator onboarding (for bus operators, not a general dataset signup): https://onboardvendor.redbus.in/
- API distribution evidence from parent-company filing: https://www.sec.gov/Archives/edgar/data/1495153/000119312526318134/mmyt-20260331.htm

The parent-company filing confirms API distribution partners. No unrestricted public redBus download or developer API was verified. Ask redBus's partnership team about academic/read-only access, allowed inventory fields, credentials, coverage, refresh limits and reuse terms. Do not assume an operator/agent login includes API access. No redBus data was collected here.

For SmartRoute, CRUT is the stronger starting point for Ama Bus schedules within the city. Treat redBus inventory as a separate source for available services rather than evidence of Ama Bus route coverage or traffic on every road.

### 4. IUDX: city data discovery and authorized feeds

- Bhubaneswar city use case: https://iudx.org.in/bhubaneshwar-smart-city/
- Catalogue: https://catalogue.cos.iudx.org.in/

IUDX describes Bhubaneswar multimodal data sharing. Search the catalogue for the city and inspect individual resources, provider contacts, schema, access policy and last update. No specific current live resource was queried or downloaded in this collection; availability and authorization must be checked per dataset.

### 5. BSCL / traffic-management authority

- Traffic-management system: https://www.smartcitybhubaneswar.gov.in/traffic-management-system
- Integrated city operations: https://www.smartcitybhubaneswar.gov.in/implementations/smart-solutions-icomc

Ask whether anonymized junction-level vehicle counts, approach/direction, observation time, average speed, queue length and signal-phase data can be provided for an academic study. Request a defined interval such as 5 or 15 minutes, location coordinates, a data dictionary and the observation period. The public system description is not itself an open traffic-feed endpoint. No raw signal or traffic-counter data was obtained.

For the project's “busier road” preference, actual vehicle counts would be more relevant evidence than route popularity, ticket listings or low road speeds. Bus frequency is useful context but is not a measurement of all traffic or personal safety.

### 6. Other useful sources

- Historical bus statistics: https://smartcities.data.gov.in/resources/buses-bhubaneswar-2019 — historical 2019 data, not live schedules.
- OpenStreetMap: the road extract is already in this project; bus route relations/stops can be another cross-check, subject to coverage and ODbL attribution.
- TomTom: already connected for driving routes, road flow and POIs. Its road traffic feed does not replace operator bus GPS, trip timetables or passenger occupancy data.
- CRUT/ONDC: https://comprehensiveregiontransport.in/project-details/56 describes digital ticketing. Ticketing participation is separate from a downloadable research dataset or an open vehicle-location feed.

## Suggested project data model

Keep sources separate until IDs and dates are reconciled:

- `stops`: source, source_stop_id, name, latitude, longitude, retrieved_at, valid_from.
- `routes`: source, route_id, name, direction, geometry, valid_from, valid_to.
- `trips` / `stop_times`: route_id, trip_id, service_date/calendar, stop_sequence, arrival, departure.
- `vehicle_positions`: source, vehicle_id, trip_id, timestamp, latitude, longitude, confidence.
- `traffic_observations`: source, road/junction ID, timestamp, count, interval_minutes, average_speed, direction.

This collection supplies GIS points/lines and source references. It does not supply a validated trip timetable, live vehicle positions, occupancy measurements or citywide traffic counts.
