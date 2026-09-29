# Confirmed purchases and private history

This bounded milestone adds a device-only confirmed-purchase foundation after the
10113 checkpoint. It does not change the existing planner, live Search, provider
feeds, price-memory authority, or Android networking policy.

## Boundary

Basket rows expose an explicit **Confirm purchase** action. Check-off, adding a
Search identity to Home, navigation, and comparison completion never mint a
purchase. The confirmation form requires an explicit checkbox and records only
facts the shopper enters: stable product identity, optional positive normalized
quantity with an explicit count, mass, or volume unit, optional exact
non-negative money/currency, optional ISO date, and optional store label. Blank
values remain unknown.

The shared-core `ConfirmedPurchase` model is provider-neutral and separates
purchase history from `ObservedOffer`, current prices, inventory, ranking,
receipts, and private comparison price memory. Repeated purchases may share an
identity but always have distinct purchase IDs.

## Durable history

History is capped at 256 records and stored in an ASCII deterministic document
through Android `AtomicFile`. Writes are replacement-atomic; malformed, oversized,
duplicate, or unreadable state blocks mutation and remains unavailable instead of
being replaced with an empty list. Records are stable-sorted by date and ID.
Saved-owned History supports repeat-to-Home, correction, and explicit deletion.
Repeat does not recreate a purchase.

## Verification

The focused tests cover exact money and quantity, explicit confirmation, unknown
fields, malformed dates, repeated identities, deterministic encoding, corrupt and
duplicate documents, failed writes with last-known-good preservation, restart,
replace/remove, the 256-record bound, Saved route navigation, and form parsing.
The local gates for this branch are shared-core tests, the complete app JVM test
suite, debug/release lint, debug/release builds, release bundle, and the APK
privacy check. The release candidate is versionCode `10114` / versionName
`101.1.13`; it has not been signed, uploaded, installed, or tested on a phone.

This milestone does not claim live price coverage, purchase receipt proof, stock,
alerts, maps, route economics, sharing, or cloud synchronization.
