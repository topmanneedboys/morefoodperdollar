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
        text.equals("Ready to check a price", ignoreCase = true) ->
            "Listo para comprobar un precio"
        text.equals(
            "Enter one product, its package quantity, and the price you observed.",
            ignoreCase = true
        ) -> "Ingresá un producto, la cantidad de su envase y el precio que observaste."
        text.equals("Add the package and price", ignoreCase = true) ->
            "Agregá el envase y el precio"
        text.equals(
            "Complete this saved product with its exact package quantity and the price you observed.",
            ignoreCase = true
        ) -> "Completá este producto guardado con la cantidad exacta del envase y el precio que observaste."
        text.equals(
            "This exact check was saved to private price memory on this device.",
            ignoreCase = true
        ) -> "Este control exacto se guardó en la memoria privada de precios de este dispositivo."
        text.equals(
            "Private price memory is unavailable. Clear it to recover.",
            ignoreCase = true
        ) -> "La memoria privada de precios no está disponible. Borrala para recuperarla."
        text.equals(
            "The check is ready, but it could not be saved to private price memory.",
            ignoreCase = true
        ) -> "El control está listo, pero no se pudo guardar en la memoria privada de precios."
        text.equals("Private price memory was cleared from this device.", ignoreCase = true) ->
            "La memoria privada de precios se borró de este dispositivo."
        text.equals("Private price memory could not be cleared.", ignoreCase = true) ->
            "No se pudo borrar la memoria privada de precios."
        text.equals("Opening the on-device barcode scanner…", ignoreCase = true) ->
            "Abriendo el escáner de códigos de barras del dispositivo…"
        text.equals("Barcode scan cancelled.", ignoreCase = true) ->
            "Se canceló el escaneo del código de barras."
        text.equals(
            "The barcode scanner could not read a product code.",
            ignoreCase = true
        ) -> "El escáner de códigos de barras no pudo leer un código de producto."
        text.equals(
            "Looking for an exact name in the on-device catalog…",
            ignoreCase = true
        ) -> "Buscando un nombre exacto en el catálogo del dispositivo…"
        text.equals("The on-device catalog could not be checked.", ignoreCase = true) ->
            "No se pudo consultar el catálogo del dispositivo."
        text.startsWith("No exact product name was found for barcode ", ignoreCase = true) ->
            "No se encontró un nombre exacto de producto para el código de barras " +
                text.substringAfter("barcode ", missingDelimiterValue = "")
        text.equals("Choose the product name that matches the package.", ignoreCase = true) ->
            "Elegí el nombre de producto que coincide con el envase."
        text.equals("Barcode suggestion was not applied.", ignoreCase = true) ->
            "No se aplicó la sugerencia del código de barras."
        text.equals(
            "That catalog name cannot be used. Enter the product name manually.",
            ignoreCase = true
        ) -> "No se puede usar ese nombre del catálogo. Ingresá el nombre del producto manualmente."
        text.equals("Complete the product details", ignoreCase = true) ->
            "Completá los datos del producto"
        text.equals(
            "The barcode suggested an editable name only. Add the exact package quantity and the price you observed.",
            ignoreCase = true
        ) -> "El código de barras sugirió solo un nombre editable. Agregá la cantidad exacta del envase y el precio que observaste."
        text.contains(" suggested from barcode ", ignoreCase = true) &&
            text.endsWith(". Add the package and price yourself.", ignoreCase = true) -> {
            val suggestion = text.substringBefore(" suggested from barcode ", missingDelimiterValue = text)
            val barcode = text.substringAfter(" suggested from barcode ", missingDelimiterValue = "")
                .removeSuffix(". Add the package and price yourself.")
            "Sugerencia de catálogo: $suggestion (código de barras $barcode). Agregá el envase y el precio manualmente."
        }
        text.equals("Enter one product", ignoreCase = true) -> "Ingresá un producto"
        text.equals(
            "Add the product name, package quantity, and price before checking it.",
            ignoreCase = true
        ) -> "Agregá el nombre del producto, la cantidad del envase y el precio antes de comprobarlo."
        text.equals("Product details are too long", ignoreCase = true) ->
            "Los datos del producto son demasiado largos"
        text.equals("The product entry is too long", ignoreCase = true) ->
            "La entrada del producto es demasiado larga"
        text.equals("Enter one product at a time", ignoreCase = true) ->
            "Ingresá un producto por vez"
        text.equals("Shorten the product details and try again.", ignoreCase = true) ->
            "Acortá los datos del producto y probá de nuevo."
        text.equals("Use one product block for this question.", ignoreCase = true) ->
            "Usá un solo bloque de producto para esta consulta."
        text.equals("This product could not be checked safely", ignoreCase = true) ->
            "No se pudo comprobar este producto de forma segura"
        text.equals("Check the product details and try again.", ignoreCase = true) ->
            "Revisá los datos del producto y probá de nuevo."
        text.equals("This product needs clearer information", ignoreCase = true) ->
            "Este producto necesita información más clara"
        text.equals(
            "Include one clear currency, price, package quantity, and any exact promotion terms.",
            ignoreCase = true
        ) -> "Incluí una moneda, un precio, una cantidad de envase y las condiciones exactas de cualquier promoción."
        text.equals("Need an exact price and package", ignoreCase = true) ->
            "Se necesita un precio y envase exactos"
        text.equals("Need a clear product name", ignoreCase = true) ->
            "Se necesita un nombre de producto claro"
        text.equals(
            "Add a short product name, package quantity, and price, then try again.",
            ignoreCase = true
        ) -> "Agregá un nombre breve, la cantidad del envase y el precio, y probá de nuevo."
        text.equals("This product could not be remembered safely", ignoreCase = true) ->
            "No se pudo guardar este producto de forma segura"
        text.equals("Is this a good price?", ignoreCase = true) -> "¿Es un buen precio?"
        text.equals("Member price", ignoreCase = true) -> "Precio de miembro"
        text.equals("Price you entered", ignoreCase = true) -> "Precio ingresado"
        text.equals("Price checked", ignoreCase = true) -> "Precio comprobado"
        text.equals(
            "This answer uses your private history only; it is not a live store claim.",
            ignoreCase = true
        ) -> "Esta respuesta usa solo tu historial privado; no es una afirmación de una tienda en vivo."
        text.equals(
            "Not enough evidence yet to know whether this is unusually good.",
            ignoreCase = true
        ) -> "Todavía no hay evidencia suficiente para saber si es especialmente conveniente."
        text.equals(
            "No matching personal history is available for this exact package yet.",
            ignoreCase = true
        ) -> "Todavía no hay historial personal coincidente para este envase exacto."
        text.equals("Below your last remembered price", ignoreCase = true) ->
            "Por debajo de tu último precio recordado"
        text.equals("Matches your last remembered price", ignoreCase = true) ->
            "Coincide con tu último precio recordado"
        text.equals("Above your last remembered price", ignoreCase = true) ->
            "Por encima de tu último precio recordado"
        text.equals("Below your remembered range", ignoreCase = true) ->
            "Por debajo de tu rango recordado"
        text.equals("Within your remembered range", ignoreCase = true) ->
            "Dentro de tu rango recordado"
        text.equals("Above your remembered range", ignoreCase = true) ->
            "Por encima de tu rango recordado"
        text.equals(
            "This matches your last matching observation. ValuePilot cannot call it unusually good from this evidence alone.",
            ignoreCase = true
        ) -> "Coincide con tu última observación coincidente. Con esta evidencia, ValuePilot no puede considerarlo especialmente conveniente."
        text.equals(
            "This falls inside your matching personal history. ValuePilot does not have enough evidence to call it unusually good.",
            ignoreCase = true
        ) -> "Está dentro de tu historial personal coincidente. ValuePilot no tiene evidencia suficiente para considerarlo especialmente conveniente."
        text.equals(
            "Not live store pricing. No matching personal history yet; this answer uses this exact price only.",
            ignoreCase = true
        ) -> "No es un precio de tienda en vivo. Todavía no hay historial personal coincidente; esta respuesta usa únicamente este precio exacto."
        text.equals(
            "Not live store pricing. Personal history matches exact package quantity, currency, price basis, and promotion terms.",
            ignoreCase = true
        ) -> "No es un precio de tienda en vivo. El historial personal coincide en la cantidad exacta del envase, la moneda, la base del precio y las condiciones de promoción."
        text.equals(
            "No personal history for this exact package yet; ValuePilot will remember this comparison.",
            ignoreCase = true
        ) -> "Todavía no hay historial personal para este envase exacto; ValuePilot recordará esta comparación."
        text.matches(
            Regex("Personal history: \\d+ observations? · lowest .+ · highest .+ · latest .+\\.", RegexOption.IGNORE_CASE)
        ) -> localizeGoodPriceHistory(text)
        text.startsWith("This is about ", ignoreCase = true) -> localizeGoodPriceRateGuidance(text)
        text.startsWith("This is lower per unit than ", ignoreCase = true) -> localizeGoodPriceRateGuidance(text)
        text.startsWith("This is higher per unit than ", ignoreCase = true) -> localizeGoodPriceRateGuidance(text)
        text.equals("Add at least two products", ignoreCase = true) ->
            "Agregá al menos dos productos"
        text.equals(
            "Enter two or more products before comparing exact value.",
            ignoreCase = true
        ) -> "Ingresá dos o más productos antes de comparar el valor exacto."
        text.equals("Confirm comparable products", ignoreCase = true) ->
            "Confirmá que los productos sean comparables"
        text.equals(
            "Confirm that every product is a like-for-like alternative before comparing exact value.",
            ignoreCase = true
        ) -> "Confirmá que cada producto sea una alternativa comparable antes de comparar el valor exacto."
        text.equals("Input is too long", ignoreCase = true) -> "La entrada es demasiado larga"
        text.equals("A product entry is too long", ignoreCase = true) ->
            "La entrada de un producto es demasiado larga"
        text.equals("Shorten that product entry and try again.", ignoreCase = true) ->
            "Acortá esa entrada de producto y probá de nuevo."
        text.equals("Too many products", ignoreCase = true) -> "Demasiados productos"
        text.startsWith("Compare up to ", ignoreCase = true) &&
            text.endsWith(" products at a time.", ignoreCase = true) ->
            "Compará hasta " + text.substringAfter("Compare up to ").removeSuffix(" products at a time.") + " productos por vez."
        text.equals("Some products need clearer information", ignoreCase = true) ->
            "Algunos productos necesitan información más clara"
        text.equals(
            "Check currency, promotion, price, and package details, then try again.",
            ignoreCase = true
        ) -> "Revisá la moneda, la promoción, el precio y los datos del envase, y probá de nuevo."
        text.equals("Best value tie", ignoreCase = true) -> "Empate en mejor valor"
        text.equals("Best value", ignoreCase = true) -> "Mejor valor"
        text.equals("Need more exact information", ignoreCase = true) ->
            "Se necesita más información exacta"
        text.equals("Cannot rank these together", ignoreCase = true) ->
            "No se pueden ordenar juntos"
        text.equals("Product names needed", ignoreCase = true) ->
            "Se necesitan nombres de productos"
        text.equals(
            "Some product names cannot be shown safely, so no winner is displayed.",
            ignoreCase = true
        ) -> "Algunos nombres de productos no se pueden mostrar de forma segura, por eso no se muestra un ganador."
        text.equals(
            "These options tie at the lowest exact unit price. Lower exact unit price wins within this comparison.",
            ignoreCase = true
        ) -> "Estas opciones empatan en el menor precio unitario exacto. Dentro de esta comparación gana el menor precio unitario exacto."
        text.equals("Lower exact unit price wins within this comparison.", ignoreCase = true) ->
            "Dentro de esta comparación gana el menor precio unitario exacto."
        text.startsWith("Lower exact unit price wins within this comparison. Best value is about ", ignoreCase = true) ->
            localizeCompareBestValueGuidance(text)
        text.equals(
            "Add at least two products with an exact current price and package quantity.",
            ignoreCase = true
        ) -> "Agregá al menos dos productos con precio actual y cantidad de envase exactos."
        text.equals(
            "Add at least two products with an exact member price and package quantity. Current prices are not used as substitutes.",
            ignoreCase = true
        ) -> "Agregá al menos dos productos con precio de miembro y cantidad de envase exactos. Los precios actuales no se usan como reemplazo."
        text.equals("These prices use different currencies.", ignoreCase = true) ->
            "Estos precios usan monedas diferentes."
        text.equals("These products use incompatible quantity units.", ignoreCase = true) ->
            "Estos productos usan unidades de cantidad incompatibles."
        text.equals(
            "These products use different currencies and quantity units.",
            ignoreCase = true
        ) -> "Estos productos usan monedas y unidades de cantidad diferentes."
        text.equals("Not part of this comparison", ignoreCase = true) ->
            "No forma parte de esta comparación"
        text.equals("Package quantity needed", ignoreCase = true) ->
            "Se necesita la cantidad del envase"
        text.equals("Member price unavailable", ignoreCase = true) ->
            "Precio de miembro no disponible"
        text.equals("Selected price unavailable", ignoreCase = true) ->
            "Precio elegido no disponible"
        text.equals("Price must be greater than zero", ignoreCase = true) ->
            "El precio debe ser mayor que cero"
        text.equals("Unit rate cannot be represented safely", ignoreCase = true) ->
            "No se puede representar el precio unitario de forma segura"
        text.equals("Values are too large to compare safely", ignoreCase = true) ->
            "Los valores son demasiado grandes para compararlos de forma segura"
        text.matches(Regex("\\d+ product names? could not be shown safely\\.", RegexOption.IGNORE_CASE)) ->
            localizeCompareOmittedNotice(text)
        text.equals(
            "The comparison couldn't be completed. Check the product details and try again.",
            ignoreCase = true
        ) -> "No se pudo completar la comparación. Revisá los datos de los productos y probá de nuevo."
        text.startsWith("Remembered ", ignoreCase = true) &&
            text.contains(" product prices privately on this device.", ignoreCase = true) ->
            localizeCompareMemoryNotice(text)
        text.equals(
            "The comparison is ready, but private history couldn't be updated. Nothing was sent online.",
            ignoreCase = true
        ) -> "La comparación está lista, pero no se pudo actualizar el historial privado. No se envió nada por internet."
        text.equals("Observed price confirmation", ignoreCase = true) ->
            "Confirmación de precio observado"
        text.equals("Confirmation details complete", ignoreCase = true) ->
            "Datos de confirmación completos"
        text.equals("Confirmation details needed", ignoreCase = true) ->
            "Faltan datos de confirmación"
        text.equals(
            "All non-byte confirmation details are present. Proof reading, retention, and confirmation validation happen separately.",
            ignoreCase = true
        ) -> "Todos los datos de confirmación, excepto el archivo, están presentes. La lectura, retención y validación de la evidencia ocurren por separado."
        text.equals(
            "Complete the remaining non-byte details before proof can be submitted for confirmation.",
            ignoreCase = true
        ) -> "Completá los datos restantes antes de enviar la evidencia para su confirmación."
        text.equals(
            "Draft completeness does not mean the confirmation is accepted. Observed prices are not retailer-confirmed current prices.",
            ignoreCase = true
        ) -> "Completar el borrador no significa que la confirmación haya sido aceptada. Los precios observados no son precios actuales confirmados por minoristas."
        text.equals("Proof reference", ignoreCase = true) -> "Referencia de evidencia"
        text.equals("Proof type", ignoreCase = true) -> "Tipo de evidencia"
        text.equals("Observation reference", ignoreCase = true) -> "Referencia de observación"
        text.equals("Product GTIN", ignoreCase = true) -> "GTIN del producto"
        text.equals("Product name", ignoreCase = true) -> "Nombre del producto"
        text.equals("Observed price", ignoreCase = true) -> "Precio observado"
        text.equals("Exact store scope", ignoreCase = true) -> "Alcance exacto de tienda"
        text.equals("Observation time", ignoreCase = true) -> "Hora de observación"
        text.equals("Confirmation reference", ignoreCase = true) -> "Referencia de confirmación"
        text.equals("Confirmation time", ignoreCase = true) -> "Hora de confirmación"
        text.equals("Confirm observed price", ignoreCase = true) -> "Confirmar precio observado"
        text.equals(
            "Confirmation is available only while this draft is open.",
            ignoreCase = true
        ) -> "La confirmación solo está disponible mientras este borrador esté abierto."
        text.equals(
            "Not submitted. Complete the required fields and proof, then try again.",
            ignoreCase = true
        ) -> "No se envió. Completá los campos obligatorios y la evidencia, y probá de nuevo."
        text.equals(
            "Checking the confirmation and retaining the proof safely on this device.",
            ignoreCase = true
        ) -> "Comprobando la confirmación y guardando la evidencia de forma segura en este dispositivo."
        text.equals(
            "A previous confirmation completed, but the current draft changed. Confirm again to submit the current values.",
            ignoreCase = true
        ) -> "Una confirmación anterior terminó, pero el borrador actual cambió. Confirmá de nuevo para enviar los valores actuales."
        text.equals(
            "The previous attempt finished, but the current draft changed. Review it and confirm the current values.",
            ignoreCase = true
        ) -> "El intento anterior terminó, pero el borrador actual cambió. Revisalo y confirmá los valores actuales."
        text.equals("When the required fields and proof are ready, confirm the observed price.", ignoreCase = true) ->
            "Cuando los campos obligatorios y la evidencia estén listos, confirmá el precio observado."
        text.equals("Confirmation could not be completed. Try again.", ignoreCase = true) ->
            "No se pudo completar la confirmación. Probá de nuevo."
        text.equals(
            "Observed price confirmed. The proof was already retained and verified on this device.",
            ignoreCase = true
        ) -> "Precio observado confirmado. La evidencia ya estaba guardada y verificada en este dispositivo."
        text.equals(
            "Observed price confirmed. The proof was retained and verified on this device.",
            ignoreCase = true
        ) -> "Precio observado confirmado. La evidencia se guardó y verificó en este dispositivo."
        text.equals(
            "The selected proof could not be accepted. Choose valid proof and try again.",
            ignoreCase = true
        ) -> "No se pudo aceptar la evidencia elegida. Seleccioná una válida y probá de nuevo."
        text.equals(
            "The confirmation details were not accepted. Review them and try again.",
            ignoreCase = true
        ) -> "No se aceptaron los datos de confirmación. Revisalos y probá de nuevo."
        text.equals(
            "The proof could not be safely retained on this device. Try again.",
            ignoreCase = true
        ) -> "No se pudo guardar la evidencia de forma segura en este dispositivo. Probá de nuevo."
        text.equals("Enter the observed price.", ignoreCase = true) -> "Ingresá el precio observado."
        text.equals("Enter the currency code.", ignoreCase = true) -> "Ingresá el código de moneda."
        text.equals(
            "Use a valid uppercase ISO currency code, such as ARS or USD.",
            ignoreCase = true
        ) -> "Usá un código de moneda ISO válido en mayúsculas, como ARS o USD."
        text.equals("That currency precision is not supported.", ignoreCase = true) ->
            "Esa precisión de moneda no es compatible."
        text.equals(
            "Use digits and an optional decimal point, such as 12.50.",
            ignoreCase = true
        ) -> "Usá dígitos y, opcionalmente, un punto decimal, por ejemplo 12.50."
        text.equals("That price is outside the supported range.", ignoreCase = true) ->
            "Ese precio está fuera del rango compatible."
        text.equals("Enter the observation date.", ignoreCase = true) -> "Ingresá la fecha de observación."
        text.equals("Enter the observation time.", ignoreCase = true) -> "Ingresá la hora de observación."
        text.equals("Enter the UTC offset.", ignoreCase = true) -> "Ingresá el desfase UTC."
        text.equals("Use YYYY-MM-DD.", ignoreCase = true) -> "Usá YYYY-MM-DD."
        text.equals("Use HH:MM or HH:MM:SS in 24-hour time.", ignoreCase = true) ->
            "Usá HH:MM o HH:MM:SS en formato de 24 horas."
        text.equals("Use Z or an explicit offset such as -03:00.", ignoreCase = true) ->
            "Usá Z o un desfase explícito, por ejemplo -03:00."
        text.equals("Enter a real calendar date and time.", ignoreCase = true) ->
            "Ingresá una fecha y hora reales del calendario."
        text.equals("Enter a proof reference.", ignoreCase = true) -> "Ingresá una referencia de evidencia."
        text.equals("Choose Receipt or Price tag.", ignoreCase = true) ->
            "Elegí Ticket o Etiqueta de precio."
        text.equals("package quantity unknown", ignoreCase = true) ->
            "cantidad del envase desconocida"
        text.equals("Price evidence unknown", ignoreCase = true) ->
            "Evidencia de precio desconocida"
        text.equals("Current qualified price preview", ignoreCase = true) ->
            "Vista previa de precio calificado actual"
        text.matches(Regex("\\d+ selected products?", RegexOption.IGNORE_CASE)) ->
            localizeSelectedProductCount(text)
        text.equals(
            "Plan all products in one authoritative request. Missing or stale evidence stays unknown.",
            ignoreCase = true
        ) -> "Planificá todos los productos en una solicitud autorizada. La evidencia faltante o vencida queda desconocida."
        text.equals(
            "Search each selected product again to attach fresh qualified price evidence.",
            ignoreCase = true
        ) -> "Buscá de nuevo cada producto elegido para adjuntar evidencia de precio reciente y calificada."
        text.equals(
            "Prices are session-only evidence from Argentina SEPA / Precios Claros. Availability is always UNKNOWN.",
            ignoreCase = true
        ) -> "Los precios son evidencia de sesión de Argentina SEPA / Precios Claros. La disponibilidad siempre es DESCONOCIDA."
        text.equals("Plan this basket", ignoreCase = true) -> "Planificar esta canasta"
        text.equals("Open Search", ignoreCase = true) -> "Abrir búsqueda"
        text.equals("No current price plan found", ignoreCase = true) ->
            "No se encontró un plan de precios actual"
        text.equals("No exact current price evidence was returned.", ignoreCase = true) ->
            "No se devolvió evidencia exacta de precio actual."
        text.equals("Availability unknown", ignoreCase = true) -> "Disponibilidad desconocida"
        text.equals("Current listed-price basket", ignoreCase = true) ->
            "Canasta de precios publicados actuales"
        text.equals("Basket price coverage is incomplete", ignoreCase = true) ->
            "La cobertura de precios de la canasta está incompleta"
        text.startsWith("Listed total · ", ignoreCase = true) ->
            "Total publicado · " + text.substringAfter("· ")
        text.startsWith("Known subtotal · ", ignoreCase = true) ->
            "Subtotal conocido · " + text.substringAfter("· ")
        text.matches(Regex("\\d+ of \\d+ selected items have exact price evidence", RegexOption.IGNORE_CASE)) ->
            localizeBasketCoverage(text)
        text.startsWith("Argentina SEPA / Precios Claros release ", ignoreCase = true) ->
            localizeBasketNotice(text)
        text.startsWith("Trip cost · ARS ", ignoreCase = true) -> localizeTripCost(text)
        text.equals("The qualified Argentina release is unavailable.", ignoreCase = true) ->
            "La publicación calificada de Argentina no está disponible."
        text.equals("The qualified Argentina release is not authorized for this request.", ignoreCase = true) ->
            "La publicación calificada de Argentina no está autorizada para esta solicitud."
        text.equals("The qualified release has no id.", ignoreCase = true) ->
            "La publicación calificada no tiene identificador."
        text.equals("The qualified release has no date.", ignoreCase = true) ->
            "La publicación calificada no tiene fecha."
        text.equals("The basket plan could not be refreshed.", ignoreCase = true) ->
            "No se pudo actualizar el plan de la canasta."
        text.startsWith("Enter a valid ", ignoreCase = true) ->
            localizeTripInputError(text)
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

private fun localizeGoodPriceHistory(text: String): String {
    val match =
        Regex(
            "Personal history: (\\d+) observations? · lowest (.+) · highest (.+) · latest (.+)\\.",
            RegexOption.IGNORE_CASE
        ).matchEntire(text) ?: return text
    val count = match.groupValues[1].toIntOrNull() ?: return text
    val noun = if (count == 1) "observación" else "observaciones"
    return "Historial personal: $count $noun · mínimo ${match.groupValues[2]} · máximo ${match.groupValues[3]} · última ${match.groupValues[4]}."
}

private fun localizeGoodPriceRateGuidance(text: String): String {
    val aboutMatch =
        Regex(
            "^This is about ([0-9]+(?:\\.[0-9]+)?)% (lower|higher) per unit than (.+?)\\.(?: (.+))?$",
            RegexOption.IGNORE_CASE
        ).matchEntire(text)
    if (aboutMatch != null) {
        val percentage = aboutMatch.groupValues[1].replace('.', ',')
        val direction = if (aboutMatch.groupValues[2].equals("lower", ignoreCase = true)) "más bajo" else "más alto"
        val reference = localizeGoodPriceReference(aboutMatch.groupValues[3])
        val suffix = aboutMatch.groupValues[4].takeIf { it.isNotBlank() }?.let(::localizeGoodPriceSentence)
        return buildString {
            append("Es aproximadamente un ")
            append(percentage)
            append(" % ")
            append(direction)
            append(" por unidad que ")
            append(reference)
            append('.')
            if (suffix != null) {
                append(' ')
                append(suffix)
            }
        }
    }

    val plainMatch =
        Regex(
            "^This is (lower|higher) per unit than (.+?)\\.(?: (.+))?$",
            RegexOption.IGNORE_CASE
        ).matchEntire(text)
    if (plainMatch != null) {
        val direction = if (plainMatch.groupValues[1].equals("lower", ignoreCase = true)) "más bajo" else "más alto"
        val reference = localizeGoodPriceReference(plainMatch.groupValues[2])
        val suffix = plainMatch.groupValues[3].takeIf { it.isNotBlank() }?.let(::localizeGoodPriceSentence)
        return buildString {
            append("Es ")
            append(direction)
            append(" por unidad que ")
            append(reference)
            append('.')
            if (suffix != null) {
                append(' ')
                append(suffix)
            }
        }
    }
    return text
}

private fun localizeGoodPriceReference(reference: String): String =
    reference
        .replace("your last matching observation", "tu última observación coincidente", ignoreCase = true)
        .replace("your previous personal low", "tu mínimo personal anterior", ignoreCase = true)
        .replace("your previous personal high", "tu máximo personal anterior", ignoreCase = true)

private fun localizeGoodPriceSentence(sentence: String): String =
    sentence
        .replace(
            "That is useful personal context, not a guarantee of the cheapest price.",
            "Es un contexto personal útil, no una garantía del precio más bajo.",
            ignoreCase = true
        )
        .replace(
            "It looks better for you, but it is not a live-market guarantee.",
            "Te resulta más conveniente, pero no es una garantía del mercado en vivo.",
            ignoreCase = true
        )
        .replace(
            "Check the package and promotion details before deciding.",
            "Revisá los datos del envase y la promoción antes de decidir.",
            ignoreCase = true
        )
        .replace(
            "ValuePilot cannot call it unusually good from this evidence alone.",
            "Con esta evidencia, ValuePilot no puede considerarlo especialmente conveniente.",
            ignoreCase = true
        )

private fun localizeCompareBestValueGuidance(text: String): String {
    val percentage =
        Regex("Best value is about ([0-9]+(?:\\.[0-9]+)?)% lower per unit than the next-best option\\.", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace('.', ',')
            ?: return text
    return "Dentro de esta comparación gana el menor precio unitario exacto. El mejor valor es aproximadamente un $percentage % más bajo por unidad que la siguiente opción."
}

private fun localizeCompareMemoryNotice(text: String): String {
    val count =
        Regex("Remembered (\\d+) product prices privately on this device\\. This is comparison history, not live store pricing\\.", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?: return text
    val noun = if (count == "1") "precio de producto" else "precios de productos"
    return "Se guardaron $count $noun de forma privada en este dispositivo. Es historial de comparación, no precios de tiendas en vivo."
}

private fun localizeCompareOmittedNotice(text: String): String {
    val count = Regex("^(\\d+)").find(text)?.groupValues?.getOrNull(1) ?: return text
    val noun = if (count == "1") "nombre de producto" else "nombres de productos"
    return "$count $noun no se pudieron mostrar de forma segura."
}

private fun localizeSelectedProductCount(text: String): String {
    val count = Regex("^(\\d+)").find(text)?.groupValues?.getOrNull(1) ?: return text
    return "$count " + if (count == "1") "producto elegido" else "productos elegidos"
}

private fun localizeBasketCoverage(text: String): String {
    val match = Regex("^(\\d+) of (\\d+) selected items have exact price evidence", RegexOption.IGNORE_CASE)
        .matchEntire(text) ?: return text
    return "${match.groupValues[1]} de ${match.groupValues[2]} productos elegidos tienen evidencia exacta de precio"
}

private fun localizeBasketNotice(text: String): String {
    val prefix = "Argentina SEPA / Precios Claros release "
    val release = text.substringAfter(prefix, missingDelimiterValue = "").trim()
    if (release.isBlank()) return text
    val localizedRelease = when {
        ". Published prices do not establish live shelf inventory; availability remains unknown." in release ->
            release.replace(
                ". Published prices do not establish live shelf inventory; availability remains unknown.",
                ". Los precios publicados no establecen inventario real de góndola; la disponibilidad queda desconocida."
            )
        "; unknown prices remain unknown." in release ->
            release.replace("; unknown prices remain unknown.", "; los precios desconocidos siguen desconocidos.")
        else -> release
    }
    return "Publicación de Argentina SEPA / Precios Claros $localizedRelease".let { localized ->
        if (localized.contains(" Missing: ")) {
            localized.replace(" Missing: ", " Faltan: ")
        } else {
            localized
        }
    }
}

private fun localizeTripCost(text: String): String {
    val match =
        Regex(
            "Trip cost · ARS (.+) \\(fuel ARS (.+) · tolls ARS (.+) · parking ARS (.+)\\)",
            RegexOption.IGNORE_CASE
        ).matchEntire(text) ?: return text
    return "Costo del viaje · ARS ${match.groupValues[1]} (combustible ARS ${match.groupValues[2]} · peajes ARS ${match.groupValues[3]} · estacionamiento ARS ${match.groupValues[4]})"
}

private fun localizeTripInputError(text: String): String =
    text.replace("Enter a valid ", "Ingresá un ", ignoreCase = true)
        .replace("fuel consumption", "consumo de combustible", ignoreCase = true)
        .replace("fuel price", "precio del combustible", ignoreCase = true)
        .replace("tolls", "peajes", ignoreCase = true)
        .replace("parking", "estacionamiento", ignoreCase = true)
        .replace("driving distance", "distancia de manejo", ignoreCase = true)
