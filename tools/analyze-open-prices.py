#!/usr/bin/env python3
"""Measure Canadian coverage in an Open Prices Parquet export.

This is research tooling only. It performs no product-runtime networking and
writes aggregate metrics without contributor identifiers or proof-image paths.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import duckdb


def is_valid_gtin(value: str | None) -> bool:
    if value is None:
        return False
    value = value.strip()
    if len(value) not in {8, 12, 13, 14} or not value.isdigit():
        return False
    supplied = int(value[-1])
    total = 0
    weight = 3
    for char in reversed(value[:-1]):
        total += int(char) * weight
        weight = 1 if weight == 3 else 3
    expected = (10 - (total % 10)) % 10
    return supplied == expected


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--json", required=True, type=Path)
    parser.add_argument("--markdown", required=True, type=Path)
    args = parser.parse_args()

    if not args.input.is_file() or args.input.stat().st_size == 0:
        raise SystemExit(f"Missing or empty Parquet input: {args.input}")

    con = duckdb.connect(database=":memory:")
    con.execute(
        "CREATE VIEW prices AS SELECT * FROM read_parquet(?)",
        [str(args.input)],
    )

    columns = {
        row[0]
        for row in con.execute("DESCRIBE prices").fetchall()
    }
    required = {
        "id",
        "type",
        "product_code",
        "product_name",
        "price",
        "price_is_discounted",
        "price_per",
        "currency",
        "location_id",
        "date",
        "proof_id",
        "proof_type",
        "location_type",
        "location_osm_display_name",
        "location_osm_tag_key",
        "location_osm_tag_value",
        "location_osm_address_city",
        "location_osm_address_country_code",
    }
    missing = sorted(required - columns)
    if missing:
        raise SystemExit(f"Open Prices schema changed; missing columns: {missing}")

    def scalar(sql: str, params: list[object] | None = None):
        return con.execute(sql, params or []).fetchone()[0]

    def grouped(sql: str) -> list[dict[str, object]]:
        cursor = con.execute(sql)
        names = [item[0] for item in cursor.description]
        return [dict(zip(names, row)) for row in cursor.fetchall()]

    ca_cad = (
        "upper(coalesce(currency, '')) = 'CAD' "
        "AND upper(coalesce(location_osm_address_country_code, '')) = 'CA'"
    )
    proof_backed = (
        "proof_id IS NOT NULL AND proof_type IN ('RECEIPT', 'PRICE_TAG')"
    )
    physical_shop = (
        "location_type = 'OSM' AND location_osm_tag_key = 'shop'"
    )
    product_row = (
        "type = 'PRODUCT' AND product_code IS NOT NULL"
    )
    positive_price = (
        "try_cast(price AS DOUBLE) IS NOT NULL "
        "AND try_cast(price AS DOUBLE) > 0"
    )

    total_rows = scalar("SELECT count(*) FROM prices")
    ca_cad_rows = scalar(f"SELECT count(*) FROM prices WHERE {ca_cad}")
    max_date = scalar("SELECT max(date) FROM prices")
    min_date = scalar("SELECT min(date) FROM prices")
    ca_max_date = scalar(f"SELECT max(date) FROM prices WHERE {ca_cad}")
    ca_min_date = scalar(f"SELECT min(date) FROM prices WHERE {ca_cad}")

    product_codes = [
        row[0]
        for row in con.execute(
            f"SELECT DISTINCT product_code FROM prices WHERE {ca_cad} AND {product_row}"
        ).fetchall()
    ]
    valid_gtins = sorted(
        code for code in product_codes if is_valid_gtin(code)
    )

    strict_without_quantity = (
        f"{ca_cad} AND {product_row} AND {positive_price} "
        f"AND {proof_backed} AND {physical_shop} AND date IS NOT NULL"
    )

    report: dict[str, object] = {
        "source": "Open Prices prices.parquet",
        "license": "ODbL",
        "dataset_rows": total_rows,
        "dataset_date_min": str(min_date) if min_date else None,
        "dataset_date_max": str(max_date) if max_date else None,
        "canada_cad": {
            "rows": ca_cad_rows,
            "share_of_dataset": (
                ca_cad_rows / total_rows if total_rows else 0.0
            ),
            "date_min": str(ca_min_date) if ca_min_date else None,
            "date_max": str(ca_max_date) if ca_max_date else None,
            "unique_locations": scalar(
                f"SELECT count(DISTINCT location_id) FROM prices WHERE {ca_cad}"
            ),
            "unique_product_codes": len(product_codes),
            "unique_checksum_valid_gtins": len(valid_gtins),
            "positive_price_rows": scalar(
                f"SELECT count(*) FROM prices WHERE {ca_cad} AND {positive_price}"
            ),
            "proof_backed_receipt_or_tag_rows": scalar(
                f"SELECT count(*) FROM prices WHERE {ca_cad} AND {proof_backed}"
            ),
            "physical_osm_shop_rows": scalar(
                f"SELECT count(*) FROM prices WHERE {ca_cad} AND {physical_shop}"
            ),
            "strict_rows_before_quantity_join": scalar(
                f"SELECT count(*) FROM prices WHERE {strict_without_quantity}"
            ),
            "strict_unique_product_codes_before_quantity_join": scalar(
                f"SELECT count(DISTINCT product_code) FROM prices WHERE {strict_without_quantity}"
            ),
            "product_name_present_rows": scalar(
                f"SELECT count(*) FROM prices WHERE {ca_cad} "
                "AND product_name IS NOT NULL AND trim(product_name) <> ''"
            ),
            "rows_last_7_days": scalar(
                f"SELECT count(*) FROM prices WHERE {ca_cad} "
                "AND date >= current_date - INTERVAL 7 DAY"
            ),
            "rows_last_30_days": scalar(
                f"SELECT count(*) FROM prices WHERE {ca_cad} "
                "AND date >= current_date - INTERVAL 30 DAY"
            ),
            "rows_last_90_days": scalar(
                f"SELECT count(*) FROM prices WHERE {ca_cad} "
                "AND date >= current_date - INTERVAL 90 DAY"
            ),
            "discounted_rows": scalar(
                f"SELECT count(*) FROM prices WHERE {ca_cad} "
                "AND price_is_discounted = true"
            ),
        },
        "canada_cad_proof_types": grouped(
            f"SELECT coalesce(proof_type, '<null>') AS proof_type, count(*) AS rows "
            f"FROM prices WHERE {ca_cad} GROUP BY 1 ORDER BY rows DESC, proof_type"
        ),
        "canada_cad_location_types": grouped(
            f"SELECT coalesce(location_type, '<null>') AS location_type, count(*) AS rows "
            f"FROM prices WHERE {ca_cad} GROUP BY 1 ORDER BY rows DESC, location_type"
        ),
        "canada_cad_price_per": grouped(
            f"SELECT coalesce(price_per, '<null>') AS price_per, count(*) AS rows "
            f"FROM prices WHERE {ca_cad} GROUP BY 1 ORDER BY rows DESC, price_per"
        ),
        "canada_cad_top_cities": grouped(
            f"SELECT coalesce(location_osm_address_city, '<unknown>') AS city, "
            f"count(*) AS rows, count(DISTINCT product_code) AS products "
            f"FROM prices WHERE {ca_cad} GROUP BY 1 "
            f"ORDER BY rows DESC, city LIMIT 25"
        ),
        "canada_cad_top_location_names": grouped(
            f"SELECT split_part(coalesce(location_osm_display_name, '<unknown>'), ',', 1) "
            f"AS location_name, count(*) AS rows, "
            f"count(DISTINCT product_code) AS products "
            f"FROM prices WHERE {ca_cad} GROUP BY 1 "
            f"ORDER BY rows DESC, location_name LIMIT 25"
        ),
        "canada_cad_top_shop_tags": grouped(
            f"SELECT coalesce(location_osm_tag_value, '<unknown>') AS shop_tag, "
            f"count(*) AS rows FROM prices WHERE {ca_cad} "
            f"AND location_osm_tag_key = 'shop' GROUP BY 1 "
            f"ORDER BY rows DESC, shop_tag LIMIT 25"
        ),
        "quantity_note": (
            "The price Parquet export does not expose package quantity as a dedicated column. "
            "Package-size coverage must be measured by joining product_code to the Open Prices "
            "Product object / Open Food Facts product metadata while preserving provenance."
        ),
    }

    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.markdown.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(
        json.dumps(report, indent=2, ensure_ascii=False, default=str) + "\n",
        encoding="utf-8",
    )

    ca = report["canada_cad"]
    assert isinstance(ca, dict)
    markdown = [
        "# Open Prices Canada/CAD coverage",
        "",
        f"- Dataset rows: **{report['dataset_rows']:,}**",
        f"- Dataset date range: **{report['dataset_date_min']} → {report['dataset_date_max']}**",
        f"- Canada + CAD rows: **{ca['rows']:,}** ({ca['share_of_dataset']:.3%})",
        f"- Canada + CAD date range: **{ca['date_min']} → {ca['date_max']}**",
        f"- Unique Canadian locations: **{ca['unique_locations']:,}**",
        f"- Unique product codes: **{ca['unique_product_codes']:,}**",
        f"- Checksum-valid GTINs: **{ca['unique_checksum_valid_gtins']:,}**",
        f"- Receipt/price-tag proof-backed rows: **{ca['proof_backed_receipt_or_tag_rows']:,}**",
        f"- Conservative physical OSM-shop rows: **{ca['physical_osm_shop_rows']:,}**",
        f"- Strict rows before package-quantity join: **{ca['strict_rows_before_quantity_join']:,}**",
        f"- Strict unique product codes before package-quantity join: **{ca['strict_unique_product_codes_before_quantity_join']:,}**",
        f"- Rows in last 7 / 30 / 90 days: **{ca['rows_last_7_days']:,} / {ca['rows_last_30_days']:,} / {ca['rows_last_90_days']:,}**",
        "",
        "Package quantity is intentionally not guessed from receipt quantity. A separate product-metadata join is required before cross-SKU unit-value ranking.",
    ]
    args.markdown.write_text("\n".join(markdown) + "\n", encoding="utf-8")

    print(args.markdown.read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
