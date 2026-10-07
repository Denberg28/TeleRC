package io.github.denberg28.telerc

internal object LoRaSetup {
    data class Board(val role: String, val chip: Int, val active: Boolean, val khz: Int,
        val power: Int, val fingerprint: String, val rx: Long, val rejected: Long,
        val txFailures: Long, val uartDrops: Long)

    fun board(reply: String): Board? {
        val f = reply.split(',')
        if (f.size != 11 || f[0] != "TELERC_LORA_INFO_V1" || f[1] !in listOf("BASE", "ROVER") ||
            f[2] !in listOf("1262", "1276") || f[3] !in listOf("0", "1")) return null
        val khz = f[4].toIntOrNull()?.takeIf { it in 150000..960000 || (it == 0 && f[3] == "0") } ?: return null
        val power = f[5].toIntOrNull()?.takeIf { it in 2..17 } ?: return null
        if (!Regex("[0-9a-fA-F]{4}").matches(f[6])) return null
        val counters = f.drop(7).map { it.toLongOrNull()?.takeIf { n -> n in 0..0xffffffffL } ?: return null }
        return Board(f[1], f[2].toInt(), f[3] == "1", khz, power, f[6].lowercase(),
            counters[0], counters[1], counters[2], counters[3])
    }

    // Same CRC16-CCITT as the firmware. A 16-bit fingerprint helps spot a mismatch;
    // equality is not proof that keys match, authentication is, and the key is never read back.
    fun fingerprint(key: String): String? {
        if (!Regex("[0-9a-fA-F]{64}").matches(key)) return null
        var crc = 0xffff
        key.chunked(2).forEach { pair ->
            crc = crc xor (pair.toInt(16) shl 8)
            repeat(8) { crc = ((crc shl 1) xor if (crc and 0x8000 != 0) 0x1021 else 0) and 0xffff }
        }
        return "%04x".format(crc)
    }
    fun request(mhz: String, power: String, key: String): String? {
        val frequency = mhz.trim().toBigDecimalOrNull() ?: return null
        val khz = try { frequency.multiply(java.math.BigDecimal(1000)).intValueExact() } catch (_: ArithmeticException) { return null }
        val dbm = power.trim().toIntOrNull() ?: return null
        val secret = key.trim()
        if (khz !in 150000..960000 || dbm !in 2..17 || !Regex("[0-9a-fA-F]{64}").matches(secret) || secret.all { it == '0' }) return null
        return "TELERC_LORA_SET_V1,$khz,$dbm,$secret"
    }
    fun describe(reply: String): String {
        val f = reply.split(',')
        if (f.size == 2 && f[0] == "TELERC_LORA_RESULT_V1") return when (f[1]) {
            "SAVED_RESTART_BOARD" -> "Saved on board. Disconnect USB and restart the board. Provision its partner with the same settings."
            "ACTIVE_DISCONNECT_AND_ERASE_NVS_FIRST" -> "Radio active: settings unchanged. Disconnect motor power, erase board NVS and reflash before re-pairing."
            "STORAGE_FAILED" -> "Board storage failed. Settings were not saved; reconnect and read before retrying."
            "INVALID" -> "Board rejected the pairing values. Settings were not saved."
            else -> "Unrecognized board result. Read settings before retrying."
        }
        val b = board(reply) ?: return "Invalid or unrecognized board settings reply."
        val frequency = if (b.khz == 0) "frequency unset" else "${b.khz / 1000.0} MHz"
        return "${b.role} · SX${b.chip} · ${if (b.active) "radio active" else "radio inactive"}\n" +
            "$frequency · ${b.power} dBm · key fingerprint ${b.fingerprint}\n" +
            "RX ${b.rx} · rejected ${b.rejected} · TX failures ${b.txFailures} · UART drops ${b.uartDrops}"
    }
}

/** USB admin has no request IDs. Serialize exchanges and require a new session after
 * timeout, so a delayed save ACK cannot confirm another draft. All calls are UI-thread only. */
internal class LoRaSetupExchange {
    data class Draft(val mhz: String, val power: String, val key: String)
    var board: LoRaSetup.Board? = null; private set
    var pending: String? = null; private set
    var blocked = false; private set
    var restartRequired = false; private set
    private var draft: Draft? = null
    val canSave get() = board?.active == false && pending == null && !blocked && !restartRequired
    fun reset() { board = null; pending = null; blocked = false; restartRequired = false; draft = null }
    fun beginRead(): Boolean {
        if (pending != null || blocked) return false
        board = null; pending = "read"; return true
    }
    fun beginSave(value: Draft): String? {
        if (!canSave) return null
        val request = LoRaSetup.request(value.mhz, value.power, value.key) ?: return null
        draft = value; pending = "save"; return request
    }
    fun receive(reply: String): Draft? {
        if (blocked) return null
        if (pending == "read") {
            val info = LoRaSetup.board(reply) ?: return null
            board = info; pending = null
        } else if (pending == "save") {
            val f = reply.split(',')
            if (f.size != 2 || f[0] != "TELERC_LORA_RESULT_V1" || f[1] !in listOf(
                    "SAVED_RESTART_BOARD", "INVALID", "STORAGE_FAILED", "ACTIVE_DISCONNECT_AND_ERASE_NVS_FIRST")) return null
            pending = null
            val saved = draft; draft = null
            board = null
            if (f[1] == "SAVED_RESTART_BOARD") { restartRequired = true; return saved }
        }
        return null
    }
    fun timeout() { if (pending != null) { pending = null; draft = null; board = null; blocked = true } }
}
