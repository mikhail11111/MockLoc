package com.example.mocklocation

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Detects the current country from the public IP address (follows VPN, if any)
 * and resolves coordinates inside that country.
 *
 * Uses keyless HTTPS endpoints (ipwho.is, then freeipapi.com as fallback).
 * Both usually return city-level coordinates; if only a country code comes
 * back, [CAPITALS] is used as fallback.
 */
object IpCountryLocator {

    data class IpGeo(
        val ip: String,
        val country: String,
        val countryCode: String,
        val city: String,
        val lat: Double?,
        val lng: Double?
    ) {
        /** Coordinates to mock: API city coords if valid, else the country's capital. */
        fun resolveLatLng(): Pair<Double, Double>? {
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                return lat to lng
            }
            return CAPITALS[countryCode.uppercase()]
        }

        fun label(): String =
            listOf(city, country).filter { it.isNotBlank() }.joinToString(", ")
    }

    /** Blocking network call — must run off the main thread. */
    fun fetch(): IpGeo {
        val errors = mutableListOf<String>()
        try {
            return fetchIpWho()
        } catch (e: Exception) {
            errors += "ipwho.is: ${e.message}"
            Log.w(TAG, "ipwho.is failed", e)
        }
        try {
            return fetchFreeIpApi()
        } catch (e: Exception) {
            errors += "freeipapi: ${e.message}"
            Log.w(TAG, "freeipapi failed", e)
        }
        throw Exception(errors.joinToString("; "))
    }

    private fun fetchIpWho(): IpGeo {
        val json = getJson("https://ipwho.is/")
        if (!json.optBoolean("success", false)) {
            throw Exception(json.optString("message", "lookup failed"))
        }
        return IpGeo(
            ip = json.optString("ip"),
            country = json.optString("country"),
            countryCode = json.optString("country_code"),
            city = json.optString("city"),
            lat = json.optDoubleOrNull("latitude"),
            lng = json.optDoubleOrNull("longitude")
        )
    }

    private fun fetchFreeIpApi(): IpGeo {
        val json = getJson("https://freeipapi.com/api/json")
        return IpGeo(
            ip = json.optString("ipAddress"),
            country = json.optString("countryName"),
            countryCode = json.optString("countryCode"),
            city = json.optString("cityName"),
            lat = json.optDoubleOrNull("latitude"),
            lng = json.optDoubleOrNull("longitude")
        )
    }

    private fun getJson(url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("User-Agent", "MockLocation-App")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw Exception("HTTP $code: ${body.take(120)}")
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (isNull(key)) return null
        val d = optDouble(key, Double.NaN)
        return if (d.isNaN()) null else d
    }

    /** Fallback: capital coordinates per ISO country code. */
    private val CAPITALS: Map<String, Pair<Double, Double>> = mapOf(
        "US" to (38.9072 to -77.0369),
        "CA" to (45.4215 to -75.6972),
        "MX" to (19.4326 to -99.1332),
        "BR" to (-15.7939 to -47.8828),
        "AR" to (-34.6037 to -58.3816),
        "CL" to (-33.4489 to -70.6693),
        "CO" to (4.711 to -74.0721),
        "PE" to (-12.0464 to -77.0428),
        "VE" to (10.4806 to -66.9036),
        "GB" to (51.5074 to -0.1278),
        "IE" to (53.3498 to -6.2603),
        "FR" to (48.8566 to 2.3522),
        "DE" to (52.52 to 13.405),
        "ES" to (40.4168 to -3.7038),
        "PT" to (38.7223 to -9.1393),
        "IT" to (41.9028 to 12.4964),
        "NL" to (52.3676 to 4.9041),
        "BE" to (50.8503 to 4.3517),
        "LU" to (49.6116 to 6.1319),
        "CH" to (46.948 to 7.4474),
        "AT" to (48.2082 to 16.3738),
        "MC" to (43.7384 to 7.4246),
        "AD" to (42.5063 to 1.5218),
        "MT" to (35.8989 to 14.5146),
        "CY" to (35.1856 to 33.3823),
        "SE" to (59.3293 to 18.0686),
        "NO" to (59.9139 to 10.7522),
        "DK" to (55.6761 to 12.508),
        "FI" to (60.1699 to 24.9384),
        "IS" to (64.1466 to -21.9426),
        "EE" to (59.437 to 24.7536),
        "LV" to (56.9496 to 24.1052),
        "LT" to (54.6872 to 25.2797),
        "PL" to (52.2297 to 21.0122),
        "CZ" to (50.0755 to 14.4378),
        "SK" to (48.1486 to 17.1077),
        "HU" to (47.4979 to 19.0402),
        "RO" to (44.4268 to 26.1025),
        "BG" to (42.6977 to 23.3219),
        "GR" to (37.9838 to 23.7275),
        "HR" to (45.815 to 15.9819),
        "SI" to (46.0569 to 14.5058),
        "BA" to (43.8563 to 18.4131),
        "RS" to (44.7866 to 20.4489),
        "ME" to (42.4304 to 19.2594),
        "MK" to (42.0029 to 21.4254),
        "AL" to (41.3275 to 19.8187),
        "XK" to (42.6629 to 21.1652),
        "UA" to (50.4501 to 30.5234),
        "BY" to (53.9045 to 27.5615),
        "MD" to (47.0105 to 28.8638),
        "RU" to (55.7558 to 37.6173),
        "KZ" to (51.1694 to 71.4491),
        "KG" to (42.8746 to 74.5698),
        "UZ" to (41.3111 to 69.2797),
        "TJ" to (38.5598 to 68.787),
        "TM" to (37.9601 to 58.3265),
        "TR" to (39.9334 to 32.8597),
        "GE" to (41.7151 to 44.8271),
        "AM" to (40.1792 to 44.4991),
        "AZ" to (40.4093 to 49.8671),
        "IL" to (31.7683 to 35.2137),
        "JO" to (31.9539 to 35.9106),
        "LB" to (33.8938 to 35.5018),
        "SY" to (33.5138 to 36.2765),
        "IQ" to (33.3152 to 44.3661),
        "IR" to (35.6892 to 51.389),
        "SA" to (24.7136 to 46.6753),
        "AE" to (24.4539 to 54.3773),
        "QA" to (25.2854 to 51.531),
        "KW" to (29.3759 to 47.9774),
        "BH" to (26.2285 to 50.586),
        "OM" to (23.588 to 58.3829),
        "YE" to (15.3694 to 44.191),
        "AF" to (34.5553 to 69.2075),
        "PK" to (33.6844 to 73.0479),
        "IN" to (28.6139 to 77.209),
        "BD" to (23.8103 to 90.4125),
        "LK" to (6.9271 to 79.8612),
        "NP" to (27.7172 to 85.324),
        "MM" to (19.7633 to 96.0785),
        "TH" to (13.7563 to 100.5018),
        "VN" to (21.0278 to 105.8342),
        "KH" to (11.55 to 104.9167),
        "LA" to (17.9757 to 102.6331),
        "MY" to (3.139 to 101.6869),
        "SG" to (1.3521 to 103.8198),
        "ID" to (-6.2088 to 106.8456),
        "PH" to (14.5995 to 120.9842),
        "CN" to (39.9042 to 116.4074),
        "HK" to (22.3193 to 114.1694),
        "TW" to (25.033 to 121.5654),
        "JP" to (35.6762 to 139.6503),
        "KR" to (37.5665 to 126.978),
        "KP" to (39.0392 to 125.7545),
        "MN" to (47.8864 to 106.9057),
        "AU" to (-35.2809 to 149.13),
        "NZ" to (-41.2865 to 174.7762),
        "FJ" to (-18.1416 to 178.4419),
        "EG" to (30.0444 to 31.2357),
        "LY" to (32.8872 to 13.1913),
        "TN" to (36.8065 to 10.1815),
        "DZ" to (36.7538 to 3.0588),
        "MA" to (34.0209 to -6.8416),
        "NG" to (9.0579 to 7.4951),
        "GH" to (5.6037 to -0.187),
        "KE" to (1.2921 to 36.8219),
        "ET" to (9.0054 to 38.7636),
        "ZA" to (-26.2041 to 28.0473)
    )

    private const val TAG = "IpCountryLocator"
}
