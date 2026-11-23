package com.archivetv.data

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class PinTest {
    @Test fun hideAdultDefaultsOn() {
        assertThat(AppSettings.InMemory().hideAdult.value).isTrue()
        val s = AppSettings.InMemory()
        s.setHideAdult(false)
        assertThat(s.hideAdult.value).isFalse()
    }

    @Test fun pinRoundTrip() {
        val s = AppSettings.InMemory()
        assertThat(s.hasPin()).isFalse()
        assertThat(s.checkPin("1234")).isFalse()
        s.setPin("1234")
        assertThat(s.hasPin()).isTrue()
        assertThat(s.checkPin("1234")).isTrue()
        assertThat(s.checkPin("4321")).isFalse()
    }

    @Test fun invalidPinRejected() {
        val s = AppSettings.InMemory()
        listOf("12a4", "123", "12345", "").forEach { pin ->
            assertThrows(IllegalArgumentException::class.java) { s.setPin(pin) }
        }
        assertThat(PinHash.isValid("0000")).isTrue()
        assertThat(PinHash.isValid("١٢٣٤")).isFalse() // non-ASCII digits
    }

    @Test fun hashIsSaltedAndStable() {
        val a = PinHash.hash("salt-a", "1234")
        assertThat(PinHash.hash("salt-a", "1234")).isEqualTo(a)
        assertThat(PinHash.hash("salt-b", "1234")).isNotEqualTo(a)
        assertThat(a).matches("[0-9a-f]{64}")
        assertThat(PinHash.newSalt()).isNotEqualTo(PinHash.newSalt())
    }

    @Test fun gateLocksAfterFiveWrong() {
        var now = 1_000L
        val gate = PinGate { now }
        repeat(4) { gate.record(correct = false) }
        assertThat(gate.lockedForMs()).isEqualTo(0)
        gate.record(correct = false)
        assertThat(gate.lockedForMs()).isEqualTo(30_000)
        now += 29_999
        assertThat(gate.lockedForMs()).isEqualTo(1)
        now += 1
        assertThat(gate.lockedForMs()).isEqualTo(0)
    }

    @Test fun correctEntryResetsTheCount() {
        val gate = PinGate { 0L }
        repeat(4) { gate.record(correct = false) }
        gate.record(correct = true)
        repeat(4) { gate.record(correct = false) }
        assertThat(gate.lockedForMs()).isEqualTo(0)
    }
}
