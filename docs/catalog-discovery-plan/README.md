# Catalog-wide bilingual discovery: implementation handoff

Prepared 2026-10-15 against source HEAD `4b3145f3`, branch
`work/valuepilot-stage2-premium-shopping`. This is a design and implementation
handoff, not a claim that the new discovery system is installed or complete.

## What the user wants

Organize every product in the actual catalog into all useful, evidence-backed
search relationships. Any supported English or Spanish search should lead to
the relevant clarification choices and then an accurately filtered table.
The user's rice, fruit, strawberry, mango, wheat, sugar and toy examples are
illustrations. They are not the scope or a fixed list of supported families.
New products must pass through the same deterministic process automatically.

A product has an actual type and can also have ingredients, flavor, scent,
material, intended use or a depicted theme. Those relationships must remain
distinct. A strawberry shampoo belongs under shampoo; strawberry can find it
through a clearly identified personal-care choice. It must never become fruit.

## Read in this order

1. [ENGINEERING_SPEC.md](ENGINEERING_SPEC.md): the required model, query rules,
   contracts, processing pipeline, delivery stages and completion gates.
2. [LUNA_IMPLEMENTATION_PROMPT.md](LUNA_IMPLEMENTATION_PROMPT.md): the executable
   agent brief. Paste it into the coding agent with this repository available.
3. [EXISTING_CATALOG_CENSUS.json](EXISTING_CATALOG_CENSUS.json): full-file counts
   from the existing local artifacts, with exact file hashes.

## Verified starting point

The read-only census visited every row of both pinned artifacts:

| Measurement | Count |
|---|---:|
| Retailer-scoped identity records | 134,337 |
| Distinct provider product IDs / existing taxonomy rows | 78,865 |
| Identity records mapped to a taxonomy record | 134,337 |
| Products without an assigned family | 66,694 |
| Products without an assigned form | 65,267 |
| Products without either family or form | 57,087 |
| Products without an English alias on any linked identity | 67,057 |
| Existing primary-department assignments | 52,363 |
| Existing department review queue | 26,502 |

These are structural counts of the old derived artifacts, not a new semantic
review. A missing alias does not mean that literal name search cannot work;
an existing alias does not establish complete English coverage. A department
assignment does not establish useful clarification coverage. The code currently
defines 31 family rules, 20 department rules and 18 form rules; the pinned
artifact is separate evidence and can lag newer code.

## Specific engineering gaps found in current code

- `backend/service.py:search` discovers a small result window first, then
  generates facets from it. `backend/reader.py` limits that window to 31
  candidates. Ordinary initial requests can use only six. Hidden candidates
  therefore cannot contribute choices; filtering after truncation loses recall.
- The generic taxonomy path emits `PRODUCT_TAXONOMY_CLARIFICATION`, `labelEs`,
  and no `evidence` field. The inspected Android parser accepts only
  `PRODUCT_FAMILY_CLARIFICATION`, requires `label`, and requires
  `evidence=OBSERVED_CATALOG_NAMES`. This is a source-level contract mismatch;
  no live request was run in this planning task.
- Generic `filter_taxonomy_candidates` returns the original candidates when
  no filtered candidates remain. An explicit selection must never silently
  restore unrelated rows.
- The taxonomy builder unions family/form facts across names and assigns a
  primary department by priority. That does not preserve which modifier belongs
  to which product component, or resolve conflicting retailer descriptions.
- Choices become rewritten query strings. The new system needs stable concept
  and predicate IDs; translations must not change the selected meaning.
- HTML displays at most six choices. The new design requires a way to access
  every nonempty choice, without rendering thousands at once.

## What can honestly be promised

The engineering can guarantee full record accounting, repeatable processing,
traceable assignments, strict filtering, consistent bilingual aliases, and
automatic processing of additions and removals. It cannot infer missing meaning
with certainty from an abbreviated or ambiguous source name. Unknown meanings
need better supplier evidence or a reviewed rule. Their records remain searchable
and explicitly unresolved internally; that does not count as semantic completion.

The specification keeps implementation completion, full-catalog semantic
completion, and live device verification as separate gates. None can substitute
for another. There is no mandatory paid AI dependency at build or search time.

## Reproduce the census

From the repository root:

```powershell
python docs/catalog-discovery-plan/audit_existing_catalog.py
```

The script uses only Python's standard library, reads the two local compressed
artifacts, and writes aggregate counts beside itself. It does not modify inputs,
query the backend, build an APK, or expose individual product records in Git.
The input files are pinned to the 2026-10-08 snapshot for the 78,865 baseline;
they are not asserted to be the latest deployed release.
