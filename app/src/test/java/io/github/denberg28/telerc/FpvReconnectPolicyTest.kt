package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test
import io.github.denberg28.telerc.FpvReconnectPolicy.Action.*

class FpvReconnectPolicyTest {
    private val receiver = FpvReconnectPolicy.Receiver(0x534d, 0x2109)
    private fun device(id: String = "usb/1#1", grant: Boolean = true) = FpvReconnectPolicy.Device(id, receiver, grant)
    @Test fun rememberedReceiverReenumeratesWithoutAnotherPrompt() {
        val p = FpvReconnectPolicy(receiver, true)
        assertEquals(OPEN, p.decide(listOf(device()), true, null, 100).action)
        assertEquals(LOST, p.decide(emptyList(), true, "usb/1#1", 200).action)
        p.detached()
        assertTrue(p.wanted)
        assertEquals(WAIT_DEVICE, p.decide(emptyList(), true, null, 300).action)
        val replacement = device("usb/2#18")
        assertEquals(replacement, p.decide(listOf(replacement), true, null, 400).device)
        assertEquals(OPEN, p.decide(listOf(replacement), true, null, 400).action)
        assertNull(p.pendingPermission)
    }
    @Test fun permissionAskedOnlyOnceForAnExplicitAttempt() {
        val p = FpvReconnectPolicy(receiver, true)
        val denied = device(grant = false)
        assertEquals(NEED_PERMISSION, p.decide(listOf(denied), true, null, 100).action)
        p.request()
        assertEquals(REQUEST_PERMISSION, p.decide(listOf(denied), true, null, 100).action)
        repeat(20) {
            p.request() // Repeated taps while the same OS dialog is pending.
            assertEquals(WAIT_PERMISSION, p.decide(listOf(denied), true, null, 200).action)
        }
        assertFalse(p.permissionResult("stale#55", true))
        assertTrue(p.permissionResult(denied.attachment, false))
        assertFalse(p.wanted)
        assertEquals(IDLE, p.decide(listOf(denied), true, null, 300).action)
        p.request()
        assertEquals(REQUEST_PERMISSION, p.decide(listOf(denied), true, null, 400).action)
    }
    @Test fun detachDuringPermissionDoesNotTransferGrantToAnotherAttachment() {
        val p = FpvReconnectPolicy(receiver)
        p.request()
        assertEquals(REQUEST_PERMISSION, p.decide(listOf(device(grant=false)), true, null, 100).action)
        assertEquals(WAIT_DEVICE, p.decide(emptyList(), true, null, 200).action)
        assertFalse(p.permissionResult("usb/1#1", true))
        assertEquals(NEED_PERMISSION, p.decide(listOf(device("usb/9#9",false)), true, null, 300).action)
    }
    @Test fun systemAttachmentLaunchCannotArmAPermissionPrompt() {
        val p=FpvReconnectPolicy(receiver)
        p.request(receiver,allowPermission=false)
        repeat(20) { assertEquals(NEED_PERMISSION,p.decide(listOf(device(grant=false)),true,null,100).action) }
        assertNull(p.pendingPermission)
        assertEquals(OPEN,p.decide(listOf(device()),true,null,200).action)
        p.request(receiver,allowPermission=true)
        assertEquals(REQUEST_PERMISSION,p.decide(listOf(device(grant=false)),true,null,300).action)
    }
    @Test fun existingSessionSurvivesRepeatedConnectAndGrantRevocationStopsIt() {
        val p = FpvReconnectPolicy(receiver, true)
        repeat(30) { p.request(); assertEquals(KEEP, p.decide(listOf(device()), true, device().attachment, 100).action) }
        assertEquals(LOST, p.decide(listOf(device(grant=false)), true, device().attachment, 200).action)
        p.detached()
        assertEquals(NEED_PERMISSION, p.decide(listOf(device(grant=false)), true, null, 300).action)
    }
    @Test fun doNotOpenAnotherReceiverOrAmbiguousIdenticalModules() {
        val p = FpvReconnectPolicy(receiver, true)
        val other = device().copy(receiver=FpvReconnectPolicy.Receiver(100,200))
        assertEquals(WAIT_DEVICE,p.decide(listOf(other),true,null,100).action)
        assertEquals(AMBIGUOUS,p.decide(listOf(device(),device("usb/2#2")),true,null,100).action)
        assertEquals(OPEN,p.decide(listOf(device(),other),true,null,100).action)
    }
    @Test fun missedDetachCannotKeepANewAttachmentInOldCooldown() {
        val p=FpvReconnectPolicy(receiver,true)
        assertEquals(OPEN,p.decide(listOf(device()),true,null,100).action)
        repeat(5) { p.failed(200) }
        assertEquals(WAIT_RETRY,p.decide(listOf(device()),true,null,300).action)
        assertEquals(OPEN,p.decide(listOf(device("usb/7#7")),true,null,400).action)
        assertEquals(0,p.failures)
        assertEquals(NEED_PERMISSION,p.decide(listOf(device("usb/8#8",false)),true,null,500).action)
    }
    @Test fun backgroundAndHiddenPreviewNeverRequestOrOpen() {
        val p = FpvReconnectPolicy(receiver)
        p.request()
        assertEquals(IDLE,p.decide(listOf(device(grant=false)),false,null,100).action)
        p.disallowPrompt()
        assertEquals(NEED_PERMISSION,p.decide(listOf(device(grant=false)),true,null,200).action)
        p.request()
        assertEquals(REQUEST_PERMISSION,p.decide(listOf(device(grant=false)),true,null,300).action)
        assertTrue(p.permissionResult(device().attachment,true))
        assertEquals(IDLE,p.decide(listOf(device()),false,null,400).action)
        assertEquals(OPEN,p.decide(listOf(device()),true,null,500).action)
        p.stop()
        assertEquals(IDLE,p.decide(listOf(device()),true,null,600).action)
    }
    @Test fun recoveryRemainsPersistentButRateBoundedAndNeedsSustainedFrames() {
        val p = FpvReconnectPolicy(receiver,true)
        assertEquals(listOf(1000L,2000L,4000L,30000L,30000L), (0..4).map { p.failed(100) })
        assertEquals(WAIT_RETRY,p.decide(listOf(device()),true,null,30099).action)
        assertEquals(OPEN,p.decide(listOf(device()),true,null,30100).action)
        assertTrue(p.wanted)
        repeat(1000) { p.failed(100) }
        assertEquals(4,p.failures)
        p.frame(40000); p.frame(43001); p.frame(46002)
        assertEquals(4,p.failures) // Isolated frames are not stable recovery.
        for (now in 47000L..50000L step 500) p.frame(now)
        assertEquals(0,p.failures)
        assertEquals(1000L,p.failed(51000))
    }
}
