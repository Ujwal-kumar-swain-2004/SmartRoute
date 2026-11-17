# Bhubaneswar OpenStreetMap road extract

Source: OpenStreetMap contributors, obtained from https://maps.mail.ru/osm/tools/overpass/api/interpreter on 2026-11-16.

OSM data timestamp: 2026-11-16T03:49:43Z. The extract has 122047 raw OSM elements. It covers ways intersecting 20.22–20.38 N, 85.75–85.90 E, together with their referenced nodes. Full ways may extend slightly beyond the box; route input locations are bounded to it.

Licence: Open Database License (ODbL) 1.0. Attribution: © OpenStreetMap contributors. Licence and attribution details: https://www.openstreetmap.org/copyright and https://opendatacommons.org/licenses/odbl/1-0/. This is an extracted, not independently surveyed, road dataset. The teaching graph derives directed edges from consecutive way nodes and selected road/access tags.

Regenerate using scripts/download-osm.ps1, with -Endpoint for an available Overpass instance. The script validates the response, updates data/bhubaneswar-osm.json and the bundled resource backend/src/main/resources/osm/bhubaneswar.json. Rebuild and restart after updating.

No TomTom credentials or provider responses are stored in this extract.
