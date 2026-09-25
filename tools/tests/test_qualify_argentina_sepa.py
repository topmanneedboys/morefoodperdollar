from __future__ import annotations

import gzip
import io
import json
import tempfile
import unittest
import zipfile
from datetime import date
from pathlib import Path

from tools.qualify_argentina_sepa import (
    EXPECTED_RELEASE_DATE,
    QualificationRun,
    SepaConfig,
    SourceVerificationError,
    is_valid_gtin,
    parse_quantity,
)


COMMERCE_HEADER = (
    "id_comercio|id_bandera|comercio_cuit|comercio_razon_social|"
    "comercio_bandera_nombre|comercio_bandera_url|"
    "comercio_ultima_actualizacion|comercio_version_sepa\n"
)
STORE_HEADER = (
    "id_comercio|id_bandera|id_sucursal|sucursales_nombre|sucursales_tipo|"
    "sucursales_calle|sucursales_numero|sucursales_latitud|"
    "sucursales_longitud|sucursales_codigo_postal|sucursales_localidad|"
    "sucursales_provincia\n"
)
PRODUCT_HEADER = (
    "id_comercio|id_bandera|id_sucursal|id_producto|productos_ean|"
    "productos_descripcion|productos_cantidad_presentacion|"
    "productos_unidad_medida_presentacion|productos_marca|"
    "productos_precio_lista|productos_precio_referencia|"
    "productos_cantidad_referencia|productos_unidad_medida_referencia|"
    "productos_precio_unitario_promo1|productos_leyenda_promo1|"
    "productos_precio_unitario_promo2|productos_leyenda_promo2\n"
)


def commerce_text(update: str = "2026-09-06T12:00:00-03:00") -> str:
    return COMMERCE_HEADER + f"1|1|30123456789|Comercio SA|Super|https://example.test|{update}|1.0\n"


def store_text(
    *,
    first_coordinates: tuple[str, str] = ("-34.60", "-58.40"),
    include_second: bool = True,
    include_third: bool = False,
) -> str:
    rows = [
        "1|1|001|Sucursal Centro|SUPERMERCADO|Calle|10|"
        f"{first_coordinates[0]}|{first_coordinates[1]}|1000|Córdoba|Buenos Aires\n"
    ]
    if include_second:
        rows.append("1|1|002|Sucursal Sin Geo|SUPERMERCADO|Otra|20|||1001|Rosario|Santa Fe\n")
    if include_third:
        rows.append("1|1|003|Sucursal Fuera|SUPERMERCADO|Lejos|30|-10.0|-10.0|1002|Mendoza|Mendoza\n")
    return STORE_HEADER + "".join(rows)


def product_row(
    product_id: str,
    *,
    store: str = "001",
    price: str = "1234.50",
    quantity: str = "500",
    unit: str = "GRM",
    description: str = "Café descripción",
    brand: str = "Marca",
    promo_price: str = "",
    promo_condition: str = "",
) -> str:
    return "|".join(
        [
            "1",
            "1",
            store,
            product_id,
            "1",
            description,
            quantity,
            unit,
            brand,
            price,
            price,
            quantity,
            unit,
            promo_price,
            promo_condition,
            "",
            "",
        ]
    ) + "\n"


def nested_zip_bytes(
    *,
    commerce: str | None = None,
    stores: str | None = None,
    products: str | None = None,
    corrupt: bool = False,
) -> bytes:
    if corrupt:
        return b"not-a-zip"
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        if commerce is not None:
            archive.writestr("comercio.csv", commerce.encode("utf-8"))
        if stores is not None:
            archive.writestr("sucursales.csv", stores.encode("utf-8"))
        if products is not None:
            archive.writestr("productos.csv", products.encode("utf-8"))
    return output.getvalue()


def outer_zip(path: Path, nested: list[tuple[str, bytes]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_STORED) as archive:
        for name, payload in nested:
            archive.writestr(name, payload, compress_type=zipfile.ZIP_STORED)


def run_fixture(
    root: Path,
    nested: list[tuple[str, bytes]],
    *,
    output_name: str = "output",
    release_date: date = EXPECTED_RELEASE_DATE,
):
    source = root / "Windows path" / "sepa fixture.zip"
    outer_zip(source, nested)
    output_dir = root / output_name
    return QualificationRun(
        SepaConfig(
            input_path=source,
            release_date=release_date,
            expected_sha256=None,
            expected_bytes=None,
            output_dir=output_dir,
        )
    ).run(), output_dir


class ArgentinaSepaQualificationTest(unittest.TestCase):
    def test_unit_aliases_are_explicit_and_unknown_stays_unknown(self):
        self.assertEqual(("GRAM", 500), parse_quantity("500", "GRM"))
        self.assertEqual(("GRAM", 1000), parse_quantity("1", "KG"))
        self.assertEqual(("MILLILITRE", 473), parse_quantity("473", "ml"))
        self.assertEqual(("MILLILITRE", 1000), parse_quantity("1", "ltr"))
        self.assertEqual(("COUNT", 1), parse_quantity("1", "UNI"))
        self.assertEqual(("COUNT", 1), parse_quantity("1", "UD"))
        self.assertIsNone(parse_quantity("1", "PACK"))
        self.assertIsNone(parse_quantity("", "GRM"))

    def test_valid_nested_release_streams_rows_and_preserves_unknowns(self):
        products = PRODUCT_HEADER + "".join(
            [
                product_row("0036000291452", price="1234.50", quantity="500", unit="GRM"),
                product_row("123", price="1234.50", quantity="1", unit="PACK"),
                product_row("7790000000000", store="002", price="2345.00", quantity="1", unit="UNI", promo_price="2000.00", promo_condition="Tarjeta y cupón"),
            ]
        )
        report, output = run_fixture(
            Path(self._tmp.name),
            [("2026-09-06/retailer.zip", nested_zip_bytes(commerce=commerce_text(), stores=store_text(), products=products))],
        )
        self.assertEqual("CONDITIONAL_GO_OFFLINE_ADAPTER_ONLY", report["status"])
        self.assertEqual(1, report["source"]["nested_retailer_zip_count"])
        self.assertEqual(3, report["scale"]["rows_scanned"])
        self.assertEqual(3, report["identity"]["product_id_present_rows"])
        self.assertEqual(1, report["identity"]["gtin_valid_rows"])
        self.assertEqual(1, report["identity"]["checksum_valid_unique_gtins"])
        self.assertEqual(2, report["quantity_unit_value"]["quantity_parseable_rows"])
        self.assertEqual(1, report["quantity_unit_value"]["quantity_unknown_rows"])
        self.assertEqual(3, report["quantity_unit_value"]["quantity_explicit_rows"])
        self.assertEqual(3, report["quantity_unit_value"]["quantity_numeric_structured_rows"])
        self.assertEqual(1, report["quantity_unit_value"]["quantity_unknown_unit_rows"])
        self.assertEqual(0, report["quantity_unit_value"]["quantity_invalid_value_rows"])
        self.assertEqual(2, report["quantity_unit_value"]["unit_value_ready_rows"])
        self.assertEqual(1, report["price_quality"]["promotion_bearing_rows"])
        self.assertEqual(3, report["price_quality"]["positive_list_price_rows"])
        self.assertEqual(3, report["diagnostics"]["counts"]["accepted_current_price_rows"])
        self.assertEqual("IN_STORE_ONLY", report["delivery_pickup"]["v1_boundary"])
        accepted = output / "accepted-observations.ndjson.gz"
        with gzip.open(accepted, "rt", encoding="utf-8") as handle:
            observations = [json.loads(line) for line in handle]
        self.assertEqual(3, len(observations))
        self.assertTrue(all(item["offer"]["availability"] == "UNKNOWN" for item in observations))
        self.assertIsNone(observations[1]["quantity"])
        self.assertEqual("UNKNOWN", observations[1]["quantity_status"])
        self.assertEqual("Tarjeta y cupón", observations[2]["offer"]["promotions"][0]["condition"])
        self.assertEqual("UNKNOWN", observations[2]["offer"]["promotions"][0]["eligibility"])
        self.assertIsNone(observations[0]["offer"]["observation_time"])

    def test_zero_byte_nested_package_is_explicit_quarantine(self):
        report, output = run_fixture(Path(self._tmp.name), [("2026-09-06/empty.zip", b"")])
        self.assertEqual(1, report["source"]["nested_zero_byte_count"])
        self.assertEqual(1, report["diagnostics"]["counts"]["quarantine_EMPTY_PROVIDER_PACKAGE"])
        with gzip.open(output / "quarantine.ndjson.gz", "rt", encoding="utf-8") as handle:
            record = json.loads(next(handle))
        self.assertEqual("EMPTY_PROVIDER_PACKAGE", record["reason"])

    def test_corrupt_nested_package_is_zip_invalid(self):
        report, _ = run_fixture(Path(self._tmp.name), [("2026-09-06/corrupt.zip", b"not-a-zip")])
        self.assertEqual(1, report["diagnostics"]["package_status_counts"]["ZIP_INVALID"])
        self.assertEqual(1, report["diagnostics"]["counts"]["quarantine_ZIP_INVALID"])

    def test_nested_crc_failure_is_zip_invalid(self):
        payload = bytearray(
            nested_zip_bytes(
                commerce=commerce_text(),
                stores=store_text(),
                products=PRODUCT_HEADER + product_row("0036000291452"),
            )
        )
        with zipfile.ZipFile(io.BytesIO(payload)) as archive:
            info = archive.getinfo("productos.csv")
        del info  # The central-directory record is the CRC authority.
        central = 0
        while True:
            central = payload.find(b"PK\x01\x02", central)
            if central < 0:
                self.fail("productos.csv central-directory record not found")
            name_length = int.from_bytes(payload[central + 28 : central + 30], "little")
            name = bytes(payload[central + 46 : central + 46 + name_length])
            if name == b"productos.csv":
                payload[central + 16] ^= 0x01
                break
            central += 4
        report, _ = run_fixture(Path(self._tmp.name), [("2026-09-06/crc.zip", bytes(payload))])
        self.assertEqual(1, report["diagnostics"]["package_status_counts"]["ZIP_INVALID"])
        self.assertEqual(1, report["diagnostics"]["counts"]["quarantine_ZIP_INVALID"])

    def test_missing_required_files_are_structural_failures(self):
        cases = {
            "comercio.csv": nested_zip_bytes(stores=store_text(), products=PRODUCT_HEADER + product_row("123")),
            "sucursales.csv": nested_zip_bytes(commerce=commerce_text(), products=PRODUCT_HEADER + product_row("123")),
            "productos.csv": nested_zip_bytes(commerce=commerce_text(), stores=store_text()),
        }
        for missing, payload in cases.items():
            with self.subTest(missing=missing):
                report, _ = run_fixture(Path(self._tmp.name), [(f"2026-09-06/{missing}.zip", payload)], output_name=f"out-{missing}")
                self.assertEqual(1, report["diagnostics"]["counts"]["quarantine_REQUIRED_FILE_MISSING"])

    def test_price_failures_and_plausibility_signals_are_not_accepted(self):
        products = PRODUCT_HEADER + "".join(
            [
                product_row("0036000291452", price="0"),
                product_row("123456789", price="-5"),
                product_row("7790000000000", price="5"),
                product_row("7790000000001", price="10000001"),
                product_row("7790000000002", price="not-a-price"),
            ]
        )
        report, output = run_fixture(
            Path(self._tmp.name),
            [("2026-09-06/retailer.zip", nested_zip_bytes(commerce=commerce_text(), stores=store_text(), products=products))],
        )
        self.assertEqual(5, report["price_quality"]["invalid_required_price_rows"] + report["price_quality"]["price_under_ars_10_rows"] + report["price_quality"]["price_over_ars_10000000_rows"])
        self.assertEqual(2, report["price_quality"]["zero_or_negative_price_rows"])
        self.assertEqual(1, report["price_quality"]["malformed_price_rows"])
        self.assertEqual(1, report["price_quality"]["price_under_ars_10_rows"])
        self.assertEqual(1, report["price_quality"]["price_over_ars_10000000_rows"])
        self.assertEqual(0, report["diagnostics"]["counts"]["accepted_current_price_rows"])
        with gzip.open(output / "accepted-observations.ndjson.gz", "rt", encoding="utf-8") as handle:
            self.assertEqual([], list(handle))

    def test_store_join_coordinate_and_freshness_gates(self):
        products = PRODUCT_HEADER + "".join(
            [
                product_row("0036000291452", store="001"),
                product_row("123", store="002", price="200"),
                product_row("7790000000000", store="003", price="200"),
                product_row("7790000000001", store="999", price="200"),
            ]
        )
        report, _ = run_fixture(
            Path(self._tmp.name),
            [("2026-09-06/retailer.zip", nested_zip_bytes(commerce=commerce_text(), stores=store_text(include_third=True), products=products))],
        )
        counts = report["diagnostics"]["counts"]
        self.assertEqual(1, counts["quarantine_GEO_OUT_OF_BOUNDS"])
        self.assertEqual(1, counts["quarantine_UNKNOWN_STORE_REFERENCE"])
        self.assertEqual(1, report["scale"]["coordinate_incomplete_store_rows"])
        self.assertEqual(1, report["scale"]["coordinate_out_of_bounds_store_rows"])
        self.assertEqual(2, counts["accepted_current_price_rows"])
        stale_products = PRODUCT_HEADER + product_row("0036000291452")
        stale_report, _ = run_fixture(
            Path(self._tmp.name),
            [("2026-09-06/stale.zip", nested_zip_bytes(commerce=commerce_text("2026-09-01T12:00:00-03:00"), stores=store_text(), products=stale_products))],
            output_name="stale-output",
        )
        self.assertEqual(1, stale_report["freshness"]["stale_package_count"])
        self.assertEqual(1, stale_report["diagnostics"]["counts"]["quarantine_STALE_PROVIDER_PACKAGE"])

    def test_partial_coordinate_is_geo_incomplete_not_invalid(self):
        products = PRODUCT_HEADER + product_row("0036000291452")
        report, output = run_fixture(
            Path(self._tmp.name),
            [
                (
                    "2026-09-06/partial-coordinate.zip",
                    nested_zip_bytes(
                        commerce=commerce_text(),
                        stores=store_text(first_coordinates=("", "-58.40"), include_second=False),
                        products=products,
                    ),
                )
            ],
        )
        self.assertEqual(1, report["scale"]["coordinate_incomplete_store_rows"])
        self.assertEqual(0, report["scale"]["coordinate_invalid_store_rows"])
        with gzip.open(output / "accepted-observations.ndjson.gz", "rt", encoding="utf-8") as handle:
            observation = json.loads(next(handle))
        self.assertEqual("GEO_INCOMPLETE", observation["store"]["geo_status"])
        self.assertIsNone(observation["store"]["latitude"])
        self.assertEqual("-58.4", observation["store"]["longitude"])

    def test_malformed_product_row_is_explicitly_quarantined(self):
        products = PRODUCT_HEADER + product_row("0036000291452") + "malformed|row\n"
        report, output = run_fixture(
            Path(self._tmp.name),
            [
                (
                    "2026-09-06/malformed-products.zip",
                    nested_zip_bytes(commerce=commerce_text(), stores=store_text(), products=products),
                )
            ],
        )
        self.assertEqual(1, report["diagnostics"]["counts"]["quarantine_MALFORMED_ROW"])
        with gzip.open(output / "quarantine.ndjson.gz", "rt", encoding="utf-8") as handle:
            records = [json.loads(line) for line in handle]
        self.assertTrue(any(record["reason"] == "MALFORMED_ROW" for record in records))

    def test_duplicate_scope_and_identity_conflict_are_measured(self):
        products = PRODUCT_HEADER + "".join(
            [
                product_row("0036000291452", price="100"),
                product_row("0036000291452", price="110", description="Café variante"),
            ]
        )
        report, _ = run_fixture(
            Path(self._tmp.name),
            [("2026-09-06/retailer.zip", nested_zip_bytes(commerce=commerce_text(), stores=store_text(), products=products))],
        )
        self.assertEqual(1, report["identity"]["duplicate_provider_product_id_rows"])
        self.assertEqual(1, report["identity"]["conflicting_product_id_scopes"])
        self.assertEqual(1, report["price_quality"]["duplicate_store_product_scopes"])
        self.assertEqual(1, report["price_quality"]["conflicting_store_product_scopes"])

    def test_repeat_run_is_deterministic_and_windows_path_is_supported(self):
        products = PRODUCT_HEADER + product_row("0036000291452", price="100")
        nested = [("2026-09-06/retailer.zip", nested_zip_bytes(commerce=commerce_text(), stores=store_text(), products=products))]
        first, first_output = run_fixture(Path(self._tmp.name), nested, output_name="first")
        second, second_output = run_fixture(Path(self._tmp.name), nested, output_name="second")
        self.assertEqual(json.dumps(first, sort_keys=True), json.dumps(second, sort_keys=True))
        for name in ("accepted-observations.ndjson.gz", "quarantine.ndjson.gz", "manifest.json", "manifest.sha256"):
            self.assertEqual((first_output / name).read_bytes(), (second_output / name).read_bytes())

    def test_exact_hash_mismatch_fails_before_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "source.zip"
            outer_zip(source, [("retailer.zip", nested_zip_bytes(commerce=commerce_text(), stores=store_text(), products=PRODUCT_HEADER + product_row("123")))])
            with self.assertRaises(SourceVerificationError):
                QualificationRun(
                    SepaConfig(
                        input_path=source,
                        expected_sha256="0" * 64,
                        expected_bytes=None,
                        output_dir=root / "out",
                    )
                ).run()
            self.assertFalse((root / "out" / "accepted-observations.ndjson.gz").exists())

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()

    def tearDown(self):
        self._tmp.cleanup()


if __name__ == "__main__":
    unittest.main()
