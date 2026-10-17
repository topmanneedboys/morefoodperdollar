from __future__ import annotations

import gzip
import json
import tempfile
import unittest
from pathlib import Path

from tools.product_identity_lexicon import (
    POLICY_VERSION,
    SCHEMA_VERSION,
    build_family_clarification,
    build_identity_lexicon,
    derive_identity_record,
    family_query_form,
    family_query_key,
    family_selection_form,
    filter_family_candidates,
)


def _row(key: str, name: str, *, brand: str = "Marca") -> dict[str, object]:
    return {
        "brand": brand,
        "gtin": key.rsplit(":", 1)[-1],
        "name": name,
        "productEvidenceKey": key,
        "quantity": {"unit": "GRAM", "value": "500"},
    }


class ProductIdentityLexiconTests(unittest.TestCase):
    def test_direct_family_adds_curated_bilingual_aliases(self) -> None:
        value = derive_identity_record(_row("p:rice", "ARROZ LARGO FINCA 500 G"))
        self.assertEqual(value["classification"], "DIRECT")
        self.assertEqual(value["familyCandidates"][0]["family"], "rice")
        self.assertEqual(value["familyCandidates"][0]["role"], "DIRECT")
        aliases = {(item["value"], item["language"], item["provenance"]) for item in value["aliases"]}
        self.assertIn(("arroz", "es", "CURATED_BILINGUAL"), aliases)
        self.assertIn(("rice", "en", "CURATED_BILINGUAL"), aliases)

    def test_reviewed_common_vocabulary_adds_family_aliases(self) -> None:
        value = derive_identity_record(_row("p:apple", "MANZANA ROJA 1 KG"))
        self.assertEqual(value["classification"], "DIRECT")
        self.assertEqual(value["familyCandidates"][0]["family"], "apple")
        aliases = {(item["value"], item["language"]) for item in value["aliases"]}
        self.assertIn(("manzana", "es"), aliases)
        self.assertIn(("apple", "en"), aliases)

    def test_catalog_rule_data_covers_common_terms_without_cross_family_guessing(self) -> None:
        wine = derive_identity_record(_row("p:wine", "VINO TINTO MALBEC X 750 CC"))
        self.assertIn(
            ("wine", "DIRECT"),
            {(item["family"], item["role"]) for item in wine["familyCandidates"]},
        )
        toothpaste = derive_identity_record(_row("p:toothpaste", "CREMA DENTAL COLGATE X 90 G"))
        self.assertIn(
            ("toothpaste", "DIRECT"),
            {(item["family"], item["role"]) for item in toothpaste["familyCandidates"]},
        )
        pasta_dental = derive_identity_record(_row("p:pasta-dental", "PASTA DENTAL COLGATE"))
        roles = {(item["family"], item["role"]) for item in pasta_dental["familyCandidates"]}
        self.assertIn(("toothpaste", "DIRECT"), roles)
        self.assertNotIn(("pasta", "DIRECT"), roles)

    def test_second_catalog_rule_batch_covers_residual_departments_conservatively(self) -> None:
        cases = (
            ("p:juice", "JUGO X 500 CC NARANJA", "juice", "DIRECT"),
            ("p:cleaner", "LIMPIADOR CREMOSO LYSOFORM X 450 CC", "cleaner", "DIRECT"),
            ("p:facial-cleanser", "GEL LIMPIADOR FACIAL NEUTROGENA X 354 CC", "facial_cleanser", "DIRECT"),
            ("p:clothing", "CAMPERA TEX DE NIEVE I882496", "jacket", "DIRECT"),
            ("p:audio", "AURICULAR VINCHA BT SONY WH CH520", "headphones", "DIRECT"),
            ("p:appliance", "LAVARROPAS CS WHIRLPOOL 9KG", "washing_machine", "DIRECT"),
            ("p:cake-pan", "BUDINERA DE SILICONA HOME", "pound_cake", None),
            ("p:fridge-accessory", "QUITAOLORES PARA HELADERA ARM HAMMER X 396 GRS", "refrigerator", "DERIVED"),
        )
        for key, name, family, expected_role in cases:
            with self.subTest(name=name):
                value = derive_identity_record(_row(key, name))
                matches = [item for item in value["familyCandidates"] if item["family"] == family]
                if expected_role is None:
                    self.assertFalse(matches)
                else:
                    self.assertEqual([item["role"] for item in matches], [expected_role])

    def test_catalog_extensions_and_third_batch_cover_observed_plural_and_abbreviated_names(self) -> None:
        cases = (
            ("p:gaseo", "GASEO COLA LIGHT X 2.25 LT", "soft_drink", "DIRECT"),
            ("p:sahumerios", "SAHUMERIOS ZED BLACK HEXAGONAL 15 UN", "incense", "DIRECT"),
            ("p:blueberry", "ARANDANO CUB 125 GR CUB 1 UN.", "blueberry", "DIRECT"),
            ("p:pajamas", "HG PIJAMA ESTAMPADO", "pajamas", "DIRECT"),
            ("p:swimwear", "TRAJE DE BANO ENTERIZO TEX", "swimwear", "DIRECT"),
            ("p:wipes", "TOALLITAS HUMEDAS BABYSEC X 50 UNI", "baby_wipes", "DIRECT"),
            ("p:menstrual", "TOALLA FEMENINA NORMAL X 8 UNI", "menstrual_care", "DIRECT"),
            ("p:hair-dye", "ISSUE KIT COLORACION CASTANO X 1 UNI", "hair_color", "DIRECT"),
        )
        for key, name, family, expected_role in cases:
            with self.subTest(name=name):
                value = derive_identity_record(_row(key, name))
                matches = [item for item in value["familyCandidates"] if item["family"] == family]
                self.assertEqual([item["role"] for item in matches], [expected_role])

    def test_negative_context_keeps_similar_names_out_of_the_wrong_family(self) -> None:
        cases = (
            ("AJO EN MALLA BOL 2 UNI", "swimwear"),
            ("CREMA DEPILATORIA VEET AXILAS Y BIKINI CJA 100CC", "swimwear"),
            ("TOALLAS FEMENINAS XTRA NOCTURNAS 8U", "towel"),
            ("TOALLAS FEM.SIEMPRE LIBRE NOCT. 8un", "towel"),
            ("TOALLA HIG. NOCT. C/ALAS MAXCURV", "towel"),
            ("NOSOTRAS TOALLA BUENAS NOCHES SUAVE MAX CURV 16U", "towel"),
            ("TOALLA CALIPSO NORMAL C CALENDULA X 8U", "towel"),
            ("TOALLA FEMEN C/ALAS C/DEO", "towel"),
            ("TOALLAS HUMEDAS HUGGIES X 48 UNI", "towel"),
            ("ARTICULO ENVASE GASEOSA RET X 2.5 LT.X 1 UNI", "soft_drink"),
        )
        for name, family in cases:
            with self.subTest(name=name):
                value = derive_identity_record(_row(f"p:{family}", name))
                candidate = next(item for item in value["familyCandidates"] if item["family"] == family)
                self.assertEqual(candidate["role"], "DERIVED")

    def test_v11_abbreviation_batch_resolves_observed_retail_names(self) -> None:
        cases = (
            ("p:deodorant", "ANTITRANSP FEM CREMA CLINICAL REXONA X 58 GR", "deodorant", "DIRECT"),
            ("p:mouthwash", "ENJ.BUCAL LISTERINE FRESHBURST X250ML", "mouthwash", "DIRECT"),
            ("p:chips", "PAPAS FRITAS CLASICAS LAYS X 230 GRS", "potato_chips", "DIRECT"),
            ("p:salt", "SAL FINA CELUSAL PAQUETE X 500 GRS", "salt", "DIRECT"),
            ("p:spice", "CONDIMENTO P/CARNES ALICANTE SOB 25 GRM", "spice", "DIRECT"),
            ("p:freshener", "DEO AMBIENTE GLADE LAVANDA X 360 CC", "air_freshener", "DIRECT"),
            ("p:dog-food", "ALIM.P/PERRO ADULTO RAZA PEQUENA X 3 KG", "dog_food", "DIRECT"),
            ("p:cat-food", "ALIM GATO ADULTO PESCADO X 3 KG", "cat_food", "DIRECT"),
            ("p:hair-color", "COLOR PERM KIT N 6 RUBIO OSCURO", "hair_color", "DIRECT"),
            ("p:conditioner", "ACONDIC. HIDRA RELLENO ELVIVE BOT 400 ML", "conditioner", "DIRECT"),
            ("p:shampoo", "SHAM NUTRITIVO ELVIVE BOT 400 ML", "shampoo", "DIRECT"),
            ("p:detergent", "DETERG.POLVO ALA 400 GR", "detergent", "DIRECT"),
            ("p:soap", "JAB LIQ PALMOLIVE 200 ML", "soap", "DIRECT"),
            ("p:sunscreen", "PROT.SOLAR FPS 50 NIVEA 50 CC", "sunscreen", "DIRECT"),
            ("p:toothpaste", "CR DENT COLGATE ANTICARIE", "toothpaste", "DIRECT"),
            ("p:toothbrush", "COLGATE CEP DENT MED", "toothbrush", "DIRECT"),
        )
        for key, name, family, expected_role in cases:
            with self.subTest(name=name):
                value = derive_identity_record(_row(key, name))
                matches = [item for item in value["familyCandidates"] if item["family"] == family]
                self.assertEqual([item["role"] for item in matches], [expected_role])

    def test_v11_negative_context_does_not_turn_mentions_into_product_types(self) -> None:
        cases = (
            ("DEO AMBIENTE AERO LAVANDA GLADE", "deodorant"),
            ("DESODORANTE AMBIENTES ALEGRA TU DIA POETT 360 ML", "deodorant"),
            ("TABLETA CHOCO C SAL MARINA LINDT", "salt"),
            ("PAPAS FRITAS PIMIENTA NEGRA GAUCHITAS", "spice"),
            ("LOMO PIMIENTA NEGRA FETEADO", "spice"),
            ("MILANESA CON PAPAS FRITAS X PORCION", "potato_chips"),
            ("ESPATULA PARA PAPAS FRITAS 1 UN", "potato_chips"),
            ("SNACK DE ARROZ SABOR PAPAS FRITAS", "potato_chips"),
            ("ACONDICIONADOR CON ROMERO 385 ML", "spice"),
            ("ACONDICIONAD. C/ROMERO Y MENTA 385 ML", "spice"),
            ("PROTECTOR BUCAL SIMBRA", "mouthwash"),
            ("PELUche PERRO PLUSH 25 CM", "dog_food"),
        )
        for name, family in cases:
            with self.subTest(name=name):
                value = derive_identity_record(_row(f"p:negative:{family}", name))
                candidates = [item for item in value["familyCandidates"] if item["family"] == family]
                if name in {"PROTECTOR BUCAL SIMBRA", "PELUche PERRO PLUSH 25 CM"}:
                    self.assertFalse(candidates)
                else:
                    self.assertTrue(candidates)
                    self.assertEqual(candidates[0]["role"], "DERIVED")

    def test_observed_fruit_and_egg_contexts_are_not_promoted_to_direct_products(self) -> None:
        cases = (
            ("ACONDICIONADOR KIDS FRUTILLA 350 ML", "strawberry", "cosmetics"),
            ("YOGUR BEBIBLE FRUTILLA 900 G", "strawberry", "dairy"),
            ("ABRELATAS PREMIUM MANGO MANGO MADERA", "mango", "unspecified"),
            ("JUGO DE MANGO 1 L", "mango", "drink"),
            ("BAGEL DE HUEVO 185 G", "egg", "whole_egg"),
            ("ENSALADA CON HUEVO X 1 PORCION", "egg", "whole_egg"),
        )
        for name, family, forbidden_form in cases:
            with self.subTest(name=name):
                value = derive_identity_record(_row(f"p:context:{family}", name))
                matches = [item for item in value["familyCandidates"] if item["family"] == family]
                self.assertEqual(len(matches), 1)
                if family == "mango" and "JUGO" in name:
                    self.assertEqual(matches[0]["role"], "DERIVED")
                    self.assertIn("drink", matches[0]["observedForms"])
                elif family == "strawberry":
                    self.assertEqual(matches[0]["role"], "DERIVED")
                    self.assertIn(forbidden_form, matches[0]["observedForms"])
                else:
                    self.assertEqual(matches[0]["role"], "DERIVED")
                    self.assertIn("unspecified", matches[0]["observedForms"])

    def test_reviewed_fruit_queries_resolve_in_both_languages(self) -> None:
        self.assertEqual(family_query_key("frutilla"), "strawberry")
        self.assertEqual(family_query_key("strawberry"), "strawberry")
        self.assertEqual(family_query_key("mango"), "mango")

    def test_abbreviated_confectionery_and_ingredient_mentions_stay_derived(self) -> None:
        cases = (
            ("Huevo MILKA leche x22g PAQ-22-gr.", "egg"),
            ("Huevo KINDERSorpresa Lei Pack 2x48 40 gr CJA-40-gr.", "egg"),
            ("FID LUCCHETTI SPAG HUEVO", "egg"),
            ("GALLETAS VAINILLAS AL HUEVO SANCHO PANZA PAQ 200 GRM", "egg"),
            ("HARINA S TACC DE ARROZ BLANCO ABUCEL X 500 GRS", "rice"),
            ("GALLETA ARROZ INTEGRAL", "rice"),
        )
        for name, family in cases:
            with self.subTest(name=name):
                value = derive_identity_record(_row(f"p:abbrev:{family}", name))
                candidate = next(item for item in value["familyCandidates"] if item["family"] == family)
                self.assertEqual(candidate["role"], "DERIVED")

    def test_v12_argentine_food_families_cover_alfajor_flour_and_peanut_forms(self) -> None:
        cases = (
            ("p:alfajor", "ALFAJOR CHOCOLATE BLANCO RELLENO DULCE DE LECHE X 60 G", "alfajor", "DIRECT", {"chocolate", "dulce_de_leche"}),
            ("p:flour", "HARINA DE TRIGO 0000 X 1 KG", "flour", "DIRECT", {"wheat"}),
            ("p:peanut", "MANI TOSTADO SALADO X 500 G", "peanut", "DIRECT", {"whole"}),
            ("p:peanut-butter", "PASTA DE MANI NATURAL X 350 G", "peanut", "DERIVED", {"butter"}),
            ("p:peanut-chocolate", "CHOCOLATE CON MANI X 90 G", "peanut", "DERIVED", {"snack"}),
        )
        for key, name, family, role, forms in cases:
            with self.subTest(name=name):
                value = derive_identity_record(_row(key, name))
                candidate = next(item for item in value["familyCandidates"] if item["family"] == family)
                self.assertEqual(candidate["role"], role)
                self.assertTrue(forms.intersection(candidate["observedForms"]))

    def test_v12_flour_negative_context_keeps_non_food_mentions_derived(self) -> None:
        cases = (
            ("FERTILIZANTE HARINA DE HUESO X 250", "flour"),
            ("HARINA DE HUESO GRHESA X 500 GR", "flour"),
            ("3D TUBITOS DE HARINA DE MAIZ SABOR QUESO X 43 G", "flour"),
            ("BAGEL CON HARINA INTEGRAL X 250 G", "flour"),
        )
        for name, family in cases:
            with self.subTest(name=name):
                value = derive_identity_record(_row(f"p:negative:{family}", name))
                candidate = next(item for item in value["familyCandidates"] if item["family"] == family)
                self.assertEqual(candidate["role"], "DERIVED")

    def test_v12_peanut_family_does_not_promote_ingredient_products_to_whole_peanuts(self) -> None:
        cases = (
            ("ACEITE DE MANI MANI KING 500 ML", "oil"),
            ("PASTA S TACC DE MANI MANI KING X 350 G", "butter"),
            ("PASTA NATURAL D/MANI CRUNCHY CROPPERS X 305 G", "butter"),
            ("CHOCOLATES CON MANI COFLER X 90 G", "snack"),
            ("BARRANOLA PASTA MANI ALM Y QUINOA X 4 UNI", "snack"),
        )
        for name, form in cases:
            with self.subTest(name=name):
                value = derive_identity_record(_row("p:peanut-derived", name))
                candidate = next(item for item in value["familyCandidates"] if item["family"] == "peanut")
                self.assertEqual(candidate["role"], "DERIVED")
                self.assertIn(form, candidate["observedForms"])

    def test_v15_meat_species_batch_resolves_observed_cuts(self) -> None:
        cases = (
            ("p:pork", "ASADO DE CERDO X KG", "pork"),
            ("p:lamb", "PALETA DE CORDERO X KG", "lamb"),
            ("p:turkey", "PECHUGA DE PAVO X KG", "turkey"),
            ("p:beef", "CENTRO DE ENTRANA VACUNO X KG", "beef"),
        )
        for key, name, family in cases:
            with self.subTest(name=name):
                value = derive_identity_record(_row(key, name))
                candidate = next(item for item in value["familyCandidates"] if item["family"] == family)
                self.assertEqual(candidate["role"], "DIRECT")
                self.assertIn("unspecified", candidate["observedForms"])

    def test_v15_meat_species_negative_context_stays_derived(self) -> None:
        cases = (
            ("ALIM.P/PERROS ADULTOS CARNE, POLLO Y CERDO PEDIGREE BSA 8KG", "pork"),
            ("VINO TINTO MALBEC CORDERO PIEL LOBO BOT X 750CC", "lamb"),
            ("WHISKAS ADULTO POUCH PAVO 85GR", "turkey"),
            ("LEGO PAVO REAL", "turkey"),
            ("MILANESA DE TERNERA CONG X 500 GR", "beef"),
            ("WRAP DE TERNERA CANON X UNID", "beef"),
        )
        for name, family in cases:
            with self.subTest(name=name):
                value = derive_identity_record(_row(f"p:negative:{family}", name))
                candidate = next(item for item in value["familyCandidates"] if item["family"] == family)
                self.assertEqual(candidate["role"], "DERIVED")

    def test_v15_meat_species_queries_keep_bilingual_family_keys(self) -> None:
        for query, expected in (
            ("cerdo", "pork"),
            ("pork", "pork"),
            ("cordero", "lamb"),
            ("lamb", "lamb"),
            ("pavo", "turkey"),
            ("turkey", "turkey"),
            ("vacuno", "beef"),
            ("veal", "beef"),
        ):
            with self.subTest(query=query):
                self.assertEqual(family_query_key(query), expected)

    def test_v15_bare_meat_query_excludes_derived_mentions(self) -> None:
        values = filter_family_candidates(
            "cerdo",
            [
                _row("p:cut", "ASADO DE CERDO X KG"),
                _row("p:pet", "ALIM.P/PERROS ADULTOS CARNE, POLLO Y CERDO PEDIGREE BSA 8KG"),
            ],
        )
        self.assertEqual([row["productEvidenceKey"] for row in values], ["p:cut"])

    def test_v20_residual_families_separate_direct_products_from_mentions(self) -> None:
        cases = (
            ("p:wafer", "OBLEA CHOCOLATE X 100 G", "wafer", "DIRECT", "unspecified"),
            ("p:wafer-en", "WAFER VANILLA X 100 G", "wafer", "DIRECT", "unspecified"),
            ("p:gum", "CHICLE BELDENT MENTA X 10 G", "chewing_gum", "DIRECT", "unspecified"),
            ("p:gum-sugar-free", "CHICLE CERO AZUCARES MENTA X 14 G", "chewing_gum", "DIRECT", "sugar_free"),
            ("p:gum-filled", "CHICLE CONFIT RELL FRUTILLA X 17 G", "chewing_gum", "DIRECT", "filled"),
            ("p:raticide", "RATICIDA BRUMOLINE 100 G", "raticide", "DIRECT", "unspecified"),
            ("p:facial", "CREMA FACIAL NIVEA HIDRATANTE X 50 G", "facial_cream", "DIRECT", "unspecified"),
        )
        for key, name, family, role, form in cases:
            with self.subTest(name=name):
                candidates = derive_identity_record(_row(key, name))["familyCandidates"]
                match = next(item for item in candidates if item["family"] == family)
                self.assertEqual(match["role"], role)
                self.assertIn(form, match["observedForms"])

    def test_v20_residual_negative_context_and_bilingual_queries(self) -> None:
        for name in (
            "AROMATIZADOR CHICLE",
            "PERFUME AUTOMOTOR CHICLE X 100 ML",
            "SHAMPOO KIDS ALGABO CHICLE X 350 ML",
            "CHICLES JUGUETE LA GALLINA X 15 G",
            "ACONDICIONADOR KIDS BUBBLE GUM CARREFOUR X 350 CC",
            "JUGO SABOR CHICLE TUTTI X 200 ML",
            "CREMA DENTAL GUM BUBBLE GUM X 100 G",
        ):
            with self.subTest(name=name):
                match = next(item for item in derive_identity_record(_row("p:negative", name))["familyCandidates"] if item["family"] == "chewing_gum")
                self.assertEqual(match["role"], "DERIVED")

        for name in (
            "CREMA DEPILATORIA FACIAL VEET X 50 G",
            "CREMA SOLAR FACIAL DERMAGLOS X 50 G",
        ):
            with self.subTest(name=name):
                self.assertFalse(any(item["family"] == "facial_cream" for item in derive_identity_record(_row("p:negative", name))["familyCandidates"]))

        for query, expected in (
            ("oblea", "wafer"),
            ("wafer", "wafer"),
            ("chicle", "chewing_gum"),
            ("chewing gum", "chewing_gum"),
            ("raticida", "raticide"),
            ("rat poison", "raticide"),
            ("crema facial", "facial_cream"),
            ("face moisturizer", "facial_cream"),
        ):
            with self.subTest(query=query):
                self.assertEqual(family_query_key(query), expected)

        self.assertEqual(family_query_form("chicle sin azucar"), ("chewing_gum", "sugar_free"))
        self.assertEqual(family_query_form("filled chewing gum"), ("chewing_gum", "filled"))
        rows = [
            _row("p:plain", "CHICLE BELDENT MENTA X 10 G"),
            _row("p:free", "CHICLE CERO AZUCARES MENTA X 14 G"),
            _row("p:filled", "CHICLE CONFIT RELL FRUTILLA X 17 G"),
            _row("p:scent", "AROMATIZADOR CHICLE"),
        ]
        self.assertEqual(
            [row["productEvidenceKey"] for row in filter_family_candidates("chicle sin azucar", rows)],
            ["p:free"],
        )
        self.assertEqual(
            [row["productEvidenceKey"] for row in filter_family_candidates("chewing gum filled", rows)],
            ["p:filled"],
        )
        clarification = build_family_clarification("chicle", rows)
        self.assertIsNotNone(clarification)
        assert clarification is not None
        self.assertEqual(
            {choice["id"] for choice in clarification["choices"]},
            {"chewing_gum:sugar_free", "chewing_gum:filled"},
        )

    def test_v21_bowl_family_keeps_appliance_mentions_out_of_bare_query(self) -> None:
        cases = (
            ("p:bowl", "BOWL CERAMICA CAROL 20 CM", "DIRECT"),
            ("p:bowl-brand", "CAROL BOWL 14 CM BEIGE", "DIRECT"),
            ("p:stand-mixer", "BATIDORA CON BOWL BLACK DECKER 500W", "DERIVED"),
            ("p:chopper", "PICADOR SUONO BOWL VIDRIO 500W", "DERIVED"),
        )
        for key, name, role in cases:
            with self.subTest(name=name):
                value = derive_identity_record(_row(key, name))
                match = next(item for item in value["familyCandidates"] if item["family"] == "bowl")
                self.assertEqual(match["role"], role)
                self.assertIn("bowl", match["matchedTerms"])

        self.assertEqual(family_query_key("bowl"), "bowl")
        rows = [
            _row("p:direct", "BOWL CERAMICA CAROL 20 CM"),
            _row("p:mixer", "BATIDORA CON BOWL BLACK DECKER 500W"),
            _row("p:chopper", "PICADOR SUONO BOWL VIDRIO 500W"),
        ]
        self.assertEqual(
            [row["productEvidenceKey"] for row in filter_family_candidates("bowl", rows)],
            ["p:direct"],
        )

    def test_v22_appliance_families_keep_accessories_food_and_wax_contexts_derived(self) -> None:
        cases = (
            ("p:coffee", "CAFETERA DE FILTRO ATMA 1.8L", "coffee_maker", "DIRECT"),
            ("p:air-fryer", "AIRFRYER ATMA DIGITAL 6.5L", "air_fryer", "DIRECT"),
            ("p:toaster", "TOSTADORA ATMA 2 REBANADAS", "toaster", "DIRECT"),
            ("p:toaster-accessory", "BOLSA ANTIADH P TOSTADORA X 2 UNI", "toaster", "DERIVED"),
            ("p:sandwich", "SANDWICHERA BLACK DECKER WM2000S", "sandwich_maker", "DIRECT"),
            ("p:sandwich-accessory", "BOLSA SEPARATA SANDWICHERA X12", "sandwich_maker", "DERIVED"),
            ("p:juicer", "EXPRIMIDOR ATMA 1LTS", "juicer", "DIRECT"),
            ("p:juicer-context", "JARRA MEDIDORA 1LT CON EXPRIMIDOR", "juicer", "DERIVED"),
            ("p:blender", "LICUADORA BLACK DECKER 500W", "blender", "DIRECT"),
            ("p:microwave", "MICROONDAS BGH 23L DIGITAL", "microwave", "DIRECT"),
            ("p:wax", "CERA DEPILATORIA MICROONDAS MILLEFIORI", "microwave", "DERIVED"),
            ("p:popcorn", "POCHOCLO PARA MICROONDAS MARCA X", "microwave", "DERIVED"),
            ("p:rack", "RACK PARA MICROONDAS MARCA X", "microwave", "DERIVED"),
        )
        for key, name, family, role in cases:
            with self.subTest(name=name):
                match = next(
                    item for item in derive_identity_record(_row(key, name))["familyCandidates"]
                    if item["family"] == family
                )
                self.assertEqual(match["role"], role)

        for query, expected in (
            ("cafetera", "coffee_maker"),
            ("coffee maker", "coffee_maker"),
            ("airfryer", "air_fryer"),
            ("air fryer", "air_fryer"),
            ("tostadora", "toaster"),
            ("toaster", "toaster"),
            ("sandwichera", "sandwich_maker"),
            ("sandwich maker", "sandwich_maker"),
            ("exprimidor", "juicer"),
            ("juicer", "juicer"),
            ("licuadora", "blender"),
            ("blender", "blender"),
            ("microondas", "microwave"),
            ("microwave", "microwave"),
        ):
            with self.subTest(query=query):
                self.assertEqual(family_query_key(query), expected)

    def test_form_qualified_family_wins_over_a_generic_word_family(self) -> None:
        self.assertEqual(family_query_key("alfajor chocolate"), "alfajor")
        self.assertEqual(family_query_form("alfajor chocolate"), ("alfajor", "chocolate"))
        self.assertEqual(family_query_form("pasta de mani"), ("peanut", "butter"))

    def test_v24_household_appliances_keep_direct_products_and_bilingual_queries(self) -> None:
        cases = (
            ("p:heater", "CALEFACTOR INFRARROJO LILIANA 1200W", "heater"),
            ("p:water-heater", "TERMOTANQUE ELECTRICO ESCORIAL 90L BLANCO", "water_heater"),
            ("p:fan", "VENTILADOR DE PIE LILIANA 20 DIGITAL", "fan"),
            ("p:vacuum", "ASPIRADORA ROBOT ELECTROLUX LASER ERB62", "vacuum_cleaner"),
        )
        for key, name, family in cases:
            with self.subTest(name=name):
                candidates = derive_identity_record(_row(key, name))["familyCandidates"]
                match = next(item for item in candidates if item["family"] == family)
                self.assertEqual(match["role"], "DIRECT")
                self.assertEqual(match["observedForms"], ["unspecified"])

        for name in (
            "AFA LATA FAN",
            "REXONA JAB FUT FAN 1",
            "PERF.SUENOS DE FAN 10 X100ML",
            "Deo Rexona Ap Aero Men Football Fan 89Gr",
        ):
            with self.subTest(name=name):
                match = next(
                    item for item in derive_identity_record(_row("p:fan-context", name))["familyCandidates"]
                    if item["family"] == "fan"
                )
                self.assertEqual(match["role"], "DERIVED")

        for query, expected in (
            ("calefactor", "heater"),
            ("heater", "heater"),
            ("space heater", "heater"),
            ("termotanque", "water_heater"),
            ("water heater", "water_heater"),
            ("ventilador", "fan"),
            ("fan", "fan"),
            ("aspiradora", "vacuum_cleaner"),
            ("vacuum cleaner", "vacuum_cleaner"),
            ("vacuum", "vacuum_cleaner"),
        ):
            with self.subTest(query=query):
                self.assertEqual(family_query_key(query), expected)

    def test_v25_cleaning_tool_families_keep_direct_products_and_exclude_observed_context(self) -> None:
        direct_cases = (
            ("p:broom", "ESCOBILLON 30CM", "broom"),
            ("p:escoba", "ESCOBA MULTIUSO VIRULANA 1 U", "broom"),
            ("p:mop", "MOPA MICROFIBRA VILEDA X1", "mop"),
            ("p:duster", "PLUMERO MICROFIBRA", "duster"),
        )
        for key, name, family in direct_cases:
            with self.subTest(name=name):
                match = next(
                    item for item in derive_identity_record(_row(key, name))["familyCandidates"]
                    if item["family"] == family
                )
                self.assertEqual(match["role"], "DIRECT")

        for key, name, family in (
            ("p:broom-accessory", "KIT GANCHOS ESCOBA FLOW BEIGE", "broom"),
            ("p:duster-context", "GUANTE NOVICA PLUMERO DE MICROFIBRA", "duster"),
        ):
            with self.subTest(name=name):
                match = next(
                    item for item in derive_identity_record(_row(key, name))["familyCandidates"]
                    if item["family"] == family
                )
                self.assertEqual(match["role"], "DERIVED")

        for query, expected in (
            ("escoba", "broom"),
            ("escobillon", "broom"),
            ("broom", "broom"),
            ("floor broom", "broom"),
            ("mopa", "mop"),
            ("mop", "mop"),
            ("plumero", "duster"),
            ("duster", "duster"),
            ("feather duster", "duster"),
        ):
            with self.subTest(query=query):
                self.assertEqual(family_query_key(query), expected)

    def test_v26_cleaning_cloth_forms_are_bilingual_and_context_safe(self) -> None:
        direct_cases = (
            ("p:cloth-floor", "PANO DE PISO CARREFOUR ESSENTIAL NIDO DE ABEJA ESP", "floor"),
            ("p:cloth-microfiber", "PANO MICROFIBRA VIRULANA EXPERT VIDRIOS", "microfiber"),
            ("p:cloth-kitchen", "PAÑO DE COCINA ECOLOGICO MISTER TRAPO X1", "kitchen"),
        )
        for key, name, expected_form in direct_cases:
            with self.subTest(name=name):
                match = next(
                    item for item in derive_identity_record(_row(key, name))["familyCandidates"]
                    if item["family"] == "cleaning_cloth"
                )
                self.assertEqual(match["role"], "DIRECT")
                self.assertIn(expected_form, match["observedForms"])

        for key, name in (
            ("p:cloth-curtain", "CORTINAS FIELDERST COCINA ESTAMP. 2PAÑO"),
            ("p:cloth-felt", "FIELTRO AUTOADHESIVO CARREFOUR PANO 80X80MM"),
            ("p:cloth-bags", "BOLSAS DE RESIDUOS 50X70 MISTER TRAPO PAQ 10 UNI"),
        ):
            with self.subTest(name=name):
                match = next(
                    item for item in derive_identity_record(_row(key, name))["familyCandidates"]
                    if item["family"] == "cleaning_cloth"
                )
                self.assertEqual(match["role"], "DERIVED")

        for query, expected in (
            ("pano", "cleaning_cloth"),
            ("paño", "cleaning_cloth"),
            ("trapo", "cleaning_cloth"),
            ("cleaning cloth", "cleaning_cloth"),
            ("cloth", "cleaning_cloth"),
            ("rag", "cleaning_cloth"),
        ):
            with self.subTest(query=query):
                self.assertEqual(family_query_key(query), expected)

        for query, expected in (
            ("pano de piso", ("cleaning_cloth", "floor")),
            ("cleaning cloth microfiber", ("cleaning_cloth", "microfiber")),
            ("pano de cocina", ("cleaning_cloth", "kitchen")),
        ):
            with self.subTest(query=query):
                self.assertEqual(family_query_form(query), expected)

    def test_mango_family_is_available_for_observed_forms(self) -> None:
        value = derive_identity_record(_row("p:mango-cookie", "GALLETAS DE MANGO 200 G"))
        mango = next(item for item in value["familyCandidates"] if item["family"] == "mango")
        self.assertEqual(mango["role"], "DERIVED")
        self.assertNotIn(
            ("mango", "en", "CURATED_BILINGUAL"),
            {(item["value"], item["language"], item["provenance"]) for item in value["aliases"]},
        )

    def test_derived_rice_form_does_not_get_direct_rice_alias(self) -> None:
        value = derive_identity_record(_row("p:cake", "GALLETITAS DE ARROZ DULCES 100 G"))
        candidate = next(item for item in value["familyCandidates"] if item["family"] == "rice")
        self.assertEqual(candidate["family"], "rice")
        self.assertEqual(candidate["role"], "DERIVED")
        self.assertIn("dessert", candidate["observedForms"])
        self.assertNotIn(("rice", "en", "CURATED_BILINGUAL"), {(item["value"], item["language"], item["provenance"]) for item in value["aliases"]})

    def test_carrot_forms_separate_food_drink_dessert_and_cosmetics(self) -> None:
        cases = (
            ("p:vegetable", "BABY CARROT", "DIRECT", "vegetable"),
            ("p:drink", "DISC:GRE JUICE MARKET HAPPY CARROT 500M", "DERIVED", "drink"),
            ("p:dessert", "POSTRE CARROT CAKE RIDERS X 140 GR", "DERIVED", "dessert"),
            ("p:cosmetics", "LOCION PROT. SOLAR FPS 30 CARROT HAWAIIAN BOT 240 ML", "DERIVED", "cosmetics"),
        )
        for key, name, role, form in cases:
            with self.subTest(name=name):
                value = derive_identity_record(_row(key, name))
                candidate = value["familyCandidates"]
                carrot = next(item for item in candidate if item["family"] == "carrot")
                self.assertEqual(carrot["role"], role)
                self.assertEqual(carrot["observedForms"], [form])
                if form == "drink":
                    self.assertIn(
                        ("juice", "DIRECT"),
                        {(item["family"], item["role"]) for item in candidate},
                    )
                elif form == "cosmetics":
                    self.assertIn(
                        ("sunscreen", "DIRECT"),
                        {(item["family"], item["role"]) for item in candidate},
                    )
                else:
                    self.assertEqual(len(candidate), 1)

        dessert = derive_identity_record(_row("p:dessert", "TORTA DE ZANAHORIA 400 G"))
        aliases = {(item["value"], item["language"], item["provenance"]) for item in dessert["aliases"]}
        self.assertIn(("carrot cake", "en", "CURATED_FORM"), aliases)
        self.assertIn(("postre de zanahoria", "es", "CURATED_FORM"), aliases)

    def test_carrot_clarification_exposes_observed_forms(self) -> None:
        candidates = [
            _row("p:vegetable", "BABY CARROT"),
            _row("p:drink", "DISC:GRE JUICE MARKET HAPPY CARROT 500M"),
            _row("p:dessert", "POSTRE CARROT CAKE RIDERS X 140 GR"),
            _row("p:cosmetics", "LOCION PROT. SOLAR FPS 30 CARROT HAWAIIAN BOT 240 ML"),
        ]
        value = build_family_clarification("carrot", candidates)
        self.assertIsNotNone(value)
        assert value is not None
        self.assertEqual(value["family"], "carrot")
        choices = {choice["id"]: choice for choice in value["choices"]}
        self.assertEqual(
            set(choices),
            {"carrot:vegetable", "carrot:drink", "carrot:dessert", "carrot:cosmetics"},
        )
        self.assertEqual(choices["carrot:vegetable"]["labelEn"], "Carrot vegetable")
        self.assertEqual(choices["carrot:drink"]["query"], "carrot juice")

    def test_egg_family_separates_food_from_toys_and_exposes_clarification(self) -> None:
        candidates = [
            _row("p:egg", "HUEVOS FRESCOS X12"),
            _row("p:liquid", "HUEVOS LIQUIDOS 500 ML"),
            _row("p:dino", "SMASH DINO NANO EGG"),
            _row("p:chocolate", "HUEVO CHOCOLATE SORPRESA"),
        ]
        direct = derive_identity_record(candidates[0])
        self.assertEqual(direct["familyCandidates"], [{
            "family": "egg",
            "role": "DIRECT",
            "matchedTerms": ["huevos"],
            "observedForms": ["whole_egg"],
        }])
        toy = derive_identity_record(candidates[2])
        self.assertEqual(toy["familyCandidates"][0]["role"], "DERIVED")
        self.assertIn("toy", toy["familyCandidates"][0]["observedForms"])

        value = build_family_clarification("eggs", candidates)
        self.assertIsNotNone(value)
        assert value is not None
        self.assertEqual(value["family"], "egg")
        self.assertEqual(
            {choice["id"] for choice in value["choices"]},
            {"egg:whole_egg", "egg:liquid", "egg:toy", "egg:chocolate"},
        )
        self.assertEqual(
            next(choice for choice in value["choices"] if choice["id"] == "egg:whole_egg")["query"],
            "huevo de gallina",
        )
        self.assertEqual(
            filter_family_candidates("eggs liquid", [
                _row("p:cleaner", "LIMPIADOR LIQUIDO 500 ML"),
                _row("p:egg", "HUEVOS FRESCOS X12"),
            ]),
            (),
        )
        self.assertEqual(family_selection_form("huevos", "egg:whole_egg"), ("egg", "whole_egg"))
        selected_shell_eggs = filter_family_candidates(
            "huevos",
            [
                _row("p:white", "HUEVO BLANCO DON REYNALDO X 12 UNI"),
                _row("p:liquid", "HUEVOS LIQUIDOS 500 ML"),
            ],
            selection_id="egg:whole_egg",
        )
        self.assertEqual([row["productEvidenceKey"] for row in selected_shell_eggs], ["p:white"])
        shell_eggs = filter_family_candidates(
            "huevo",
            [
                _row("p:white", "HUEVO BLANCO DON REYNALDO X 12 UNI"),
                _row("p:toy", "SMASH DINO NANO EGG"),
                _row("p:chocolate", "HUEVO CHOCOLATE SORPRESA"),
            ],
        )
        self.assertEqual([row["productEvidenceKey"] for row in shell_eggs], ["p:white"])

    def test_rice_bar_is_a_derived_form(self) -> None:
        value = derive_identity_record(_row("p:bar", "BARRA DE ARROZ DULCE 12 G"))
        candidate = value["familyCandidates"][0]
        self.assertEqual(candidate["role"], "DERIVED")
        self.assertIn("dessert", candidate["observedForms"])

    def test_bare_reviewed_family_keeps_direct_products_out_of_derived_rows(self) -> None:
        values = filter_family_candidates(
            "rice",
            [
                _row("p:grain", "ARROZ LARGO FINO 1 KG"),
                _row("p:bar", "BARRA DE ARROZ DULCE 12 G"),
            ],
        )
        self.assertEqual([row["productEvidenceKey"] for row in values], ["p:grain"])

        carrot_values = filter_family_candidates(
            "carrot",
            [
                _row("p:vegetable", "BABY CARROT"),
                _row("p:lotion", "LOCION CARROT HAWAIIAN 240 ML"),
            ],
        )
        self.assertEqual([row["productEvidenceKey"] for row in carrot_values], ["p:vegetable"])

    def test_explicit_form_query_does_not_fail_open_to_unrelated_rows(self) -> None:
        values = filter_family_candidates(
            "lychee drink",
            [
                _row("p:apple-juice", "JUGO DE MANZANA 500 CC"),
                _row("p:soap", "JABON LIQUIDO SANDIA Y LICHI 250 ML"),
            ],
        )
        self.assertEqual(values, ())

    def test_bare_reviewed_family_without_direct_item_does_not_show_derived_rows(self) -> None:
        values = filter_family_candidates(
            "lychee",
            [
                _row("p:soap", "JABON LIQUIDO SANDIA Y LICHI 250 ML"),
                _row("p:colorissimo", "COLORISSIMO LYCHEE FRESCO", brand="VOGUE"),
            ],
        )
        self.assertEqual(values, ())

    def test_meat_family_rules_reject_pet_toy_and_alcohol_contexts(self) -> None:
        cases = (
            ("pork", "ASADO DE CERDO X KG", "DIRECT"),
            ("pork", "JUEGO DE MESA CHANCHO GOTON", "DERIVED"),
            ("pork", "JUGUETE CAN CAT CERDO GLADIADOR", "DERIVED"),
            ("lamb", "ASADO DE CORDERO X KG", "DIRECT"),
            ("lamb", "SNACK D/CORDERO P/PERRO MEAT BITES", "DERIVED"),
            ("turkey", "PECHUGA DE PAVO X KG", "DIRECT"),
            ("turkey", "W.WILD TURKEY X750ML BOURBON", "DERIVED"),
            ("beef", "CENTRO DE ENTRANA VACUNO X KG", "DIRECT"),
            ("beef", "MILANESA DE CARNE VACUNA X KG", "DERIVED"),
            ("beef", "ALIMENTO PERROS CARNE VACUNA 2 KG", "DERIVED"),
        )
        for family, name, role in cases:
            with self.subTest(family=family, name=name):
                candidates = derive_identity_record(_row(f"p:{family}", name))["familyCandidates"]
                match = next(item for item in candidates if item["family"] == family)
                self.assertEqual(match["role"], role)

        for query, rows in (
            ("pork", [_row("p:meat", "ASADO DE CERDO X KG"), _row("p:toy", "JUEGO DE MESA CHANCHO GOTON")]),
            ("lamb", [_row("p:meat", "ASADO DE CORDERO X KG"), _row("p:pet", "SNACK D/CORDERO P/PERRO MEAT BITES")]),
            ("turkey", [_row("p:meat", "PECHUGA DE PAVO X KG"), _row("p:wine", "W.WILD TURKEY X750ML BOURBON")]),
            ("beef", [_row("p:meat", "CENTRO DE ENTRANA VACUNO X KG"), _row("p:pet", "ALIMENTO PERROS CARNE VACUNA 2 KG")]),
        ):
            with self.subTest(query=query):
                self.assertEqual([row["productEvidenceKey"] for row in filter_family_candidates(query, rows)], ["p:meat"])

    def test_yerba_form_query_ignores_generic_family_words(self) -> None:
        self.assertEqual(family_query_form("te de yerba mate"), ("yerba_mate", "tea_bags"))
        self.assertEqual(family_query_form("bebida de yerba"), ("yerba_mate", "drink"))
        self.assertIsNone(family_query_form("yerba mate"))

    def test_lychee_in_soap_is_not_fresh_fruit(self) -> None:
        value = derive_identity_record(_row("p:soap", "JABON LIQUIDO SANDIA Y LICHI 250 ML"))
        candidate = value["familyCandidates"][0]
        self.assertEqual(candidate["role"], "DERIVED")
        self.assertNotIn(("lychee", "en", "CURATED_BILINGUAL"), {(item["value"], item["language"], item["provenance"]) for item in value["aliases"]})

    def test_lychee_cosmetic_with_fresco_does_not_match_fresh_choice(self) -> None:
        cosmetic = _row("p:colorissimo", "COLORISSIMO LYCHEE FRESCO", brand="VOGUE")
        value = derive_identity_record(cosmetic)
        candidate = value["familyCandidates"][0]
        self.assertEqual(candidate["role"], "DERIVED")
        self.assertIn("fresh", candidate["observedForms"])

        fruit = _row("p:fruit", "LICHI FRESCO X 500 G")
        filtered = filter_family_candidates("lychee fresh", [cosmetic, fruit])
        self.assertEqual([row["productEvidenceKey"] for row in filtered], ["p:fruit"])

    def test_multiple_families_are_retained_for_clarification(self) -> None:
        value = derive_identity_record(_row("p:drink", "BEBIDA DE YERBA CON ARROZ"))
        self.assertEqual(value["classification"], "MULTIPLE_FAMILIES")
        families = {item["family"] for item in value["familyCandidates"]}
        self.assertEqual(families, {"rice", "yerba_mate"})
        self.assertIn("DERIVED", {item["role"] for item in value["familyCandidates"]})

    def test_stationery_brand_is_not_yerba_mate(self) -> None:
        value = derive_identity_record(_row("p:pen", "BOLIGRAFO PAPER MATE AZUL X1"))
        self.assertEqual(value["classification"], "UNCLASSIFIED")
        self.assertFalse(value["familyCandidates"])

    def test_family_clarification_uses_only_observed_rice_forms(self) -> None:
        candidates = [
            _row("p:grain", "ARROZ LARGO FINCA 500 G"),
            _row("p:cake", "GALLETITAS DE ARROZ DULCES 100 G"),
            _row("p:flour", "HARINA DE ARROZ 500 G"),
        ]
        value = build_family_clarification("rice", candidates)
        self.assertIsNotNone(value)
        assert value is not None
        self.assertEqual(value["code"], "PRODUCT_FAMILY_CLARIFICATION")
        self.assertEqual(value["family"], "rice")
        choices = {choice["id"]: choice for choice in value["choices"]}
        self.assertEqual(set(choices), {"rice:grain", "rice:dessert", "rice:flour"})
        self.assertEqual(choices["rice:grain"]["query"], "arroz largo")
        self.assertEqual(choices["rice:flour"]["labelEn"], "Rice flour")

    def test_family_clarification_is_absent_without_two_observed_forms(self) -> None:
        value = build_family_clarification("arroz", [_row("p:grain", "ARROZ LARGO FINCA 500 G")])
        self.assertIsNone(value)

    def test_family_clarification_does_not_invent_generic_family_forms(self) -> None:
        value = build_family_clarification("manzana", [_row("p:apple", "MANZANA ROJA 1 KG")])
        self.assertIsNone(value)

    def test_build_is_deterministic_and_writes_manifest(self) -> None:
        rows = [
            _row("p:rice", "ARROZ LARGO FINCA 500 G"),
            _row("p:cake", "GALLETITAS DE ARROZ DULCES 100 G"),
            _row("p:mate", "YERBA MATE SUAVE 1 KG"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "products.jsonl.gz"
            with gzip.open(source, "wt", encoding="utf-8", newline="") as handle:
                for row in rows:
                    handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
            output = root / "identity.jsonl.gz"
            manifest_path = root / "manifest.json"
            manifest = build_identity_lexicon(source, output, manifest_path)
            self.assertEqual(manifest["schemaVersion"], SCHEMA_VERSION)
            self.assertEqual(manifest["policyVersion"], POLICY_VERSION)
            self.assertEqual(manifest["identityCounts"]["records"], 3)
            self.assertEqual(manifest["source"]["recordCount"], 3)
            self.assertTrue(manifest["output"]["sha256"])
            with gzip.open(output, "rt", encoding="utf-8") as handle:
                written = [json.loads(line) for line in handle if line.strip()]
            self.assertEqual(len(written), 3)
            self.assertEqual({item["productEvidenceKey"] for item in written}, {"p:rice", "p:cake", "p:mate"})
            self.assertEqual(json.loads(manifest_path.read_text(encoding="utf-8"))["families"]["rice"]["derivedCount"], 1)

    def test_new_source_rows_are_classified_on_each_build(self) -> None:
        row = _row("p:new-carrot", "ZANAHORIA FRESCA 1 KG")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "products.jsonl"
            source.write_text(json.dumps(row, ensure_ascii=False) + "\n", encoding="utf-8")
            output = root / "identity.jsonl.gz"
            manifest = build_identity_lexicon(source, output)
            self.assertEqual(manifest["source"]["recordCount"], 1)
            self.assertEqual(manifest["identityCounts"]["classifications"], {"DIRECT": 1})
            self.assertEqual(manifest["families"]["carrot"]["directCount"], 1)
            with gzip.open(output, "rt", encoding="utf-8") as handle:
                identity = json.loads(handle.readline())
            self.assertEqual(identity["familyCandidates"][0]["observedForms"], ["vegetable"])

    def test_duplicate_keys_fail_closed(self) -> None:
        rows = [_row("p:same", "ARROZ"), _row("p:same", "ARROZ INTEGRAL")]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "products.jsonl"
            source.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "duplicate productEvidenceKey"):
                build_identity_lexicon(source, root / "identity.jsonl.gz")


if __name__ == "__main__":
    unittest.main()
