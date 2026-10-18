package com.valuepilot.app

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
import java.util.Locale

/**
 * User-initiated Argentina Search origin picker.
 *
 * It never requests device location, never tracks the shopper, and never invents
 * coordinates. The shopper types an address or exact coordinates and confirms
 * one of the returned candidates.
 */
internal class UserSearchAreaPicker(
    private val activity: Activity,
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
            hint = "Address, city and province"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
            maxLines = 3
            filters = arrayOf(android.text.InputFilter.LengthFilter(160))
        }
        dialog = AlertDialog.Builder(activity)
            .setTitle("Search area in Argentina")
            .setMessage("Choose where ValuePilot compares prices and distances from.")
            .setView(field)
            .setPositiveButton("Find", null)
            .setNeutralButton("Coordinates") { _, _ -> coordinates() }
            .setNegativeButton("Cancel", null)
            .create()
        dialog?.setOnShowListener {
            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val query = field.text.toString().trim()
                if (query.length < 3) {
                    field.error = "Enter an address or city."
                } else {
                    resolve(query)
                }
            }
        }
        dialog?.show()
    }

    private fun resolve(query: String) {
        if (Build.VERSION.SDK_INT < 33 || !Geocoder.isPresent()) {
            Toast.makeText(
                activity,
                "Address lookup is unavailable on this device. Enter coordinates instead.",
                Toast.LENGTH_LONG
            ).show()
            coordinates()
            return
        }
        val request = ++generation
        dialog?.dismiss()
        val pending = AlertDialog.Builder(activity)
            .setMessage("Finding location…")
            .setNegativeButton("Cancel") { _, _ -> generation++ }
            .create()
            .also { it.setOnCancelListener { generation++ }; it.show() }
        dialog = pending

        fun finish(addresses: List<Address>) {
            handler.post {
                if (closed || generation != request || activity.isFinishing || activity.isDestroyed) return@post
                generation++
                pending.dismiss()
                val areas = addresses
                    .mapNotNull(::valuePilotAreaFromAddress)
                    .distinctBy { it.latitude to it.longitude }
                    .take(MAX_CANDIDATES)
                if (areas.isEmpty()) {
                    Toast.makeText(
                        activity,
                        "No Argentina location found. Try a city and province.",
                        Toast.LENGTH_LONG
                    ).show()
                    open()
                } else {
                    dialog = AlertDialog.Builder(activity)
                        .setTitle("Confirm your search area")
                        .setItems(areas.map { it.label }.toTypedArray()) { _, index -> selected(areas[index]) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }

        handler.postDelayed({
            if (!closed && generation == request) {
                generation++
                pending.dismiss()
                Toast.makeText(
                    activity,
                    "The lookup took too long. Try again or enter coordinates.",
                    Toast.LENGTH_LONG
                ).show()
                open()
            }
        }, LOOKUP_TIMEOUT_MILLIS)

        try {
            Geocoder(activity.applicationContext, Locale.forLanguageTag("es-AR")).getFromLocationName(
                "$query, Argentina",
                MAX_CANDIDATES,
                ARGENTINA_MIN_LATITUDE,
                ARGENTINA_MIN_LONGITUDE,
                ARGENTINA_MAX_LATITUDE,
                ARGENTINA_MAX_LONGITUDE,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = finish(addresses)
                    override fun onError(errorMessage: String?) = finish(emptyList())
                }
            )
        } catch (_: Exception) {
            finish(emptyList())
        }
    }

    private fun coordinates() {
        generation++
        dialog?.dismiss()
        fun field(hintText: String) = EditText(activity).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
            filters = arrayOf(android.text.InputFilter.LengthFilter(24))
        }
        val label = EditText(activity).apply {
            hint = "Place name"
            filters = arrayOf(android.text.InputFilter.LengthFilter(120))
        }
        val latitude = field("Latitude")
        val longitude = field("Longitude")
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            listOf(label, latitude, longitude).forEach(::addView)
        }
        dialog = AlertDialog.Builder(activity)
            .setTitle("Search area in Argentina")
            .setMessage("Enter a place name and exact coordinates.")
            .setView(form)
            .setPositiveButton("Use this area", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog?.setOnShowListener {
            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val area = runCatching {
                    ArgentinaUserSearchArea(
                        label.text.toString().trim(),
                        latitude.text.toString().trim().replace(',', '.'),
                        longitude.text.toString().trim().replace(',', '.')
                    )
                }.getOrNull()
                if (area == null) {
                    label.error = "Check the place name and Argentina coordinates."
                } else {
                    dialog?.dismiss()
                    selected(area)
                }
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

    private companion object {
        const val MAX_CANDIDATES = 5
        const val LOOKUP_TIMEOUT_MILLIS = 20_000L
        const val ARGENTINA_MIN_LATITUDE = -56.0
        const val ARGENTINA_MAX_LATITUDE = -21.0
        const val ARGENTINA_MIN_LONGITUDE = -74.0
        const val ARGENTINA_MAX_LONGITUDE = -53.0
    }
}

/** Maps a geocoder result to an explicit Argentina search area, or nothing. */
internal fun valuePilotAreaFromAddress(address: Address): ArgentinaUserSearchArea? {
    if (!address.countryCode.equals("AR", ignoreCase = true)) return null
    if (!address.hasLatitude() || !address.hasLongitude()) return null
    return runCatching {
        val label = address.getAddressLine(0)?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(address.locality, address.adminArea, address.countryName)
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
            ?: return null
        ArgentinaUserSearchArea(
            label.take(MAX_LABEL_CHARS),
            address.latitude.toString(),
            address.longitude.toString()
        )
    }.getOrNull()
}

private const val MAX_LABEL_CHARS = 256
