"""Barcode canonicalization compatible with Open Food Facts lookup semantics.

This module is research/support tooling. It does not decide product equivalence
beyond the leading-zero representations documented by Open Food Facts/GS1-style
GTIN handling.
"""

from __future__ import annotations

from tools.qualify_merchant_feed import is_valid_gtin


def canonical_open_facts_gtin(value: str) -> str | None:
    """Return the canonical leading-zero representation used for lookup.

    Open Food Facts normalizes barcodes that differ only by representation
    leading zeroes. In particular, North American UPC-A / GTIN-12 values are
    represented as 13 digits by prefixing zero. A leading-zero GTIN-14 may
    therefore collapse to the same canonical GTIN-13 representation.

    Invalid GTINs are never repaired or promoted.
    """

    code = value.strip()
    if not is_valid_gtin(code):
        return None

    significant = code.lstrip("0")
    if not significant:
        return None

    if len(significant) <= 7:
        canonical = significant.zfill(8)
    elif len(significant) == 8:
        canonical = significant
    elif 9 <= len(significant) <= 12:
        canonical = significant.zfill(13)
    else:
        canonical = significant

    return canonical if is_valid_gtin(canonical) else None
