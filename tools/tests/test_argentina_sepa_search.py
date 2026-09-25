from __future__ import annotations

import unittest

from tools.argentina_sepa_search import SearchError, normalize_spanish, search_products


def product(key: str, name: str, brand: str | None = None, gtin: str | None = None) -> dict:
    return {"productEvidenceKey": key, "name": name, "brand": brand, "gtin": gtin}


class ArgentinaSepaSearchTest(unittest.TestCase):
    def setUp(self) -> None:
        self.products = [
            product("p-leche-1", "LECHE ENTERA 1 L", "La Serenísima"),
            product("p-leche-2", "LECHE DESCREMADA 1 L", "Tregar"),
            product("p-leche-negative", "LECHE Y TONICO MICELAR", "Cosmética"),
            product("p-pan-1", "PAN LACTAL BLANCO", "Bimbo"),
            product("p-pan-brand-negative", "MANTECA CON MARCA PAN"),
            product("p-arroz-1", "ARROZ LARGO FINO 1 KG"),
            product("p-arroz-negative", "CONDIMENTO PARA ARROZ"),
            product("p-aceite-negative", "ACEITE PARA BEBE"),
            product("p-huevo-1", "HUEVOS BLANCOS X 12"),
            product("p-huevo-negative", "FIDEOS CON HUEVO"),
            product("p-huevo-chocolate", "HUEVO CHOCOLATE SORPRESA"),
            product("p-fideos-1", "FIDEOS TIRABUZON"),
            product("p-azucar-1", "AZUCAR BLANCA 1 KG"),
            product("p-azucar-negative", "GASEOSA SIN AZUCAR"),
            product("p-queso-1", "QUESO CREMOSO"),
            product("p-queso-negative", "PASTA RELLENA DE QUESO"),
            product("p-cereal-1", "CEREAL DE MAIZ"),
            product("p-cereal-negative", "YOGUR CON CEREAL"),
            product("p-gaseosa-negative", "GASEOSA EMPLEADOS EXTERNOS"),
            product("p-cafe-1", "CAFÉ MOLIDO"),
            product("p-papel-1", "PAPEL HIGIÉNICO DOBLE HOJA"),
            product("p-papel-negative", "PALITO PORTO ROLLO PAPEL HIGIENICO"),
            product("p-panales-1", "PAÑALES TALLE G"),
            product("p-boundary", "COMPANERO DE COCINA"),
        ]

    def test_accents_case_punctuation_and_phrase_tokens(self):
        self.assertEqual(normalize_spanish("  Café!!! "), "cafe")
        self.assertEqual(normalize_spanish("Papel higiénico"), "papel higienico")
        self.assertEqual(search_products(self.products, "CAFÉ")[0].product_evidence_key, "p-cafe-1")
        self.assertEqual(search_products(self.products, "papel-higienico")[0].product_evidence_key, "p-papel-1")

    def test_known_false_positive_classes_are_excluded(self):
        for query, negative in (
            ("pan", "p-pan-brand-negative"),
            ("arroz", "p-arroz-negative"),
            ("huevos", "p-huevo-negative"),
            ("azúcar", "p-azucar-negative"),
            ("queso", "p-queso-negative"),
            ("cereal", "p-cereal-negative"),
            ("leche", "p-leche-negative"),
            ("aceite", "p-aceite-negative"),
            ("huevos", "p-huevo-chocolate"),
            ("gaseosa", "p-gaseosa-negative"),
            ("papel higiénico", "p-papel-negative"),
        ):
            results = search_products(self.products, query)
            self.assertNotIn(negative, {result.product_evidence_key for result in results}, query)

    def test_token_boundary_and_unknown_query(self):
        self.assertEqual(search_products(self.products, "panaderia"), [])
        self.assertEqual(search_products(self.products, "producto inexistente"), [])
        self.assertEqual(search_products(self.products, "!!!"), [])
        self.assertEqual(search_products(self.products, ""), [])

    def test_plural_aliases_and_stable_tie_breaking(self):
        self.assertEqual(search_products(self.products, "fideos")[0].product_evidence_key, "p-fideos-1")
        self.assertEqual(search_products(self.products, "panales")[0].product_evidence_key, "p-panales-1")
        tied = [product("a", "LECHE ESPECIAL"), product("b", "LECHE ESPECIAL")]
        self.assertEqual([result.product_evidence_key for result in search_products(tied, "leche", limit=2)], ["a", "b"])
        self.assertEqual([result.product_evidence_key for result in search_products(list(reversed(tied)), "leche", limit=2)], ["a", "b"])

    def test_limit_and_candidate_bound_are_explicit(self):
        with self.assertRaises(SearchError):
            search_products(self.products, "leche", limit=6)
        with self.assertRaises(SearchError):
            search_products(self.products, "leche", max_candidates=2)


if __name__ == "__main__":
    unittest.main()
