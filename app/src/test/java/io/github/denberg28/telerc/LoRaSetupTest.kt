package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class LoRaSetupTest {
    private val key = "01".repeat(32)
    @Test fun validatesExactFrequencyAndKey() {
        assertEquals("TELERC_LORA_SET_V1,433125,2,$key", LoRaSetup.request("433.125", "2", key))
        assertNull(LoRaSetup.request("433.1251", "2", key))
        assertNull(LoRaSetup.request("433", "18", key))
        assertNull(LoRaSetup.request("433", "2", "00".repeat(32)))
        assertNull(LoRaSetup.request("433", "2", key + ",bad"))
    }
    @Test fun distinguishesLocalBoardFromRoverHeartbeat() {
        val status = LoRaSetup.describe("TELERC_LORA_INFO_V1,BASE,1262,0,433125,2,abcd,1,2,3,4")
        assertTrue(status.contains("radio inactive"))
        assertTrue(status.contains("433.125 MHz"))
        assertTrue(status.contains("UART drops 4"))
        assertTrue(LoRaSetup.describe("TELERC_LORA_RESULT_V1,SAVED_RESTART_BOARD").contains("restart"))
    }
}
