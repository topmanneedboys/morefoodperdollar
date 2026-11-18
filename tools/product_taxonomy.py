#!/usr/bin/env python3
"""Build a conservative, auditable taxonomy for the Argentina catalogue.

The provider source contains product names and retailer offers, not a trusted
department taxonomy.  This module therefore derives only bounded labels from
reviewed Spanish/English phrases.  It keeps the customer-facing choices small
and useful while preserving a review state for names that do not contain
enough evidence.

The taxonomy has three independent layers:

* ``departmentCandidates`` — broad shopping areas such as beverages or
  personal care;
* ``familyCandidates`` — the reviewed product-family records from
  ``product_identity_lexicon``;
* ``formCandidates`` — observed presentations such as grain, liquid, biscuit,
  or cosmetic.

This is deliberately not a one-category-per-product system.  A product can
carry several evidence-backed labels, while every provider product receives a
deterministic ``coverageStatus``.  Prices, offers, stock, availability, and
credentials are never read or written by this module.
"""

from __future__ import annotations

import gzip
import hashlib
import json
import re
from collections import Counter, defaultdict
from dataclasses import dataclass, replace
from functools import lru_cache
from pathlib import Path
from typing import Any, Iterable, Iterator, Mapping, Sequence

from tools.consumer_input_intelligence import normalize_text


SCHEMA_VERSION = "valuepilot-argentina-product-taxonomy-v1"
POLICY_VERSION = "valuepilot-argentina-product-taxonomy-policy-v1"
MAX_NAME_LENGTH = 512
MAX_NAMES_PER_PROVIDER = 32
MAX_IDENTITIES_PER_PROVIDER = 512
MAX_MATCHED_TERMS = 12
MAX_FORMS = 12
MAX_CLARIFICATION_CHOICES = 30
MAX_INDEX_RECORDS = 100_000
MAX_INDEX_BYTES = 64 * 1024 * 1024


@dataclass(frozen=True)
class TaxonomyRule:
    key: str
    label_es: str
    label_en: str
    terms: tuple[str, ...]
    excluded_terms: tuple[str, ...] = ()
    priority: int = 50


class ProductTaxonomyIndex:
    """Bounded, identity-only lookup for a built provider taxonomy.

    The index is an optional sidecar to an already qualified release.  It is
    never required for Search to run: callers can continue using the reviewed
    lexical rules when the sidecar is absent.  ``annotate`` adds only an
    internal ``_taxonomy`` field so the backend can apply the same observed
    form choices without exposing the sidecar as a new public contract.
    """

    def __init__(self, records: Mapping[str, Mapping[str, Any]], *, path: Path | None = None):
        self._records = dict(records)
        self.path = path

    @classmethod
    def load(
        cls,
        path: Path | str,
        *,
        expected_sha256: str | None = None,
        max_bytes: int = MAX_INDEX_BYTES,
        max_records: int = MAX_INDEX_RECORDS,
    ) -> "ProductTaxonomyIndex":
        source = Path(path).resolve()
        if not source.is_file():
            raise ValueError(f"taxonomy index does not exist: {source}")
        size = source.stat().st_size
        if size > max_bytes:
            raise ValueError("taxonomy index is too large")
        if expected_sha256 is not None:
            expected = str(expected_sha256).strip().lower()
            if not re.fullmatch(r"[0-9a-f]{64}", expected):
                raise ValueError("taxonomy index checksum is invalid")
            if _sha256_file(source) != expected:
                raise ValueError("taxonomy index checksum mismatch")

        records: dict[str, Mapping[str, Any]] = {}
        provider_count = 0
        for line_number, value in _iter_jsonl(source):
            if provider_count >= max_records:
                raise ValueError("taxonomy index record limit exceeded")
            provider_id = value.get("providerProductId")
            taxonomy = value.get("taxonomy")
            if not isinstance(provider_id, str) or not provider_id.strip():
                raise ValueError(f"taxonomy index line {line_number} has no providerProductId")
            if not isinstance(taxonomy, Mapping):
                raise ValueError(f"taxonomy index line {line_number} has no taxonomy")
            record = {
                "providerProductId": provider_id.strip(),
                "taxonomy": dict(taxonomy),
            }
            provider_count += 1
            records[f"provider:{provider_id.strip()}"] = record
            identity_keys = value.get("identityKeys")
            if isinstance(identity_keys, Sequence) and not isinstance(identity_keys, (str, bytes)):
                for identity_key in identity_keys:
                    if isinstance(identity_key, str) and identity_key.strip():
                        records[f"identity:{identity_key.strip()}"] = record
        return cls(records, path=source)

    @property
    def record_count(self) -> int:
        return len({id(value) for value in self._records.values()})

    def taxonomy_for(self, candidate: Mapping[str, Any]) -> Mapping[str, Any] | None:
        provider_id = candidate.get("providerProductId")
        if isinstance(provider_id, str) and provider_id.strip():
            value = self._records.get(f"provider:{provider_id.strip()}")
            if isinstance(value, Mapping):
                taxonomy = value.get("taxonomy")
                if isinstance(taxonomy, Mapping):
                    return taxonomy
        for field in ("productEvidenceKey", "identityKey"):
            identity_key = candidate.get(field)
            if isinstance(identity_key, str) and identity_key.strip():
                value = self._records.get(f"identity:{identity_key.strip()}")
                if isinstance(value, Mapping):
                    taxonomy = value.get("taxonomy")
                    if isinstance(taxonomy, Mapping):
                        return taxonomy
        return None

    def annotate(self, candidates: Iterable[Mapping[str, Any]]) -> tuple[Mapping[str, Any], ...]:
        annotated: list[Mapping[str, Any]] = []
        for candidate in candidates:
            if not isinstance(candidate, Mapping):
                annotated.append(candidate)
                continue
            taxonomy = self.taxonomy_for(candidate)
            if taxonomy is None:
                annotated.append(candidate)
                continue
            value = dict(candidate)
            value["_taxonomy"] = dict(taxonomy)
            annotated.append(value)
        return tuple(annotated)


# These rules are intentionally broad enough to cover the catalogue's main
# shopping departments, but every match remains source-name evidence.  A new
# rule belongs here only after a review of real source examples and a test.
DEPARTMENT_RULES: tuple[TaxonomyRule, ...] = (
    TaxonomyRule(
        "fresh_produce", "Frutas y verduras", "Fresh produce",
        ("fruta", "frutas", "fruit", "fruits", "manzana", "apple", "banana", "bananas", "platano", "banana", "pera", "pear", "uva", "grape", "limon", "lemon", "naranja", "orange", "mandarina", "tangerine", "pomelo", "grapefruit", "mango", "mangos", "kiwi", "anana", "pina", "pineapple", "durazno", "peach", "ciruela", "plum", "frutilla", "strawberry", "frambuesa", "raspberry", "arandano", "blueberry", "palta", "aguacate", "avocado", "tomate", "tomato", "zanahoria", "zanahorias", "carrot", "papa", "papas", "potato", "cebolla", "onion", "ajo", "garlic", "lechuga", "lettuce", "zapallo", "calabaza", "pumpkin", "pepino", "cucumber", "apio", "celery", "morron", "pimiento", "pepper", "remolacha", "beet", "brocoli", "broccoli", "espinaca", "spinach"),
        ("jugo", "zumo", "juice", "bebida", "drink", "sabor", "flavor", "flavour", "aroma", "cosmetico", "cosmetics", "locion", "lotion", "crema", "cream", "postre", "dessert", "torta", "cake"),
        90,
    ),
    TaxonomyRule(
        "beverages", "Bebidas", "Beverages",
        ("agua", "water", "gaseosa", "gaseosas", "soda", "soft drink", "refresco", "bebida", "bebidas", "drink", "drinks", "jugo", "jugos", "zumo", "juice", "nectar", "nectar", "vino", "wines", "wine", "malbec", "cabernet", "chardonnay", "espumante", "champagne", "champan", "cerveza", "beer", "sidra", "cider", "whisky", "whiskey", "vodka", "gin", "ron", "rum", "fernet", "licor", "liquor", "energizante", "energy drink", "cafe", "coffee", "te", "tea", "yerba", "yerba mate", "mate cocido"),
        ("sabor", "flavor", "flavour", "aroma"),
        85,
    ),
    TaxonomyRule(
        "dairy", "Lácteos", "Dairy",
        ("leche", "milk", "queso", "cheese", "yogur", "yogurt", "manteca", "butter", "crema de leche", "dulce de leche", "cream cheese", "ricota", "ricotta"),
        ("corporal", "body", "locion", "lotion", "cosmetico", "cosmetics", "sabor", "flavor", "flavour"),
        82,
    ),
    TaxonomyRule(
        "meat_seafood", "Carnes y pescados", "Meat and seafood",
        ("carne", "beef", "vaca", "vacuno", "cerdo", "pork", "pollo", "chicken", "pavo", "turkey", "pescado", "fish", "salmon", "salmon", "atun", "tuna", "merluza", "hake", "camaron", "shrimp", "langostino", "jamon", "ham", "salame", "salami", "chorizo", "bondiola", "bacon", "panceta", "costilla", "milanesa", "asado", "roast", "bife", "steak", "vacio", "novillito", "leberwurst", "salchicha", "frankfurt", "morcilla", "blood sausage"),
        ("sabor", "flavor", "flavour", "caldo", "broth", "mascota", "pet"),
        80,
    ),
    TaxonomyRule(
        "staples", "Almacén y básicos", "Staples and dry goods",
        ("arroz", "rice", "harina", "flour", "fideos", "fideo", "pasta", "noodle", "noodles", "avena", "oat", "oats", "cereal", "cereales", "quinoa", "polenta", "maiz", "corn", "lenteja", "lentils", "poroto", "porotos", "bean", "beans", "garbanzo", "chickpea", "huevo", "huevos", "egg", "eggs", "pan", "bread", "tostada", "toast", "galletita", "galletitas", "galleta", "galletas", "biscuit", "biscuits", "cracker", "crackers"),
        ("sabor", "flavor", "flavour", "cosmetico", "cosmetics"),
        76,
    ),
    TaxonomyRule(
        "snacks_sweets", "Snacks y dulces", "Snacks and sweets",
        ("galletita", "galletitas", "galleta", "galletas", "cookie", "cookies", "biscuit", "biscuits", "cracker", "crackers", "alfajor", "alfajores", "chocolate", "chocolates", "caramelo", "caramel", "golosina", "golosinas", "candy", "turron", "nougat", "helado", "helados", "ice cream", "postre", "postres", "dessert", "dulce", "dulces", "bombon", "bonbon", "chicle", "gum", "papas fritas", "chips", "mani", "peanut", "snack", "snacks"),
        ("sabor", "flavor", "flavour", "aroma"),
        78,
    ),
    TaxonomyRule(
        "condiments", "Aceites, sal y condimentos", "Oils, salt and condiments",
        ("aceite", "oil", "sal", "salt", "azucar", "sugar", "vinagre", "vinegar", "salsa", "sauce", "mayonesa", "mayonnaise", "ketchup", "mostaza", "mustard", "condimento", "seasoning", "especia", "spice", "pimienta", "pepper", "miel", "honey", "mermelada", "jam", "dulce de leche"),
        ("sabor", "flavor", "flavour", "cosmetico", "cosmetics", "masaje", "massage"),
        73,
    ),
    TaxonomyRule(
        "household_cleaning", "Limpieza y hogar", "Household and cleaning",
        ("detergente", "detergents", "limpiador", "limpiadores", "cleaner", "cleaning", "limpieza", "lavandina", "bleach", "desinfectante", "disinfectant", "suavizante", "fabric softener", "jabon para ropa", "jabon liquido", "jabon liq", "jabon lavar", "laundry", "esponja", "sponge", "trapo", "pano", "cloth", "escoba", "broom", "escobillon", "mopa", "mop", "plumero", "duster", "aromatizante", "air freshener", "aromatizador", "papel higienico", "toilet paper", "servilleta", "napkin", "toalla de papel", "paper towel", "rollo", "bolsa de basura", "trash bag", "basura", "basurero"),
        ("sabor", "flavor", "flavour", "cosmetico", "cosmetics"),
        68,
    ),
    TaxonomyRule(
        "personal_care", "Cuidado personal", "Personal care",
        ("shampoo", "champu", "acondicionador", "acond", "conditioner", "desodorante", "deodorant", "antitranspirante", "antiperspirant", "perfume", "colonia", "cologne", "crema facial", "face cream", "crema manos", "hand cream", "crema corporal", "body cream", "crema", "locion", "lotion", "protector solar", "sunscreen", "cosmetico", "cosmetics", "maquillaje", "makeup", "jabon de tocador", "jabon tocador", "toilet soap", "bath soap", "cepillo dental", "toothbrush", "pasta dental", "toothpaste", "dental", "higiene bucal", "mouthwash", "enjuague bucal", "coloracion", "hair color", "tinte", "gel cabello", "hair gel", "quitaesmalte", "nail polish remover", "labial", "lipstick", "base maquillaje", "mascara de pestañas", "mascara pestanas", "rubor", "blush", "afeitadora", "shaver", "depiladora", "wax"),
        ("alimento", "food", "bebida", "drink", "sabor", "flavor", "flavour", "crema de leche", "crema pastelera", "crema de cacao"),
        67,
    ),
    TaxonomyRule(
        "baby_pet", "Bebés y mascotas", "Baby and pet",
        ("pañal", "panal", "diaper", "toallita bebe", "baby wipes", "formula infantil", "infant formula", "alimento bebe", "baby food", "bebe", "baby", "perro", "dog", "gato", "cat", "mascota", "pet", "alimento perro", "dog food", "alimento gato", "cat food", "arena sanitaria", "cat litter", "correa", "leash"),
        ("sabor", "flavor", "flavour"),
        66,
    ),
    TaxonomyRule(
        "health_supplements", "Salud y suplementos", "Health and supplements",
        ("vitamina", "vitamin", "suplemento", "supplement", "extracto", "extract", "capsula", "capsule", "medicamento", "medicine", "analgesico", "painkiller", "farmacia", "pharmacy", "proteina", "protein", "colageno", "collagen"),
        ("sabor", "flavor", "flavour", "mascota", "pet"),
        65,
    ),
    TaxonomyRule(
        "clothing", "Ropa y calzado", "Clothing and footwear",
        ("remera", "rem", "tshirt", "camiseta", "shirt", "pantalon", "pant", "pants", "jean", "jeans", "buzo", "sweatshirt", "short", "shorts", "bermuda", "vestido", "dress", "camisa", "blusa", "blouse", "ropa", "clothing", "calzado", "footwear", "zapatilla", "sneaker", "zapato", "shoe", "sandalia", "sandal", "medias", "socks", "campera", "jacket", "abrigo", "coat", "gorra", "cap", "sombrero", "hat", "pijama", "pyjamas", "boxer", "calza", "legging", "chaleco", "vest", "bata", "robe", "sweater", "jogger", "body", "camison", "camisón", "pollera", "skirt", "musculosa", "tank top"),
        (),
        60,
    ),
    TaxonomyRule(
        "electronics", "Electrónica", "Electronics",
        ("celular", "cellphone", "cel", "phone", "smartphone", "tablet", "notebook", "laptop", "computadora", "computer", "televisor", "television", "tv", "led", "monitor", "cargador", "charger", "auricular", "headphone", "parlante", "speaker", "torre de sonido", "sound tower", "cable usb", "usb cable", "memoria", "memory card", "micro sd", "impresora", "printer", "heladera", "refrigerator", "freezer", "horno elect", "horno electrico", "electric oven", "microondas", "microwave", "aire acondicionado", "air conditioner", "aire split", "lavarropas", "washing machine", "freidora", "air fryer", "batidora", "mixer", "licuadora", "blender", "tostadora", "toaster", "pava electrica", "electric kettle", "aspiradora", "vacuum"),
        ("sabor", "flavor", "flavour", "alimento", "food"),
        58,
    ),
    TaxonomyRule(
        "home_goods", "Hogar y cocina", "Home and kitchen",
        ("bandeja", "tray", "fuente", "serving dish", "plato", "platos", "dish", "vajilla", "dishware", "olla", "pot", "cacerola", "sarten", "sartenes", "frying pan", "bowl", "tazon", "vaso", "glass", "mug", "taza", "jarra", "pitcher", "frasco", "jar", "botella", "bottle", "hermetico", "container", "organizador", "organizer", "cesto", "basket", "canasto", "cajon", "drawer", "silla", "chair", "mesa", "table", "almohadon", "cushion", "toalla", "towel", "toallon", "cortina", "curtain", "alfombra", "rug", "mantel", "tablecloth", "sabana", "sheets", "frazada", "blanket", "secaplatos", "dish rack", "escurridor", "colador", "strainer", "perchero", "hanger", "lampara", "lamp", "vela", "candle", "difusor", "diffuser", "sahumerio", "incienso", "incense", "madera", "wood", "plastico", "plastic", "acero inoxidable", "stainless steel"),
        ("sabor", "flavor", "flavour", "alimento", "food"),
        62,
    ),
    TaxonomyRule(
        "toys_games", "Juguetes y juegos", "Toys and games",
        ("juego", "juegos", "game", "games", "juguete", "juguetes", "toy", "toys", "muneca", "muñeca", "doll", "lego", "playset", "pelota", "ball", "dardo", "darts", "arco y flecha", "bow and arrow", "rompecabezas", "puzzle", "peluche", "plush", "figura", "figure", "disfraz", "costume", "frisbee", "patin", "skate", "auto a control remoto", "remote control car", "vehiculo a control remoto", "play doh"),
        (),
        61,
    ),
    TaxonomyRule(
        "stationery", "Librería y papelería", "Stationery and school supplies",
        ("lapiz", "lapices", "pencil", "pencils", "boligrafo", "boligrafos", "ballpen", "marcador", "marker", "fibra", "cuaderno", "agenda", "goma de borrar", "eraser", "regla", "ruler", "tijera", "scissors", "calculadora", "calculator", "resaltador", "highlighter", "carpeta", "folder", "cartucho", "cartridge", "toner", "tinta", "ink", "papel", "paper", "escolar", "school", "lapicera", "pen"),
        (),
        60,
    ),
    TaxonomyRule(
        "tools_hardware", "Herramientas y ferretería", "Tools and hardware",
        ("taladro", "drill", "herramienta", "tool", "destornillador", "screwdriver", "martillo", "hammer", "sierra", "saw", "cinta adhesiva", "adhesive tape", "silicona", "silicone", "latex", "pintura", "paint", "pincel", "brush", "tornillo", "screw", "tarugo", "pegamento", "glue", "adhesivo", "adhesive", "repuesto", "replacement", "ferreteria", "hardware", "guante de trabajo", "work glove", "acero", "steel", "metal", "madera", "wood"),
        (),
        59,
    ),
    TaxonomyRule(
        "garden_outdoor", "Jardín y exterior", "Garden and outdoor",
        ("maceta", "flower pot", "jardin", "garden", "jardineria", "gardening", "semilla", "seed", "tierra", "soil", "manguera", "hose", "pileta", "pool", "parrilla", "grill", "asador", "barbecue", "barbacoa", "carpa", "tent", "camping", "pesca", "fishing", "hamaca", "hammock"),
        (),
        57,
    ),
    TaxonomyRule(
        "sports_luggage", "Deportes y equipaje", "Sports and luggage",
        ("mochila", "backpack", "valija", "suitcase", "bolso", "duffel", "riñonera", "rinonera", "waist bag", "deporte", "sports", "fitness", "yoga", "bicicleta", "bicycle", "casco", "helmet", "raqueta", "racket", "pesas", "weights", "pelota de futbol", "soccer ball"),
        (),
        56,
    ),
    TaxonomyRule(
        "prepared_food", "Comidas preparadas", "Prepared foods",
        ("pizza", "hamburguesa", "hamburger", "empanada", "sopa", "soup", "sushi", "sandwich", "fiambre", "cold cuts", "salchichon", "mortadela", "provoleta", "aceituna", "aceitunas", "olives", "almendra", "almendras", "almonds", "nuez", "nueces", "nuts", "muffin", "budin", "torta", "panificado", "prepared meal"),
        ("sabor", "flavor", "flavour"),
        72,
    ),
)

# Conservative additions derived from the current review queue.  These are
# kept separate from the original rules so each batch can be audited and
# reverted without rewriting the established lexical catalogue.  A term is
# added only when source-name examples show a stable product meaning; broad or
# ambiguous words stay out of the rules and remain review-required.
_REVIEW_BATCH_DEPARTMENT_TERMS: dict[str, tuple[str, ...]] = {
    "fresh_produce": (
        "batata", "berenjena", "chaucha", "choclo fresco", "choclo en chala",
        "choclo suelto", "choclo x kg", "choclo pelado", "choclo natural",
        "coliflor", "hinojo", "hinojos", "perejil fresco", "perejil x kg",
        "perejil paq", "perejil paquete", "puerro", "repollo", "zapallito",
        "jengibre fresco", "raiz de jengibre", "jengibre en raiz", "albahaca fresca",
        "albahaca x kg", "albahaca paq", "albahaca paquete", "albahaca x atado",
    ),
    "beverages": (
        "fresa fizz", "fresas fizz", "clerico fizz", "booch", "kombucha",
        "limonada", "aperitivo", "infusion", "cappuccino",
    ),
    "dairy": (
        "mozzarella", "muzzarella", "muzzarela", "barra tybo", "cheddar",
        "pategras", "reggianito", "port salut", "cremoso", "criollo cabra",
        "q tybo", "q danbo", "queso tybo", "queso danbo",
    ),
    "meat_seafood": (
        "bola de lomo", "carnaza", "osobuco", "chinchulin", "chinchulines",
        "higado", "lengua", "molleja", "mollejas", "rinon", "rinones",
        "muslito", "muslitos", "tapa de nalga", "colita de cuadril", "tomahawk",
        "cuarto trasero", "salamin", "chorizos", "costillas", "milanesas",
        "salchichas", "muslo",
    ),
    "staples": (
        "arveja", "arvejas", "peas", "baguette", "baguetin", "media baguette",
        "pan lactal", "caldo", "caldos", "polvo de hornear", "lentejas", "coco rallado",
    ),
    "snacks_sweets": (
        "bombon", "bombones", "bon o bon", "pretzel", "pretzels", "tostada",
        "tostadas", "choc", "choco", "chocolat", "pochoclo", "pochoclos",
        "dce", "dce.", "dce.batata", "dce.membr", "dce.arandanos", "d batata",
        "gomita", "gomitas", "frutos secos", "fruto seco",
    ),
    "toys_games": (
        "a control remoto", "control remoto de perrito", "vehiculo control remoto",
        "balde bloques", "balde de arena", "arena inteligente",
    ),
    "household_cleaning": (
        "dispenser de jabon", "dispenser de jabón", "escobilla", "escobilla de bano",
        "escobilla de baño", "limpia bano", "limpiabano", "pala con cepillo", "cera liquida", "cera líquida",
    ),
    "home_goods": (
        "tabla de corte", "tabla para cortar", "copetinero", "copetinera",
        "tendedero", "felpudo", "guante de cocina", "manopla cocina", "agarradera",
        "tabla planchar", "tabla de planchar", "sahumerios", "jarro", "mug", "guante cocina",
        "growler", "contenedor", "vasos", "balde", "baldes", "organizadora", "organizadoras",
        "termo", "termos", "toallas", "conservadora",
    ),
    "baby_pet": (
        "mamadera", "biberon", "biberón", "chupete", "tetina", "moises",
        "moisés", "alimento perros", "alimento gatos", "piedras sanitarias",
    ),
    "health_supplements": (
        "mentho-plus", "mentho plus",
    ),
    "personal_care": (
        "acondic", "acondicionad", "acondicionad.", "tintura", "esmalte unas",
        "esmalte de uñas", "bloqueador solar", "toallas femeninas", "toallas fem",
        "toallas humedas", "toallas húmedas",
    ),
    "condiments": (
        "jengibre molido", "jengibre deshidra", "jengibre deshidratado",
        "jengibre en polvo", "curcuma", "cúrcuma", "albahaca desecada",
        "albahaca molida", "oregano", "orégano", "romero", "tomillo",
        "comino", "canela", "laurel", "aceto balsamico", "aceto", "edulcorante",
        "ajo en polvo", "pimenton", "pimentón", "ajo deshidratado", "polvo tajin",
    ),
    "prepared_food": (
        "tarta", "tartas", "tapas p pascualina", "tapas pascualina", "tapas empanadas",
        "tapas p empanadas", "tapas para pascualina", "tapa p pascualina", "tapa pascualina",
        "pascualina", "pascualina criolla", "guiso", "guiso de lentejas", "ensalada", "ensaladas", "waffle vegano", "waffle belga",
        "waffle glaseado", "donut", "donuts",
    ),
    "clothing": (
        "ojota", "ojotas", "pantufla", "pantuflas", "vedetina", "bombacha", "bikini",
        "chomba", "guardapolvo",
    ),
    "sports_luggage": (
        "antiparras",
    ),
    "garden_outdoor": (
        "bulbos crocus", "bulbos anemonas", "bulbos narcisos", "bulbos muscari",
        "bulbos brodiaea", "bulbos fresias", "bulbos jacinto", "bulbos tulipan",
    ),
    "tools_hardware": (
        "alicate media cana", "alicate corte oblicuo", "alicate diagonal",
        "alicate frontal", "alicate corte aislado", "alicate de corte",
    ),
    "electronics": (
        "secador de pelo", "secador cabello", "plancha vapor", "plancha pelo",
        "cocina electrica", "cocina elec", "lavavajillas", "calefactor", "calefactores",
        "cafetera", "cafetera filtro", "ventilador", "vent turbo", "vent de pie",
        "control remoto", "control remoto universal", "camara seguridad", "cámara seguridad",
        "teclado", "teclados", "mouse", "pila alcalina", "pilas alcalinas", "yogurtera", "termotanque",
    ),
    "stationery": (
        "libro", "libros",
    ),
}

_REVIEW_BATCH_DEPARTMENT_EXCLUSIONS: dict[str, tuple[str, ...]] = {
    "fresh_produce": (
        "dce", "dce.batata", "dce.membr", "dce.arandanos", "dulce", "dulcor",
        "jugo", "zumo", "juice", "bebida", "drink", "limonada", "booch",
        "kombucha", "aperitivo", "tarta", "tartas", "postre", "dessert",
        "cacao", "choco", "chocolate", "caramelo", "refrig", "deshidrat",
        "deshidra", "deshidratado", "desecada", "molido", "molida", "polvo",
        "frito", "fritas", "pastafrola", "pastelito", "pastelitos", "locion",
        "lotion", "crema", "cosmet", "balsamo", "queso", "qso", "pizza",
        "sorrentino", "medallon", "medallones", "pan", "baguetin", "dce.",
        "d batata", "lata", "lat", "conserva", "te", "saquito", "saquitos",
        "infusion", "sopa", "salsa",
    ),
    "beverages": (
        "sodastream",
    ),
    "staples": (
        "croqueta", "croquetas", "medallon", "medallones", "med nutree", "hamburguesa", "tarta", "guiso",
    ),
    "dairy": (
        "pochoclo", "pochoclos", "popcorn", "snack", "snacks", "muslito",
        "muslitos", "pizza", "empanada", "hamburguesa", "galleta", "galletita",
        "cracker", "salsa", "dip", "postre", "helado", "tarta", "relleno",
        "pan",
    ),
    "snacks_sweets": (
        "cepillo", "dental", "shampoo", "acondic", "acondicionador", "coloracion",
        "lavavajillas", "lavavajilla", "sodastream", "electrico", "electrica",
        "jabon", "detergente", "locion", "lotion", "cosmet", "perfume", "pintura",
        "herramienta", "herramient", "juguete", "toy", "cable", "celular", "tablet",
        "mueble", "silla", "mesa", "crema dental", "pasta dental", "tocador",
        "vitalidad", "calcio", "defensas", "suplemento", "suplementos", "vitamina", "vitaminas",
    ),
    "condiments": (
        "acondic", "acondicionad", "acondicionad.", "acondicionador", "shampoo", "sedal", "crema", "jabon",
        "locion", "perfume", "cosmet", "bebida", "limonada", "kombucha",
        "booch", "sh", "hs", "head shoulders", "fructis", "nivea", "ogx",
        "dove", "jergens", "capilatis", "body", "corp", "capilar", "balsamo",
        "neutrogena", "algabo", "gel dental", "pasta dental",
    ),
    "prepared_food": (
        "molde", "cuch", "cuchillo", "utensilio",
        "herramienta",
    ),
    "home_goods": (
        "yogurtera", "toallas fem", "toallas femeninas", "toallas humedas", "toallas húmedas",
        "incontinencia", "bloques", "balde de arena",
    ),
    "electronics": (
        "a control remoto", "vehiculo control remoto", "auto a control remoto",
    ),
}

# Batch 2026-11-18: shopper-query vocabulary gaps found by the relevance set.
# Additive and revertible; no existing term or exclusion is removed.
_REVIEW_BATCH_DEPARTMENT_TERMS.update({
    "dairy": _REVIEW_BATCH_DEPARTMENT_TERMS["dairy"] + ("crema",),
    "staples": _REVIEW_BATCH_DEPARTMENT_TERMS["staples"] + (
        "garbanzos", "garbanzo", "porotos", "poroto", "avena", "polenta", "quinoa",
    ),
    "snacks_sweets": _REVIEW_BATCH_DEPARTMENT_TERMS["snacks_sweets"] + (
        "caramelos", "caramelo", "golosinas", "chicles", "chicle",
    ),
    "household_cleaning": _REVIEW_BATCH_DEPARTMENT_TERMS["household_cleaning"] + (
        "jabon", "jabón", "bolsas", "bolsa", "papel de cocina", "servilletas",
    ),
    "personal_care": _REVIEW_BATCH_DEPARTMENT_TERMS["personal_care"] + (
        "dentifrico", "dentífrico", "hilo dental",
    ),
    "baby_pet": _REVIEW_BATCH_DEPARTMENT_TERMS["baby_pet"] + (
        "panales", "pañales", "toallitas", "alimento para perros", "alimento para gatos",
    ),
    "health_supplements": _REVIEW_BATCH_DEPARTMENT_TERMS["health_supplements"] + (
        "ibuprofeno", "paracetamol", "aspirina", "analgesico", "analgésico", "vitamina c",
    ),
    "prepared_food": _REVIEW_BATCH_DEPARTMENT_TERMS["prepared_food"] + (
        "empanada", "empanadas",
    ),
})
_REVIEW_BATCH_DEPARTMENT_EXCLUSIONS.update({
    "dairy": _REVIEW_BATCH_DEPARTMENT_EXCLUSIONS["dairy"] + (
        "dental", "facial", "corporal", "manos", "cosmetic", "shampoo",
    ),
    "household_cleaning": _REVIEW_BATCH_DEPARTMENT_EXCLUSIONS.get("household_cleaning", ()) + (
        "tocador",
    ),
})


def _extend_review_batch_rules(rule: TaxonomyRule) -> TaxonomyRule:
    terms = tuple(dict.fromkeys(rule.terms + _REVIEW_BATCH_DEPARTMENT_TERMS.get(rule.key, ())))
    exclusions = tuple(dict.fromkeys(rule.excluded_terms + _REVIEW_BATCH_DEPARTMENT_EXCLUSIONS.get(rule.key, ())))
    return replace(rule, terms=terms, excluded_terms=exclusions)


DEPARTMENT_RULES = tuple(_extend_review_batch_rules(rule) for rule in DEPARTMENT_RULES)


# Form rules are intentionally reusable across families.  They are used to
# produce clarification choices from forms actually observed in the source.
FORM_RULES: tuple[TaxonomyRule, ...] = (
    TaxonomyRule("fresh", "Fresco", "Fresh", ("fresco", "fresca", "fresh", "crudo", "cruda", "raw"), priority=10),
    TaxonomyRule("frozen", "Congelado", "Frozen", ("congelado", "congelada", "frozen"), priority=10),
    TaxonomyRule("liquid", "Líquido", "Liquid", ("liquido", "liquida", "liquidos", "liquidas", "liquid"), priority=10),
    TaxonomyRule("powder", "En polvo", "Powder", ("polvo", "powder"), priority=10),
    TaxonomyRule("grain", "En grano", "Grain", ("grano", "granos", "grain", "grains", "largo", "integral", "parboil", "parboiled"), priority=10),
    TaxonomyRule("flour", "Harina", "Flour", ("harina", "flour"), priority=10),
    TaxonomyRule("pasta", "Pasta o fideos", "Pasta or noodles", ("pasta", "fideo", "fideos", "noodle", "noodles"), priority=10),
    TaxonomyRule("biscuit", "Galleta o bizcocho", "Biscuit or cookie", ("galleta", "galletita", "galletitas", "cookie", "cookies", "biscuit", "biscuits", "cracker", "crackers"), priority=10),
    TaxonomyRule("dessert", "Postre o dulce", "Dessert or sweet", ("postre", "dessert", "torta", "cake", "dulce", "caramelo", "candy", "alfajor", "helado", "ice cream"), priority=10),
    TaxonomyRule("drink", "Bebida", "Drink", ("bebida", "drink", "jugo", "juice", "zumo", "nectar", "gaseosa", "soda"), priority=10),
    TaxonomyRule("alcohol", "Bebida alcohólica", "Alcoholic drink", ("vino", "wine", "cerveza", "beer", "whisky", "whiskey", "vodka", "gin", "ron", "rum", "fernet", "licor", "espumante", "champagne", "sidra", "cider"), priority=10),
    TaxonomyRule("tea_bags", "En saquitos", "Tea bags", ("saquito", "saquitos", "teabag", "tea bags", "filtro", "infusion"), priority=10),
    TaxonomyRule("loose_leaves", "Suelto o en hojas", "Loose leaves", ("hoja", "hojas", "loose leaves", "yerba mate", "yerba"), priority=10),
    TaxonomyRule("canned", "En conserva", "Canned or preserved", ("lata", "enlatado", "conserva", "almibar", "jarabe", "canned", "preserved", "syrup"), priority=10),
    TaxonomyRule("cosmetics", "Cosmético", "Cosmetic", ("cosmetico", "cosmetics", "crema facial", "face cream", "locion", "lotion", "maquillaje", "makeup", "perfume", "shampoo", "acondicionador", "conditioner"), priority=10),
    TaxonomyRule("cleaning", "Limpieza", "Cleaning", ("detergente", "cleaner", "limpiador", "lavandina", "bleach", "desinfectante", "disinfectant", "limpieza", "laundry"), priority=10),
    TaxonomyRule("supplement", "Suplemento", "Supplement", ("suplemento", "supplement", "vitamina", "vitamin", "extracto", "extract", "capsula", "capsule", "proteina", "protein"), priority=10),
    TaxonomyRule("whole_egg", "Huevo entero", "Whole egg", ("huevo fresco", "huevos frescos", "huevo entero", "huevos enteros", "huevo con cascara", "huevos con cascara", "whole egg", "whole eggs", "in shell"), excluded_terms=("fideo", "fideos", "pasta", "chocolate", "juguete", "toy", "sorpresa"), priority=10),
)

# Clarification choices should put the shopper's likely direct product form
# first.  Alphabetical ordering made a query such as ``huevos`` start with
# incidental alcohol or pasta matches and hide ``Huevo entero`` after the
# six-choice bound.  The order is presentation-only; it does not change the
# underlying candidate set or any price/evidence semantics.
FORM_CLARIFICATION_ORDER = (
    "whole_egg",
    "fresh",
    "grain",
    "liquid",
    "powder",
    "drink",
    "loose_leaves",
    "flour",
    "pasta",
    "biscuit",
    "dessert",
    "canned",
    "tea_bags",
    "frozen",
    "cosmetics",
    "cleaning",
    "supplement",
    "alcohol",
)
FORM_CLARIFICATION_RANK = {key: index for index, key in enumerate(FORM_CLARIFICATION_ORDER)}


@lru_cache(maxsize=4096)
def _tokens(value: str) -> tuple[str, ...]:
    return tuple(normalize_text(value, max_length=MAX_NAME_LENGTH).split())


def _contains_sequence(tokens: Sequence[str], wanted: Sequence[str]) -> bool:
    if not wanted or len(wanted) > len(tokens):
        return False
    size = len(wanted)
    return any(tuple(tokens[index:index + size]) == tuple(wanted) for index in range(len(tokens) - size + 1))


def _matched_terms(tokens: Sequence[str], rule: TaxonomyRule) -> tuple[str, ...]:
    matched = [term for term in rule.terms if _contains_sequence(tokens, _tokens(term))]
    return tuple(sorted(set(matched), key=lambda value: (len(_tokens(value)) * -1, value)))[:MAX_MATCHED_TERMS]


def _has_exclusion(tokens: Sequence[str], rule: TaxonomyRule) -> bool:
    return any(_contains_sequence(tokens, _tokens(term)) for term in rule.excluded_terms)


def _candidate(rule: TaxonomyRule, matched: Sequence[str]) -> dict[str, Any]:
    return {
        "key": rule.key,
        "labelEs": rule.label_es,
        "labelEn": rule.label_en,
        "matchedTerms": list(matched),
        "evidence": "SOURCE_NAME",
        "priority": rule.priority,
    }


def classify_name(name: str, *, family_candidates: Iterable[Mapping[str, Any]] = ()) -> dict[str, Any]:
    """Classify one source name without inventing a product identity."""

    if not isinstance(name, str) or not name.strip() or len(name) > MAX_NAME_LENGTH:
        raise ValueError("product name is invalid")
    clean_name = " ".join(name.split())
    tokens = _tokens(clean_name)
    departments: list[dict[str, Any]] = []
    for rule in DEPARTMENT_RULES:
        matched = _matched_terms(tokens, rule)
        if matched and not _has_exclusion(tokens, rule):
            departments.append(_candidate(rule, matched))
    departments.sort(key=lambda item: (-int(item["priority"]), str(item["key"])))

    forms: list[dict[str, Any]] = []
    for rule in FORM_RULES:
        matched = _matched_terms(tokens, rule)
        if matched and not _has_exclusion(tokens, rule):
            forms.append(_candidate(rule, matched))
    # ``whole_egg`` is an explicit shell/whole presentation.  When the name
    # states a different presentation, keep that evidence separate instead
    # of presenting liquid or powdered egg as whole eggs as well.
    specific_egg_forms = {"liquid", "powder", "biscuit", "dessert", "drink", "canned", "cosmetics"}
    if any(item["key"] in specific_egg_forms for item in forms):
        forms = [item for item in forms if item["key"] != "whole_egg"]
    forms.sort(key=lambda item: str(item["key"]))

    families = []
    for family in family_candidates:
        if not isinstance(family, Mapping):
            continue
        key = family.get("family")
        if isinstance(key, str) and key.strip():
            families.append({
                "family": key,
                "role": str(family.get("role") or "UNKNOWN"),
                "observedForms": sorted({str(value) for value in family.get("observedForms", ()) if str(value).strip()}),
                "matchedTerms": sorted({str(value) for value in family.get("matchedTerms", ()) if str(value).strip()}),
                "evidence": "REVIEWED_FAMILY_LEXICON",
            })
    families.sort(key=lambda item: (item["family"], item["role"], item["matchedTerms"]))

    if departments:
        top_priority = int(departments[0]["priority"])
        top = [item for item in departments if int(item["priority"]) == top_priority]
        primary = top[0]["key"] if len(top) == 1 else None
    else:
        primary = None

    if primary is not None:
        status = "CLASSIFIED"
    elif departments or families:
        status = "REVIEW_REQUIRED"
    else:
        status = "REVIEW_REQUIRED"
    return {
        "coverageStatus": status,
        "primaryDepartment": primary,
        "departmentCandidates": departments,
        "familyCandidates": families,
        "formCandidates": forms[:MAX_FORMS],
    }


def _family_keys_for_query(query: str) -> tuple[str, ...]:
    """Return reviewed family keys explicitly present in a customer query."""

    from tools.product_identity_lexicon import FAMILY_RULES

    tokens = _tokens(query)
    keys = []
    for rule in FAMILY_RULES:
        if any(_contains_sequence(tokens, _tokens(term)) for term in rule.include_terms):
            keys.append(rule.key)
    return tuple(dict.fromkeys(keys))


def taxonomy_selection_form(query: str, selection_id: str | None) -> tuple[str, str] | None:
    """Resolve a stable taxonomy clarification predicate for one query."""

    if not isinstance(selection_id, str) or not selection_id.strip():
        return None
    parts = selection_id.strip().split(":")
    if len(parts) != 2 or not all(parts):
        return None
    family_keys = _family_keys_for_query(query)
    form_keys = {rule.key for rule in FORM_RULES}
    if len(family_keys) != 1 or family_keys[0] != parts[0] or parts[1] not in form_keys:
        return None
    return parts[0], parts[1]


def _identity_for_candidate(candidate: Mapping[str, Any], index: int) -> dict[str, Any] | None:
    """Derive identity evidence for one bounded Search candidate."""

    from tools.product_identity_lexicon import derive_identity_record

    name = candidate.get("name")
    if not isinstance(name, str) or not name.strip():
        return None
    key = candidate.get("productEvidenceKey")
    if not isinstance(key, str) or not key.strip():
        key = f"taxonomy-candidate:{index}"
    try:
        return derive_identity_record({
            "productEvidenceKey": key,
            "name": name,
            "brand": candidate.get("brand"),
            "gtin": candidate.get("gtin"),
            "quantity": candidate.get("quantity"),
        })
    except (TypeError, ValueError):
        return None


def _candidate_taxonomy(candidate: Mapping[str, Any], index: int) -> Mapping[str, Any] | None:
    """Use a release sidecar taxonomy when present, with a safe fallback."""

    supplied = candidate.get("_taxonomy")
    if isinstance(supplied, Mapping):
        return supplied
    identity = _identity_for_candidate(candidate, index)
    if identity is None:
        return None
    return classify_name(
        str(candidate.get("name") or ""),
        family_candidates=identity.get("familyCandidates", ()),
    )


def build_taxonomy_clarification(
    query: str,
    candidates: Iterable[Mapping[str, Any]],
) -> dict[str, Any] | None:
    """Expose observed product forms for generic reviewed families.

    The result is a presentation hint only.  It is emitted when the bounded
    Search window contains at least two explicit forms for one family.  It
    never invents a form that is absent from the candidates.
    """

    normalized_query = " ".join(str(query or "").split())
    if not normalized_query:
        return None
    family_keys = _family_keys_for_query(normalized_query)
    if len(family_keys) != 1:
        return None
    family_key = family_keys[0]
    form_counts: Counter[str] = Counter()
    form_examples: dict[str, dict[str, Any]] = {}
    for index, candidate in enumerate(candidates):
        if not isinstance(candidate, Mapping):
            continue
        taxonomy = _candidate_taxonomy(candidate, index)
        if taxonomy is None or not any(item.get("family") == family_key for item in taxonomy.get("familyCandidates", ()) if isinstance(item, Mapping)):
            continue
        for form in taxonomy.get("formCandidates", ()):
            form_key = form.get("key") if isinstance(form, Mapping) else None
            if not isinstance(form_key, str) or not form_key:
                continue
            form_counts[form_key] += 1
            form_examples.setdefault(form_key, dict(form))
    forms = sorted(
        (key for key in form_counts if form_counts[key] > 0),
        key=lambda key: (FORM_CLARIFICATION_RANK.get(key, len(FORM_CLARIFICATION_ORDER)), key),
    )
    if len(forms) < 2:
        return None
    choices = []
    for form_key in forms[:MAX_CLARIFICATION_CHOICES]:
        form = form_examples[form_key]
        choices.append({
            "id": f"{family_key}:{form_key}",
            "labelEs": form["labelEs"],
            "labelEn": form["labelEn"],
            "query": f"{normalized_query} {form_key.replace('_', ' ')}",
            "observedCount": form_counts[form_key],
        })
    return {
        "code": "PRODUCT_TAXONOMY_CLARIFICATION",
        "family": family_key,
        "message": "¿Qué tipo de producto buscás? / What kind of product are you looking for?",
        "choices": choices,
    }


def filter_taxonomy_candidates(
    query: str,
    candidates: Iterable[Mapping[str, Any]],
    *,
    selection_id: str | None = None,
) -> tuple[Mapping[str, Any], ...]:
    """Apply an explicit observed-form refinement from a clarification choice."""

    values = tuple(candidate for candidate in candidates if isinstance(candidate, Mapping))
    family_keys = _family_keys_for_query(query)
    if len(family_keys) != 1:
        return values
    query_tokens = _tokens(query)
    selected_context = taxonomy_selection_form(query, selection_id) if selection_id is not None else None
    requested_forms = ([selected_context[1]] if selected_context is not None else [
        rule.key
        for rule in FORM_RULES
        if any(_contains_sequence(query_tokens, _tokens(term)) for term in rule.terms)
    ])
    if not requested_forms:
        return values
    filtered: list[Mapping[str, Any]] = []
    family_key = family_keys[0]
    for index, candidate in enumerate(values):
        taxonomy = _candidate_taxonomy(candidate, index)
        if taxonomy is None or not any(item.get("family") == family_key for item in taxonomy.get("familyCandidates", ()) if isinstance(item, Mapping)):
            continue
        forms = {item.get("key") for item in taxonomy.get("formCandidates", ()) if isinstance(item, Mapping)}
        if forms.intersection(requested_forms):
            filtered.append(candidate)
    # An explicit stable selection is a strict predicate.  Never restore the
    # unfiltered candidate set when its accepted membership is empty; doing so
    # would make a stale or contradictory choice broaden the result table.
    if selected_context is not None:
        return tuple(filtered)
    return tuple(filtered) if filtered else values


def _open_text(path: Path, mode: str):
    if path.suffix == ".gz":
        return gzip.open(path, mode + "t", encoding="utf-8", newline="")
    return path.open(mode + "t", encoding="utf-8", newline="")


def _iter_jsonl(path: Path) -> Iterator[tuple[int, Mapping[str, Any]]]:
    try:
        with _open_text(path, "r") as handle:
            for line_number, line in enumerate(handle, 1):
                if not line.strip():
                    continue
                value = json.loads(line)
                if not isinstance(value, Mapping):
                    raise ValueError(f"source line {line_number} is not an object")
                yield line_number, value
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValueError(f"could not read taxonomy source: {path}") from exc


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _canonical_json(value: Mapping[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _write_jsonl(path: Path, values: Iterable[Mapping[str, Any]]) -> tuple[int, str, int]:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as raw:
        if path.suffix == ".gz":
            stream = gzip.GzipFile(fileobj=raw, mode="wb", mtime=0)
        else:
            stream = raw
        try:
            count = 0
            for value in values:
                stream.write((_canonical_json(value) + "\n").encode("utf-8"))
                count += 1
        finally:
            if stream is not raw:
                stream.close()
    return count, _sha256_file(path), path.stat().st_size


def _identity_names(identity: Mapping[str, Any]) -> tuple[str, ...]:
    values: list[str] = []
    name = identity.get("name")
    if isinstance(name, str) and name.strip():
        values.append(" ".join(name.split()))
    evidence = identity.get("sourceEvidence")
    samples = evidence.get("nameSamples") if isinstance(evidence, Mapping) else None
    if isinstance(samples, Sequence) and not isinstance(samples, (str, bytes)):
        values.extend(" ".join(str(value).split()) for value in samples if isinstance(value, str) and value.strip())
    return tuple(dict.fromkeys(values))[:MAX_NAMES_PER_PROVIDER]


def _provider_product_id(identity: Mapping[str, Any]) -> str:
    value = identity.get("providerProductId")
    if isinstance(value, str) and value.strip():
        return value.strip()
    raise ValueError("identity is missing providerProductId")


def build_taxonomy_index(
    source_path: Path | str,
    output_path: Path | str,
    report_path: Path | str,
    review_output: Path | str | None = None,
    *,
    source_release_id: str | None = None,
    source_release_sha256: str | None = None,
) -> dict[str, Any]:
    """Aggregate retailer identities into a deterministic provider taxonomy."""

    source = Path(source_path).resolve()
    output = Path(output_path).resolve()
    report_file = Path(report_path).resolve()
    if not source.is_file():
        raise ValueError(f"taxonomy source does not exist: {source}")
    if source == output or source == report_file:
        raise ValueError("taxonomy outputs must be separate from source")

    providers: dict[str, dict[str, Any]] = {}
    source_records = 0
    for line_number, identity in _iter_jsonl(source):
        source_records += 1
        provider_id = _provider_product_id(identity)
        aggregate = providers.setdefault(
            provider_id,
            {
                "providerProductId": provider_id,
                "identityKeys": [],
                "names": Counter(),
                "brands": Counter(),
                "familyCandidates": {},
                "identityCount": 0,
            },
        )
        aggregate["identityCount"] += 1
        identity_key = identity.get("productEvidenceKey")
        if isinstance(identity_key, str) and identity_key.strip() and len(aggregate["identityKeys"]) < MAX_IDENTITIES_PER_PROVIDER:
            aggregate["identityKeys"].append(identity_key)
        for name in _identity_names(identity):
            aggregate["names"][name] += 1
        brand = identity.get("brand")
        if isinstance(brand, str) and brand.strip():
            aggregate["brands"][" ".join(brand.split())] += 1
        families = identity.get("familyCandidates")
        if isinstance(families, Sequence) and not isinstance(families, (str, bytes)):
            for family in families:
                if not isinstance(family, Mapping):
                    continue
                key = family.get("family")
                if not isinstance(key, str) or not key.strip():
                    continue
                target = aggregate["familyCandidates"].setdefault(key, {"roles": set(), "forms": set(), "matchedTerms": set()})
                role = family.get("role")
                if isinstance(role, str):
                    target["roles"].add(role)
                for field_name, target_name in (("observedForms", "forms"), ("matchedTerms", "matchedTerms")):
                    values = family.get(field_name)
                    if isinstance(values, Sequence) and not isinstance(values, (str, bytes)):
                        target[target_name].update(str(value) for value in values if str(value).strip())

    def provider_records() -> Iterator[dict[str, Any]]:
        for provider_id in sorted(providers):
            aggregate = providers[provider_id]
            names: Counter[str] = aggregate["names"]
            canonical_names = [name for name, _count in sorted(names.items(), key=lambda item: (-item[1], normalize_text(item[0])))][:MAX_NAMES_PER_PROVIDER]
            if not canonical_names:
                raise ValueError(f"provider {provider_id} has no product name")
            family_candidates = []
            for family, evidence in sorted(aggregate["familyCandidates"].items()):
                family_candidates.append({
                    "family": family,
                    "role": "DIRECT" if "DIRECT" in evidence["roles"] else "DERIVED",
                    "matchedTerms": sorted(evidence["matchedTerms"]),
                    "observedForms": sorted(evidence["forms"]),
                    "evidence": "REVIEWED_FAMILY_LEXICON",
                })
            # Classify every observed name, then union the evidence.  A term
            # found at one retailer is retained for the provider product.
            classified = [classify_name(name, family_candidates=family_candidates) for name in canonical_names]
            department_by_key: dict[str, dict[str, Any]] = {}
            form_by_key: dict[str, dict[str, Any]] = {}
            statuses = []
            for result in classified:
                statuses.append(result["coverageStatus"])
                for item in result["departmentCandidates"]:
                    existing = department_by_key.setdefault(item["key"], {**item, "matchedTerms": []})
                    existing["matchedTerms"] = sorted(set(existing["matchedTerms"]) | set(item["matchedTerms"]))[:MAX_MATCHED_TERMS]
                for item in result["formCandidates"]:
                    existing = form_by_key.setdefault(item["key"], {**item, "matchedTerms": []})
                    existing["matchedTerms"] = sorted(set(existing["matchedTerms"]) | set(item["matchedTerms"]))[:MAX_MATCHED_TERMS]
            departments = sorted(department_by_key.values(), key=lambda item: (-int(item["priority"]), item["key"]))
            top_priority = int(departments[0]["priority"]) if departments else None
            top = [item for item in departments if int(item["priority"]) == top_priority] if top_priority is not None else []
            primary = top[0]["key"] if len(top) == 1 else None
            if primary is not None:
                status = "CLASSIFIED"
            elif departments or family_candidates:
                status = "REVIEW_REQUIRED"
            else:
                status = "REVIEW_REQUIRED"
            yield {
                "providerProductId": provider_id,
                "name": canonical_names[0],
                "nameSamples": canonical_names,
                "brand": next(iter(sorted(aggregate["brands"], key=lambda value: (-aggregate["brands"][value], normalize_text(value)))) , None),
                "identityCount": aggregate["identityCount"],
                "identityKeys": sorted(set(aggregate["identityKeys"])),
                "taxonomy": {
                    "coverageStatus": status,
                    "primaryDepartment": primary,
                    "departmentCandidates": departments,
                    "familyCandidates": family_candidates,
                    "formCandidates": sorted(form_by_key.values(), key=lambda item: item["key"])[:MAX_FORMS],
                },
            }

    # Materialize the bounded provider list once so the report and both output
    # streams are byte-for-byte consistent.
    records = list(provider_records())
    count, output_sha256, output_bytes = _write_jsonl(output, records)
    review_records = [
        {
            "providerProductId": value["providerProductId"],
            "name": value["name"],
            "nameSamples": value["nameSamples"],
            "brand": value["brand"],
            "identityCount": value["identityCount"],
            "coverageStatus": value["taxonomy"]["coverageStatus"],
            "primaryDepartment": value["taxonomy"]["primaryDepartment"],
            "departmentCandidates": value["taxonomy"]["departmentCandidates"],
            "familyCandidates": value["taxonomy"]["familyCandidates"],
            "formCandidates": value["taxonomy"]["formCandidates"],
        }
        for value in records
        if value["taxonomy"]["coverageStatus"] == "REVIEW_REQUIRED"
    ]
    review_meta = None
    if review_output is not None:
        review_path = Path(review_output).resolve()
        review_count, review_sha256, review_bytes = _write_jsonl(review_path, review_records)
        review_meta = {"fileName": review_path.name, "records": review_count, "sha256": review_sha256, "bytes": review_bytes}

    status_counts = Counter(value["taxonomy"]["coverageStatus"] for value in records)
    department_counts = Counter()
    form_counts = Counter()
    family_counts = Counter()
    multiple_departments = 0
    for value in records:
        taxonomy = value["taxonomy"]
        if len(taxonomy["departmentCandidates"]) > 1:
            multiple_departments += 1
        if taxonomy["primaryDepartment"]:
            department_counts[taxonomy["primaryDepartment"]] += 1
        for item in taxonomy["departmentCandidates"]:
            department_counts[f"candidate:{item['key']}"] += 1
        for item in taxonomy["formCandidates"]:
            form_counts[item["key"]] += 1
        for item in taxonomy["familyCandidates"]:
            family_counts[item["family"]] += 1

    report = {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "status": "COMPLETE",
        "source": {
            "fileName": source.name,
            "sha256": _sha256_file(source),
            "identityRecords": source_records,
            "releaseId": source_release_id,
            "releaseSourceSha256": source_release_sha256,
        },
        "output": {"fileName": output.name, "providerProductRecords": count, "sha256": output_sha256, "bytes": output_bytes},
        "coverage": {
            "providerProductIds": len(records),
            "statusCounts": dict(sorted(status_counts.items())),
            "reviewQueueRecords": len(review_records),
            "productsWithMultipleDepartmentCandidates": multiple_departments,
            "departmentCandidateCounts": dict(sorted(department_counts.items())),
            "formCounts": dict(sorted(form_counts.items())),
            "familyCounts": dict(sorted(family_counts.items())),
        },
        "safety": {
            "prices": False,
            "offers": False,
            "stock": False,
            "availability": False,
            "credentials": False,
            "forcedSemanticClassification": False,
            "everyProviderProductHasCoverageStatus": len(records) == len(providers),
        },
    }
    report_file.parent.mkdir(parents=True, exist_ok=True)
    report_file.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return report


__all__ = [
    "DEPARTMENT_RULES",
    "FORM_RULES",
    "FORM_CLARIFICATION_ORDER",
    "MAX_INDEX_BYTES",
    "MAX_INDEX_RECORDS",
    "POLICY_VERSION",
    "ProductTaxonomyIndex",
    "SCHEMA_VERSION",
    "build_taxonomy_index",
    "build_taxonomy_clarification",
    "classify_name",
    "taxonomy_selection_form",
    "filter_taxonomy_candidates",
]
