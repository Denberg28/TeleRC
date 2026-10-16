package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class UsbVideoPolicyTest {
    @Test fun videoClassAndCompositeReceiversAreCandidates() {
        assertTrue(UsbVideoPolicy.isVideo(14, emptyList()))
        assertTrue(UsbVideoPolicy.isVideo(0, listOf(1, 14, 14)))
        assertTrue(UsbVideoPolicy.isVideo(239, listOf(14, 14)))
        assertTrue(UsbVideoPolicy.isVideo(255, listOf(14)))
    }
    @Test fun serialLoRaAndUnknownVendorDevicesAreNotOpenedAsCameras() {
        assertFalse(UsbVideoPolicy.isVideo(239, listOf(2, 10)))
        assertFalse(UsbVideoPolicy.isVideo(255, listOf(255)))
        assertFalse(UsbVideoPolicy.isVideo(0, emptyList()))
    }
}
