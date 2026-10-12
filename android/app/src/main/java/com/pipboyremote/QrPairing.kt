package com.pipboyremote

import java.net.URI

/** Parser deliberately does not launch arbitrary QR URLs or trust remote host names. */
data class PairingInvitation(val host: String, val secret: String)

fun parsePairingInvitation(raw: String): PairingInvitation {
    require(raw.length in 60..512) { "Invalid Pip-Boy pairing QR" }
    val url = URI(raw)
    require(url.scheme == "pipboyremote" && url.host == "pair" && url.port == -1 &&
        url.userInfo == null && url.rawFragment == null && (url.path.isNullOrBlank())) {
        "This QR is not a Pip-Boy pairing invitation"
    }
    val params = url.rawQuery?.split('&')?.map { item ->
        val components = item.split('=', limit = 2)
        require(components.size == 2) { "Invalid QR parameters" }
        components[0] to components[1]
    } ?: throw IllegalArgumentException("QR parameters missing")
    require(params.size == 4 && params.map { it.first }.toSet() ==
        setOf("v", "host", "port", "secret")) { "Invalid or duplicate QR fields" }
    val values = params.toMap()
    require(values["v"] == "1" && values["port"] == "8765") { "Unsupported pairing protocol" }
    val host = privateIpv4Host(values["host"].orEmpty())
    val secret = values["secret"].orEmpty()
    require(Regex("^[A-Za-z0-9_-]{32}$").matches(secret)) { "Invalid QR secret" }
    return PairingInvitation(host, secret)
}
