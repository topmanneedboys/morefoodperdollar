import csv
import hashlib
import io
import json
import tempfile
import zipfile
from pathlib import Path
import unittest

from tools.audit_argentina_product_identity import audit_source


class ArgentinaProductIdentityAuditTests(unittest.TestCase):
    def _source(self, directory: Path) -> Path:
        rows = [
            ["1", "100", "1", "ARROZ BLANCO X 1 KG", "1", "KG", "MARCA A"],
            ["1", "100", "1", "ARROZ BLANCO X 1 KG", "1", "KG", "MARCA A"],
            ["1", "101", "1", "BEBIDA DE ARROZ X 1 L", "1", "LT", "MARCA B"],
            ["1", "102", "1", "LOCION DE ZANAHORIA X 200 ML", "200", "ML", "MARCA C"],
            ["1", "103", "1", "PRODUCTO PRUEBA X 1 L", "1", "LT", "MARCA D"],
        ]
        header = [
            "id_comercio",
            "id_producto",
            "id_sucursal",
            "productos_descripcion",
            "productos_cantidad_presentacion",
            "productos_unidad_medida_presentacion",
            "productos_marca",
            "productos_ean",
        ]
        product_csv = io.StringIO(newline="")
        writer = csv.writer(product_csv, delimiter="|", lineterminator="\n")
        writer.writerow(header)
        for row in rows:
            writer.writerow(row[:3] + [row[3], row[4], row[5], row[6], "7790000000000"])
        inner_bytes = io.BytesIO()
        with zipfile.ZipFile(inner_bytes, "w", zipfile.ZIP_DEFLATED) as inner:
            inner.writestr("productos.csv", product_csv.getvalue().encode("utf-8"))
        source = directory / "source.zip"
        with zipfile.ZipFile(source, "w", zipfile.ZIP_STORED) as outer:
            outer.writestr(
                "2026-09-18/sepa_1_comercio-sepa-1_2026-09-18.zip",
                inner_bytes.getvalue(),
            )
        return source

    def test_audits_unique_identities_and_reviewed_families(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source = self._source(directory)
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            identity_output = directory / "identities.jsonl.gz"
            report = audit_source(
                source,
                expected_sha256=digest,
                expected_bytes=source.stat().st_size,
                release_date="2026-09-18",
                identity_output=identity_output,
            )
            self.assertEqual(report["scan"]["productRowsScanned"], 5)
            self.assertEqual(report["scan"]["distinctProductIdentities"], 4)
            self.assertEqual(report["scan"]["distinctProviderProductIds"], 4)
            self.assertEqual(report["scan"]["distinctCommerceIds"], 1)
            self.assertEqual(report["scan"]["distinctCanonicalNames"], 4)
            self.assertEqual(report["identityCounts"]["classifications"]["UNCLASSIFIED"], 1)
            self.assertIn("rice", report["families"])
            self.assertIn("carrot", report["families"])
            self.assertTrue(identity_output.is_file())

            import gzip

            with gzip.open(identity_output, "rt", encoding="utf-8") as handle:
                identities = [json.loads(line) for line in handle if line.strip()]
            self.assertEqual(len(identities), 4)
            carrot = next(item for item in identities if item["providerProductId"] == "102")
            self.assertEqual(carrot["classification"], "DERIVED")
            self.assertEqual(carrot["familyCandidates"][0]["family"], "carrot")
            rice = next(item for item in identities if item["providerProductId"] == "100")
            self.assertEqual(rice["classification"], "DIRECT")
            self.assertGreaterEqual(rice["sourceEvidence"]["rows"], 2)


if __name__ == "__main__":
    unittest.main()
