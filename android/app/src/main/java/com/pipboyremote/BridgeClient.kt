package com.pipboyremote

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** This client deliberately exposes no game write endpoint. */
class BridgeClient(private val host: String) {
    private val baseUrl = "http://${privateIpv4Host(host)}:8765"

    fun pair(code: String): String {
        require(Regex("^[0-9]{6}$").matches(code)) { "Pairing code must be six digits" }
        val response = send(
            path = "/v1/pair", method = "POST",
            body = JSONObject().put("code", code).toString()
        )
        return response.getString("token").also { require(it.length >= 30) { "Invalid bridge token" } }
    }

    fun pairQr(secret: String): String {
        require(Regex("^[A-Za-z0-9_-]{32}$").matches(secret)) { "Invalid QR invitation" }
        val response = send(
            path = "/v1/pair", method = "POST",
            body = JSONObject().put("qr_secret", secret).toString()
        )
        return response.getString("token").also { require(it.length >= 30) { "Invalid bridge token" } }
    }

    fun snapshot(token: String): GameSnapshot = parseGameSnapshot(send("/v1/snapshot", bearer = token))

    fun unpair(token: String) {
        send("/v1/unpair", method = "POST", bearer = token, body = "")
    }

    private fun send(path: String, method: String = "GET", bearer: String? = null, body: String? = null): JSONObject {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 3500
        conn.readTimeout = 3500
        conn.instanceFollowRedirects = false
        conn.requestMethod = method
        conn.setRequestProperty("Accept", "application/json")
        if (!bearer.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $bearer")
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            if (status in 300..399) throw IllegalStateException("Bridge redirect refused")
            val response = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(2_000_000) } ?: "{}"
            val json = JSONObject(response)
            if (status !in 200..299) {
                val reason = json.optString("error", "HTTP $status")
                    .replace('_', ' ').replaceFirstChar { it.titlecase(Locale.US) }
                throw BridgeException(status, reason)
            }
            return json
        } finally {
            conn.disconnect()
        }
    }
}

class BridgeException(val status: Int, message: String) : RuntimeException(message)

/** Restrict development HTTP credential traffic to explicitly entered private IPv4 LAN hosts. */
fun privateIpv4Host(input: String): String {
    val host = input.trim()
    val octets = host.split('.')
    require(octets.size == 4 && octets.all { it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) }) {
        "Enter the PC's private IPv4 address (no http:// or port)"
    }
    val numbers = octets.map { it.toIntOrNull() ?: -1 }
    require(numbers.all { it in 0..255 } &&
        (numbers[0] == 10 ||
            (numbers[0] == 172 && numbers[1] in 16..31) ||
            (numbers[0] == 192 && numbers[1] == 168))) {
        "Only private LAN addresses are allowed"
    }
    require(octets.zip(numbers).all { (text, number) -> text == number.toString() }) {
        "Use a normal dotted IPv4 address"
    }
    return host
}
