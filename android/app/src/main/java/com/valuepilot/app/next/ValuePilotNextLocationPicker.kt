package com.valuepilot.app.next

import android.app.Activity
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.valuepilot.app.ArgentinaUserSearchArea
import java.util.Locale

internal fun valuePilotNextAreaFromAddress(address: Address): ArgentinaUserSearchArea? {
    if (!address.countryCode.equals("AR", ignoreCase = true) || !address.hasLatitude() || !address.hasLongitude()) return null
    return runCatching {
        val label = address.getAddressLine(0)?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(address.locality, address.adminArea, address.countryName).joinToString(", ")
        ArgentinaUserSearchArea(label.take(256), address.latitude.toString(), address.longitude.toString())
    }.getOrNull()
}

/** User-initiated address resolution. No GPS permission, tracking or invented addresses. */
internal class ValuePilotNextLocationPicker(
    private val activity: Activity,
    private val text: (String, String) -> String,
    private val selected: (ArgentinaUserSearchArea) -> Unit
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0L
    private var dialog: AlertDialog? = null
    private var closed = false

    fun open() {
        if (closed || activity.isFinishing) return
        generation++
        dialog?.dismiss()
        val field = EditText(activity).apply {
            hint = text("Dirección, ciudad y provincia", "Address, city and province")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
            maxLines = 3
            filters = arrayOf(android.text.InputFilter.LengthFilter(160))
        }
        dialog = AlertDialog.Builder(activity)
            .setTitle(text("Tu ubicación en Argentina", "Your location in Argentina"))
            .setMessage(text("Elegí desde dónde comparar precios y distancias.", "Choose where to compare prices and distances from."))
            .setView(field)
            .setPositiveButton(text("Buscar", "Find"), null)
            .setNeutralButton(text("Coordenadas", "Coordinates")) { _, _ -> coordinates() }
            .setNegativeButton(text("Cancelar", "Cancel"), null).create()
        dialog?.setOnShowListener {
            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val query = field.text.toString().trim()
                if (query.length < 3) field.error = text("Ingresá una dirección o ciudad.", "Enter an address or city.")
                else resolve(query)
            }
        }
        dialog?.show()
    }

    private fun resolve(query: String) {
        if (Build.VERSION.SDK_INT < 33 || !Geocoder.isPresent()) {
            Toast.makeText(activity, text("No se pudo consultar direcciones. Podés ingresar coordenadas.",
                "Address lookup is unavailable. You can enter coordinates."), Toast.LENGTH_LONG).show()
            coordinates()
            return
        }
        val request = ++generation
        dialog?.dismiss()
        val pending = AlertDialog.Builder(activity)
            .setMessage(text("Buscando ubicación…", "Finding location…"))
            .setNegativeButton(text("Cancelar", "Cancel")) { _, _ -> generation++ }
            .create().also { it.setOnCancelListener { generation++ }; it.show() }
        dialog = pending
        fun finish(addresses: List<Address>) {
            handler.post {
                if (closed || generation != request || activity.isFinishing || activity.isDestroyed) return@post
                generation++
                pending.dismiss()
                val areas = addresses.mapNotNull(::valuePilotNextAreaFromAddress)
                    .distinctBy { it.latitude to it.longitude }.take(5)
                if (areas.isEmpty()) {
                    Toast.makeText(activity, text("No encontramos una ubicación en Argentina. Probá con ciudad y provincia.",
                        "No Argentina location found. Try a city and province."), Toast.LENGTH_LONG).show()
                    open()
                } else {
                    dialog = AlertDialog.Builder(activity)
                        .setTitle(text("Confirmá tu ubicación", "Confirm your location"))
                        .setItems(areas.map { it.label }.toTypedArray()) { _, index -> selected(areas[index]) }
                        .setNegativeButton(text("Cancelar", "Cancel"), null).show()
                }
            }
        }
        handler.postDelayed({
            if (!closed && generation == request) {
                generation++
                pending.dismiss()
                Toast.makeText(activity, text("La consulta tardó demasiado. Probá de nuevo o ingresá coordenadas.",
                    "The lookup timed out. Retry or enter coordinates."), Toast.LENGTH_LONG).show()
                open()
            }
        }, 20_000)
        try {
            Geocoder(activity.applicationContext, Locale.forLanguageTag("es-AR")).getFromLocationName(
                "$query, Argentina", 5, -56.0, -74.0, -21.0, -53.0,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = finish(addresses)
                    override fun onError(errorMessage: String?) = finish(emptyList())
                })
        } catch (_: Exception) { finish(emptyList()) }
    }

    private fun coordinates() {
        generation++
        dialog?.dismiss()
        fun field(hintText: String) = EditText(activity).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            filters = arrayOf(android.text.InputFilter.LengthFilter(24))
        }
        val label = EditText(activity).apply {
            hint = text("Nombre del lugar", "Place name")
            filters = arrayOf(android.text.InputFilter.LengthFilter(120))
        }
        val latitude = field(text("Latitud", "Latitude"))
        val longitude = field(text("Longitud", "Longitude"))
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            listOf(label, latitude, longitude).forEach(::addView)
        }
        dialog = AlertDialog.Builder(activity).setTitle(text("Ubicación en Argentina", "Location in Argentina"))
            .setView(form).setPositiveButton(text("Usar ubicación", "Use location"), null)
            .setNegativeButton(text("Cancelar", "Cancel"), null).create()
        dialog?.setOnShowListener {
            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val area = runCatching { ArgentinaUserSearchArea(label.text.toString().trim(),
                    latitude.text.toString().trim().replace(',', '.'), longitude.text.toString().trim().replace(',', '.')) }.getOrNull()
                if (area == null) label.error = text("Revisá el nombre y las coordenadas de Argentina.", "Check the place name and Argentina coordinates.")
                else { dialog?.dismiss(); selected(area) }
            }
        }
        dialog?.show()
    }

    override fun close() {
        closed = true
        generation++
        handler.removeCallbacksAndMessages(null)
        dialog?.dismiss()
        dialog = null
    }
}
