package com.pipboyremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QrPairingTest {
    private val base = "pipboyremote://pair?v=1&host=192.168.1.60&port=8765&secret=abcdefghijklmnopqrstuvwxyzABCDEF"

    @Test fun parsesInvitation() {
        val invitation = parsePairingInvitation(base)
        assertEquals("192.168.1.60", invitation.host)
        assertEquals("abcdefghijklmnopqrstuvwxyzABCDEF", invitation.secret)
    }

    @Test fun deniesInternetHost() {
        assertThrows(IllegalArgumentException::class.java) {
            parsePairingInvitation(base.replace("192.168.1.60", "8.8.8.8"))
        }
    }

    @Test fun deniesDuplicateSecret() {
        assertThrows(IllegalArgumentException::class.java) {
            parsePairingInvitation(base + "&secret=abcdefghijklmnopqrstuvwxyzABCDEF")
        }
    }

    @Test fun deniesUnexpectedProtocol() {
        assertThrows(IllegalArgumentException::class.java) {
            parsePairingInvitation(base.replace("v=1", "v=2"))
        }
    }
}
