package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class LoRaSetupExchangeTest {
    private val info = "TELERC_LORA_INFO_V1,BASE,1262,0,433125,2,abcd,1,2,3,4"
    private val draft = LoRaSetupExchange.Draft("433.125", "2", "01".repeat(32))
    private fun ready() = LoRaSetupExchange().apply {
        assertTrue(beginRead()); receive(info); assertTrue(canSave)
    }
    @Test fun requiresARealInactiveBoardReadBeforeSave() {
        val session = LoRaSetupExchange()
        assertNull(session.beginSave(draft))
        assertTrue(session.beginRead())
        session.receive(info.replace(",0,433", ",1,433"))
        assertFalse(session.canSave); assertNull(session.beginSave(draft))
        assertTrue(session.beginRead())
        session.receive(info)
        assertNotNull(session.beginSave(draft))
    }
    @Test fun onlyMatchingConfirmedSaveReturnsPersistableDraft() {
        val session = ready()
        assertNotNull(session.beginSave(draft))
        assertFalse(session.beginRead())
        assertNull(session.receive(info))
        assertEquals("save", session.pending)
        assertEquals(draft, session.receive("TELERC_LORA_RESULT_V1,SAVED_RESTART_BOARD"))
        assertTrue(session.restartRequired); assertFalse(session.canSave)
        assertNull(session.receive("TELERC_LORA_RESULT_V1,SAVED_RESTART_BOARD"))
    }
    @Test fun rejectedSaveDoesNotReturnOrRetainDraftForAnotherResult() {
        for (reason in listOf("INVALID", "STORAGE_FAILED", "ACTIVE_DISCONNECT_AND_ERASE_NVS_FIRST")) {
            val session = ready(); session.beginSave(draft)
            assertNull(session.receive("TELERC_LORA_RESULT_V1,$reason"))
            assertNull(session.pending); assertNull(session.board); assertFalse(session.canSave)
            assertNull(session.receive("TELERC_LORA_RESULT_V1,SAVED_RESTART_BOARD"))
        }
    }
    @Test fun timeoutBlocksDelayedAckAndFurtherExchangesUntilReconnect() {
        val session = ready(); session.beginSave(draft); session.timeout()
        assertTrue(session.blocked); assertFalse(session.beginRead())
        assertNull(session.beginSave(draft))
        assertNull(session.receive("TELERC_LORA_RESULT_V1,SAVED_RESTART_BOARD"))
        assertFalse(session.restartRequired)
        session.reset(); assertTrue(session.beginRead()); session.receive(info)
        assertTrue(session.canSave)
    }
    @Test fun resetDropsOldSessionBoardAndPendingSave() {
        val session = ready(); session.beginSave(draft); session.reset()
        assertNull(session.receive("TELERC_LORA_RESULT_V1,SAVED_RESTART_BOARD"))
        assertNull(session.board); assertNull(session.pending); assertFalse(session.canSave)
    }
    @Test fun malformedOrWrongTypeReplyCannotCompleteRead() {
        val session = LoRaSetupExchange(); session.beginRead()
        assertNull(session.receive("TELERC_LORA_RESULT_V1,SAVED_RESTART_BOARD"))
        assertNull(session.receive(info + ",extra"))
        assertEquals("read", session.pending); assertNull(session.board)
        session.timeout(); assertTrue(session.blocked)
    }
    @Test fun readAfterSaveCannotRemoveRestartRequirement() {
        val session = ready(); session.beginSave(draft)
        session.receive("TELERC_LORA_RESULT_V1,SAVED_RESTART_BOARD")
        assertTrue(session.beginRead()); session.receive(info)
        assertTrue(session.restartRequired); assertFalse(session.canSave)
    }
}
