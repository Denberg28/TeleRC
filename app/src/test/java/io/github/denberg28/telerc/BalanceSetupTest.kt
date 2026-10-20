package io.github.denberg28.telerc
import org.junit.Assert.*
import org.junit.Test
class BalanceSetupTest {
    private fun bytes(s:String)=s.toByteArray(Charsets.US_ASCII)
    private val fixture="TELERC_BALANCE_V1|STATUS|555|123|1|0|IDLE|0|1|REUSED|0|1|-2.700|0.100|VERIFY_DIRECTIONS_T_THEN_V|CAL_SAVED_READY|ready|0.0400|55|150"
    @Test fun parseFirmwareStatusAndBuildOneAction() {
        val s=BalanceSetup.parse(bytes(fixture))!!
        assertTrue(s.calibrated);assertTrue(s.testDone);assertFalse(s.verified)
        assertEquals(-2.7f,s.angle,.001f)
        assertEquals("TELERC_BALANCE_V1|123|9|4",String(BalanceSetup.command(s,9,BalanceSetup.Action.BOTH),Charsets.US_ASCII))
        assertEquals("TELERC_BALANCE_V1|S",String(BalanceSetup.stop,Charsets.US_ASCII))
    }
    @Test fun malformedUnsupportedAndNonfiniteNeverEnable() {
        for(s in listOf(fixture.replace("|55|150","|255|150"),fixture.replace("|55|150","|55|1500"),fixture.replace("-2.700","NaN"),
            fixture.replace("|123|","|4294967296|"),fixture+"|extra",fixture+"\n",fixture.replace("|STATUS|","|STATUS2|"),fixture.replace("|REUSED|0|1","|REUSED|2|1")))
            assertNull(s,BalanceSetup.parse(bytes(s)))
        assertNull(BalanceSetup.parse(bytes("TELERC_DISCOVER_V1")))
    }
    @Test fun orderedStatusAndReboot() {
        val s=BalanceSetup.parse(bytes(fixture))!!
        assertFalse(BalanceSetup.newer(s,s))
        assertFalse(BalanceSetup.newer(s,s.copy(sequence=0)))
        assertTrue(BalanceSetup.newer(s,s.copy(sequence=2)))
        assertTrue(BalanceSetup.newer(s,s.copy(boot=556,sequence=0)))
        assertTrue(BalanceSetup.newer(s.copy(sequence=0xffffffffL),s.copy(sequence=0)))
    }
    @Test fun freshnessModeAndDirectionRequirements() {
        val s=BalanceSetup.parse(bytes(fixture))!!
        for(a in BalanceSetup.Action.entries)assertTrue(BalanceSetup.allowed(s,100,101,a))
        assertFalse(BalanceSetup.allowed(s,100,1400,BalanceSetup.Action.BOTH))
        assertFalse(BalanceSetup.allowed(s,100,99,BalanceSetup.Action.BOTH))
        assertFalse(BalanceSetup.allowed(s.copy(mode=2),100,101,BalanceSetup.Action.BOTH))
        assertFalse(BalanceSetup.allowed(s.copy(challenge=0),100,101,BalanceSetup.Action.BOTH))
        assertFalse(BalanceSetup.allowed(s.copy(testDone=false),100,101,BalanceSetup.Action.VERIFY))
        assertFalse(BalanceSetup.allowed(s.copy(angle=8f),100,101,BalanceSetup.Action.VERIFY))
        assertFalse(BalanceSetup.allowed(s.copy(blocker="CONTROLLER_STATUS_EXPIRED"),100,101,BalanceSetup.Action.CALIBRATE))
        assertTrue(BalanceSetup.allowed(s.copy(mode=4,calibrated=false),100,101,BalanceSetup.Action.CALIBRATE))
        assertFalse(BalanceSetup.allowed(s.copy(mode=4,calibrated=false),100,101,BalanceSetup.Action.LEFT))
    }
    @Test fun consentCannotCrossReconnectBootOrReadinessChange() {
        val status=BalanceSetup.parse(bytes(fixture))!!
        for(action in BalanceSetup.Action.entries) {
            val consent=BalanceSetup.Consent(status.boot,12,action)
            assertTrue(consent.allowed(status.copy(sequence=2,challenge=456),12,100,110))
            assertFalse(consent.allowed(status,13,100,110))
            assertFalse(consent.allowed(status.copy(boot=556),12,100,110))
            assertFalse(consent.allowed(null,12,100,110))
            assertFalse(consent.allowed(status,12,100,1400))
            assertFalse(consent.allowed(status.copy(mode=2),12,100,110))
        }
    }
}
