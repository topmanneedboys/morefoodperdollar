# Independent semantic review ledger

`SEMANTIC_REVIEW_LEDGER.schema.json` defines the evidence required before the
catalog can claim semantic accuracy. The compiler is not the reviewer and its
own postings cannot be copied into this file as the expected answer.

## One case

Each reviewed case records:

- the exact query, language and optional strict selection;
- a **complete** `expectedIdentityKeys` set, including every retailer identity
  that should be returned;
- the complete `expectedProviderProductIds` set, so duplicate retailer copies
  cannot be silently added or split;
- reviewer identity, review method, timestamp, source evidence and risk tags.

`HUMAN_SOURCE_REVIEW`, `DUAL_HUMAN_REVIEW`, and `DOMAIN_REVIEW` are the only
methods accepted by the verifier. A classifier self-check, a copied compiler
label, or a positive-only sample is not independent evidence.

## Required coverage

The held-out split must contain at least 1,000 distinct provider products and
must keep all retailer identities for a provider product in the same split.
It must cover every observed department, both supported languages, ordinary
and rare products, and the difficult senses already identified in the
engineering specification: product versus ingredient, flavor, scent, brand,
accessory, material, form, packaging, unrelated mentions, compounds,
negation, and ambiguous words.

Reviewers should record both positive and negative membership decisions. For a
strict selection, the expected set is the final filtered result, not the broad
clarification window. A query with no safe result may use an empty expected set
and an empty provider set, provided the reviewer records why.

## Verification behavior

Run the full catalog verifier with `--semantic-eval` pointing at the reviewed
ledger. It checks every page, exact identity membership, exact provider-group
membership, duplicate IDs, stale cursors and query failures. It reports
identity and provider precision/recall separately. Any unreviewed case,
missing complete oracle, invalid reviewer provenance, or unexpected result
keeps the semantic gate below `PASS`.

The current v40 checkpoint intentionally has no reviewed ledger. Its
provider-group-safe queue contains 1,000 groups and 1,260 linked identity rows,
remains `PENDING_HUMAN_REVIEW`, and is summarized in
`SEMANTIC_REVIEW_QUEUE_SUMMARY.json`. This file defines the gate and prevents a
future run from accidentally treating compiler output as semantic truth.
