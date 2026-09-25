# Store directory snapshot

This directory contains the deterministic configuration and rights record for
the bundled GTA and Metro Vancouver location snapshot. The raw Overpass JSON
input is intentionally kept under the ignored `local-provider-data/` boundary;
it is not a release artifact and is never read by Android.

To rebuild a candidate locally, save a lawful public OSM/Overpass response to
`local-provider-data/osm-gta-gva-YYYY-MM-DD.json`, then run:

```text
python tools/build_store_directory_snapshot.py --config tools/store_directory/config.json --input local-provider-data/osm-gta-gva-YYYY-MM-DD.json --output <empty-output> --private-key <external-store-directory-key.pem> --require-signature
python tools/verify_store_directory_snapshot.py --snapshot <empty-output> --public-key android/app/src/main/assets/store_directory/public-key.pem --require-signature
```

Only the signed output is eligible for bundling. Rows are source-listed
locations and deliberately contain no product, price, promotion, stock, or
availability facts. OpenStreetMap attribution and ODbL-1.0 source boundaries
are carried in the manifest and `ATTRIBUTION.txt`.
