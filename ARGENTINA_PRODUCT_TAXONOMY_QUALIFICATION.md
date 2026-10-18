# Argentina product taxonomy qualification

**Checkpoint:** active release identity coverage and isolated taxonomy build  
**Status:** complete for the derived index; human review remains intentionally explicit

## Source

This checkpoint uses the active Argentina release already qualified in the
identity audit:

- Release: `argentina-sepa-2026-10-08-153e9d9f7ab4b87e-routing-v1-search-v2`
- Official source SHA-256: `153e9d9f7ab4b87e72c16aebebc69c6f9f90fca681cd1075d625244e2e31bc2c`
- Identity input: `134,337` retailer-scoped identities
- Provider products: `78,865`

The taxonomy build uses product identity names and reviewed family evidence.
It does not read prices, offers, stock, availability, credentials, or
production search configuration.

## What was implemented

- A deterministic provider-level taxonomy index. The same provider product
  appearing in multiple stores is classified once; store identities remain
  linked through their identity keys.
- Broad departments for groceries, beverages, produce, dairy, meat, snacks,
  household cleaning, personal care, baby/pet, health, clothing, electronics,
  home goods, toys, stationery, tools, garden, sports/luggage, and prepared
  foods.
- Reusable product forms such as fresh, frozen, liquid, powder, grain, flour,
  pasta, biscuit, dessert, drink, alcohol, canned, cosmetics, cleaning,
  supplement, and whole egg.
- A full review queue for products that have no safe department evidence or
  need a human decision.
- Generic clarification support in Search. Choices are shown only when the
  bounded result window contains at least two observed forms. Existing
  reviewed family clarification remains the first projection, with the
  generic taxonomy projection as a fallback.
- Mango vocabulary and observed-form handling, so mango fruit, mango drinks,
  mango desserts, and mango cookies can remain separate when the source
  contains them.
- The review-queue batches add observed Argentine terms for produce,
  dairy, meat, snacks, beverages, home and baby goods, health, personal care,
  condiments, toys, electronics, stationery, and prepared foods. Punctuation
  variants, packaging words, and ingredient words are guarded with explicit
  exclusions; ambiguous names remain in review.

## Coverage result

The derived index contains all `78,865` provider products.

- `52,363` (`66.40%`) have a stable evidence-backed primary department.
- `26,502` (`33.60%`) are in `REVIEW_REQUIRED` with their evidence and sample
  names preserved.
- `11,159` products have multiple department candidates retained as labels;
  a primary department is chosen only when the reviewed priority is unique.
- Every provider product has a coverage status. No product is silently dropped
  or forced into a made-up category.

The current derived artifact is local and ignored by Git. Its final build
digest is recorded in the machine-readable report
`ARGENTINA_PRODUCT_TAXONOMY_QUALIFICATION.json`.

## Customer behavior

Customers see a simple clarification box only when it helps. For example,
searching `huevos` can show the observed choices `Huevo entero`, `Líquido`,
and `En polvo`; selecting a choice refines the table. A single observed form
opens the table directly. Internal department names, confidence states, and
review status are never exposed as customer copy.

## Safety and remaining work

The index is identity-only. It does not change the active release, backend
security, prices, store offers, user data, or the Play-installed application.

`REVIEW_REQUIRED` is deliberate. Product names alone cannot prove the meaning
of private-label abbreviations, colour-only names, set descriptions, or
ambiguous words. The next taxonomy batches should review that queue in volume
order, add only evidence-backed rules, and rebuild the complete index after
each accepted rule. A perfect semantic label for every obscure item requires
additional supplier metadata or human review; the current checkpoint provides
100% accounting and an auditable path to that review.
