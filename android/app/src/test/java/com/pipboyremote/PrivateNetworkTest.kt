package com.pipboyremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PrivateNetworkTest {
    @Test fun acceptsPrivateLanIp() {
        assertEquals("192.168.1.50", privateIpv4Host(" 192.168.1.50 "))
        assertEquals("172.16.10.1", privateIpv4Host("172.16.10.1"))
        assertEquals("10.20.30.40", privateIpv4Host("10.20.30.40"))
    }

    @Test fun rejectsPublicOrMalformedAddresses() {
        listOf("8.8.8.8", "https://192.168.1.2", "192.168.1.2:8765", "127.0.0.1",
            "172.32.0.1", "192.168.01.2", "192.168.1.300", "10.0.0.1/evil", "localhost").forEach {
            assertThrows(IllegalArgumentException::class.java) { privateIpv4Host(it) }
        }
    }
}
