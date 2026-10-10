package com.valuepilot.app.next

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

internal fun valuePilotNextText(
    language: ValuePilotNextLanguage,
    english: String,
    spanishArgentina: String
): String =
    if (language == ValuePilotNextLanguage.SPANISH_AR) spanishArgentina else english

/** Resolves short Activity-owned messages from the same persisted preview preference. */
internal fun valuePilotNextText(
    context: Context,
    english: String,
    spanishArgentina: String
): String = valuePilotNextText(
    language = ValuePilotNextUiPreferencesCodec.resolvesLanguage(
        ValuePilotNextUiPreferencesStore(context).load().language
    ),
    english = english,
    spanishArgentina = spanishArgentina
)

/** The single language source used by every native preview surface. */
internal val LocalValuePilotNextLanguage =
    staticCompositionLocalOf { ValuePilotNextLanguage.ENGLISH }

/** Localizes renderer-owned Search status and evidence copy without translating data. */
internal fun valuePilotNextLocalizedSearchText(
    language: ValuePilotNextLanguage,
    text: String
): String {
    if (language != ValuePilotNextLanguage.SPANISH_AR) return text
    return when (text) {
        "Search a product in the qualified Argentina area." ->
            "Buscá un producto en el área calificada de Argentina."
        "Searching Argentina product identities…" ->
            "Buscando identidades de productos de Argentina…"
        "Loading more matching identities…" ->
            "Cargando más identidades coincidentes…"
        "Qualified product identities and listed-price evidence found. This release reached the bounded result limit." ->
            "Se encontraron identidades calificadas y evidencia de precios publicados. Esta versión alcanzó el límite de resultados."
        "Qualified product identities and listed-price evidence found. More matching identities are available." ->
            "Se encontraron identidades calificadas y evidencia de precios publicados. Hay más identidades coincidentes disponibles."
        "Qualified product identities and listed-price evidence found. No more matching identities were returned in this release." ->
            "Se encontraron identidades calificadas y evidencia de precios publicados. Esta versión no devolvió más identidades coincidentes."
        "Product identities found. This release reached the bounded result limit." ->
            "Se encontraron identidades de productos. Esta versión alcanzó el límite de resultados."
        "Product identities found. More matching identities are available." ->
            "Se encontraron identidades de productos. Hay más identidades coincidentes disponibles."
        "Product identities found. No more matching identities were returned in this release." ->
            "Se encontraron identidades de productos. Esta versión no devolvió más identidades coincidentes."
        "No matching product identity was found in this release." ->
            "No se encontró una identidad de producto coincidente en esta versión."
        "Live Search is not authorized on this build yet." ->
            "Live Search todavía no está autorizado en esta compilación."
        "This release is no longer current. Try again later." ->
            "Esta versión ya no está vigente. Probá más tarde."
        "Search took too long. Try again." ->
            "La búsqueda tardó demasiado. Probá de nuevo."
        "Live Search is temporarily unavailable. Try again." ->
            "Live Search no está disponible por ahora. Probá de nuevo."
        "Identity suggestions only. No current price, stock, availability or ranking is confirmed; package size appears only when the source supplies it explicitly." ->
            "Solo sugerencias de identidad. No se confirman precio actual, stock, disponibilidad ni ranking; el tamaño del envase aparece únicamente cuando la fuente lo informa de forma explícita."
        "Exact fresh listed prices and stores appear only where the qualified source attached them. Stock, pickup, delivery and Best Value ranking remain unknown." ->
            "Los precios publicados recientes y las tiendas exactas aparecen solo cuando la fuente calificada los adjunta. El stock, retiro, entrega y ranking de mejor valor siguen sin conocerse."
        else -> text
    }
}

@Composable
internal fun valuePilotNextText(
    english: String,
    spanishArgentina: String
): String = valuePilotNextText(
    LocalValuePilotNextLanguage.current,
    english,
    spanishArgentina
)

/**
 * Localizes fixed presentation copy emitted by the existing Saved feature renderers.
 * Product names, store names, prices, dates, and provider text are intentionally returned
 * unchanged because they are data rather than interface copy.
 */
internal fun valuePilotNextLocalizedDynamicText(
    language: ValuePilotNextLanguage,
    text: String?
): String? {
    if (text == null || language != ValuePilotNextLanguage.SPANISH_AR) return text
    return when {
        text.equals("No saved choices yet.", ignoreCase = true) ->
            "Todavía no hay elecciones guardadas."
        text.equals("Refresh", ignoreCase = true) -> "Actualizar"
        text.equals("Try again", ignoreCase = true) -> "Intentar de nuevo"
        text.equals("Load saved choices", ignoreCase = true) -> "Cargar elecciones guardadas"
        text.equals("Loading saved choices…", ignoreCase = true) ->
            "Cargando elecciones guardadas…"
        text.equals("Refreshing saved choices…", ignoreCase = true) ->
            "Actualizando elecciones guardadas…"
        text.equals("Updating saved choices…", ignoreCase = true) ->
            "Actualizando elecciones guardadas…"
        text.equals("Saved choices are ready to load.", ignoreCase = true) ->
            "Las elecciones guardadas están listas para cargar."
        text.equals("Exact product choice", ignoreCase = true) ->
            "Elección exacta de producto"
        text.equals("Exact store choice", ignoreCase = true) ->
            "Elección exacta de tienda"
        text.equals("Usual-store basket prices", ignoreCase = true) ->
            "Precios de canasta de la tienda habitual"
        text.equals("Alternative store identities", ignoreCase = true) ->
            "Identidades de tiendas alternativas"
        text.equals("Alternative-store basket prices", ignoreCase = true) ->
            "Precios de canasta de tiendas alternativas"
        text.equals("Additional travel details", ignoreCase = true) ->
            "Detalles adicionales del viaje"
        text.equals("Evidence freshness metadata", ignoreCase = true) ->
            "Metadatos de vigencia de evidencia"
        text.equals("Choose at least two saved staples and your usual store.", ignoreCase = true) ->
            "Elegí al menos dos esenciales guardados y tu tienda habitual."
        text.equals(
            "Staple identities are ready for current price, route, and evidence checks.",
            ignoreCase = true
        ) -> "Las identidades de esenciales están listas para revisar precio actual, ruta y evidencia."
        text.equals("Refresh the selected saved choice names before continuing.", ignoreCase = true) ->
            "Actualizá los nombres de las elecciones guardadas antes de continuar."
        text.equals("Choose one saved product and one saved store.", ignoreCase = true) ->
            "Elegí un producto y una tienda guardados."
        text.equals(
            "The selected Saved identities can be checked for safe observed-price prefill.",
            ignoreCase = true
        ) -> "Las identidades guardadas elegidas se pueden comprobar para completar el precio observado de forma segura."
        text.equals("Refresh the selected saved choice name before checking this pair.", ignoreCase = true) ->
            "Actualizá el nombre de la elección guardada antes de comprobar este par."
        text.equals(
            "These are your exact comparison observations stored on this device. They are personal context, not live store prices, inventory, retailer offers and not a guarantee. Matching requires the same name, package, currency, price basis and promotion terms.",
            ignoreCase = true
        ) -> "Estas son tus observaciones exactas de comparación guardadas en este dispositivo. Son contexto personal, no precios actuales, inventario, ofertas de minoristas ni una garantía. La coincidencia requiere el mismo nombre, envase, moneda, base de precio y condiciones de promoción."
        text.equals("Clear selection", ignoreCase = true) -> "Borrar selección"
        text.equals("Clear product", ignoreCase = true) -> "Borrar producto"
        text.equals("Select product", ignoreCase = true) -> "Elegir producto"
        text.equals("Clear store", ignoreCase = true) -> "Borrar tienda"
        text.equals("Select store", ignoreCase = true) -> "Elegir tienda"
        text.equals("Check selected Saved pair", ignoreCase = true) ->
            "Comprobar el par guardado elegido"
        text.equals("Saved products", ignoreCase = true) -> "Productos guardados"
        text.equals("Saved stores", ignoreCase = true) -> "Tiendas guardadas"
        text.equals("Usual store", ignoreCase = true) -> "Tienda habitual"
        text.equals("Choose staples to watch", ignoreCase = true) ->
            "Elegir esenciales para seguir"
        text.equals("Watch My Staples", ignoreCase = true) -> "Mis esenciales"
        text.equals("Stop watching", ignoreCase = true) -> "Dejar de seguir"
        text.equals("Watch", ignoreCase = true) -> "Seguir"
        text.equals("Clear usual store", ignoreCase = true) -> "Borrar tienda habitual"
        text.equals("Use as usual store", ignoreCase = true) -> "Usar como tienda habitual"
        text.equals("Clear staple setup", ignoreCase = true) -> "Borrar configuración de esenciales"
        text.equals("Continue", ignoreCase = true) -> "Continuar"
        text.equals(
            "Choose recurring saved items and a usual store to check whether a future switch is worth the trip.",
            ignoreCase = true
        ) -> "Elegí artículos guardados recurrentes y una tienda habitual para comprobar si un cambio futuro vale el viaje."
        text.equals(
            "Save at least two named products and one named store to set up Watch My Staples.",
            ignoreCase = true
        ) -> "Guardá al menos dos productos con nombre y una tienda con nombre para configurar Mis esenciales."
        text.equals("Save one more named product to set up Watch My Staples.", ignoreCase = true) ->
            "Guardá un producto con nombre más para configurar Mis esenciales."
        text.equals("Save at least two named products to set up Watch My Staples.", ignoreCase = true) ->
            "Guardá al menos dos productos con nombre para configurar Mis esenciales."
        text.equals("Save a named store to set up Watch My Staples.", ignoreCase = true) ->
            "Guardá una tienda con nombre para configurar Mis esenciales."
        text.equals(
            "Some saved names and display cleanup are temporarily unavailable.",
            ignoreCase = true
        ) -> "Algunos nombres guardados y la limpieza visual no están disponibles por ahora."
        text.equals(
            "Some saved names couldn't be loaded. Exact saved choices are unchanged.",
            ignoreCase = true
        ) -> "No se pudieron cargar algunos nombres guardados. Las elecciones exactas no cambiaron."
        text.equals(
            "Saved choices were updated, but some display cleanup is still pending.",
            ignoreCase = true
        ) -> "Las elecciones guardadas se actualizaron, pero falta completar parte de la limpieza visual."
        text.equals("Saved choices couldn't be loaded.", ignoreCase = true) ->
            "No se pudieron cargar las elecciones guardadas."
        text.equals("That saved-choice change couldn't be completed.", ignoreCase = true) ->
            "No se pudo completar ese cambio de elección guardada."
        text.equals("Saved choices are temporarily unavailable.", ignoreCase = true) ->
            "Las elecciones guardadas no están disponibles por ahora."
        text.equals(
            "1 saved choice needs a display name before it can be shown.",
            ignoreCase = true
        ) -> "Una elección guardada necesita un nombre para poder mostrarse."
        text.matches(Regex("\\d+ saved choices need display names before they can be shown\\.", RegexOption.IGNORE_CASE)) ->
            text.replace(
                Regex("saved choices need display names before they can be shown\\."),
                "elecciones guardadas necesitan nombres para poder mostrarse."
            )
        text.equals("Private price history", ignoreCase = true) ->
            "Historial privado de precios"
        text.equals("Exact package histories", ignoreCase = true) ->
            "Historiales de envases exactos"
        text.equals("Scan & compare", ignoreCase = true) -> "Escanear y comparar"
        text.equals("Is this a good price?", ignoreCase = true) -> "¿Es un buen precio?"
        text.equals("Current price", ignoreCase = true) -> "Precio actual"
        text.equals("Member price", ignoreCase = true) -> "Precio de miembro"
        text.equals("Same as previous observation", ignoreCase = true) ->
            "Igual que la observación anterior"
        text.startsWith("Down from previous ", ignoreCase = true) ->
            "Bajó desde la observación anterior " + text.substringAfter("previous ")
        text.startsWith("Up from previous ", ignoreCase = true) ->
            "Subió desde la observación anterior " + text.substringAfter("previous ")
        text.startsWith("Remove saved product ", ignoreCase = true) ->
            "Quitar producto guardado " + text.substringAfter("product ")
        text.startsWith("Check a price for ", ignoreCase = true) ->
            "Comprobar un precio de " + text.substringAfter("for ")
                .replace("; package quantity and price are still required", "; todavía se necesitan cantidad y precio del envase")
        text.startsWith("Remove saved store ", ignoreCase = true) ->
            "Quitar tienda guardada " + text.substringAfter("store ")
        text.startsWith("Clear selected saved product ", ignoreCase = true) ->
            "Borrar producto guardado elegido " + text.substringAfter("product ")
        text.startsWith("Select saved product ", ignoreCase = true) ->
            "Elegir producto guardado " + text.substringAfter("product ")
        text.startsWith("Clear selected saved store ", ignoreCase = true) ->
            "Borrar tienda guardada elegida " + text.substringAfter("store ")
        text.startsWith("Select saved store ", ignoreCase = true) ->
            "Elegir tienda guardada " + text.substringAfter("store ")
        text.startsWith("Stop watching saved product ", ignoreCase = true) ->
            "Dejar de seguir el producto guardado " + text.substringAfter("product ")
        text.startsWith("Watch saved product ", ignoreCase = true) ->
            "Seguir el producto guardado " + text.substringAfter("product ")
        text.startsWith("1 selected saved choice needs a current display name before setup", ignoreCase = true) ->
            "Una elección guardada elegida necesita un nombre actualizado antes de continuar con la configuración."
        text.matches(Regex("\\d+ selected saved choices need current display names before setup.*", RegexOption.IGNORE_CASE)) ->
            text.replace(
                Regex("selected saved choices need current display names before setup can continue\\."),
                "elecciones guardadas elegidas necesitan nombres actualizados antes de continuar con la configuración."
            )
        text.startsWith("1 selected saved choice needs a current display name", ignoreCase = true) ->
            "Una elección guardada elegida necesita un nombre actualizado antes de poder comprobarla."
        text.matches(Regex("\\d+ selected saved choices need current display names.*", RegexOption.IGNORE_CASE)) ->
            text.replace(
                Regex("selected saved choices need current display names before they can be checked\\."),
                "elecciones guardadas elegidas necesitan nombres actualizados antes de poder comprobarlas."
            )
        text.startsWith("1 other saved choice is hidden", ignoreCase = true) ->
            "Otra elección guardada está oculta hasta que su nombre esté disponible."
        text.matches(Regex("\\d+ other saved choices are hidden.*", RegexOption.IGNORE_CASE)) ->
            text.replace(
                Regex("other saved choices are hidden until their display names are available\\."),
                "otras elecciones guardadas están ocultas hasta que sus nombres estén disponibles."
            )
        text.contains("Scan & compare", ignoreCase = true) ->
            text.replace("Scan & compare", "Escanear y comparar", ignoreCase = true)
                .replace("Is this a good price?", "¿Es un buen precio?", ignoreCase = true)
        text.contains("Is this a good price?", ignoreCase = true) ->
            text.replace("Is this a good price?", "¿Es un buen precio?", ignoreCase = true)
        text.startsWith("Clear ", ignoreCase = true) && text.endsWith(" as usual store", ignoreCase = true) ->
            "Borrar " + text.substring(6, text.length - " as usual store".length) + " como tienda habitual"
        text.startsWith("Use ", ignoreCase = true) && text.endsWith(" as usual store", ignoreCase = true) ->
            "Usar " + text.substring(4, text.length - " as usual store".length) + " como tienda habitual"
        text.startsWith("Promotion: ", ignoreCase = true) ->
            "Promoción: " + text.substringAfter(": ")
        else -> text
    }
}
