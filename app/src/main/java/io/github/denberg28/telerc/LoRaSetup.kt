package io.github.denberg28.telerc

internal object LoRaSetup {
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
        if (f.firstOrNull() == "TELERC_LORA_RESULT_V1") return when (f.getOrNull(1)) {
            "SAVED_RESTART_BOARD" -> "Saved on board. Disconnect USB and restart the board. Provision its partner with the same settings."
            "ACTIVE_DISCONNECT_AND_ERASE_NVS_FIRST" -> "Radio active: settings unchanged. Disconnect motor power, erase board NVS and reflash before re-pairing."
            else -> "Board rejected the setup request: ${f.getOrNull(1).orEmpty()}"
        }
        if (f.size != 11 || f[0] != "TELERC_LORA_INFO_V1") return "Unrecognized board reply."
        val khz = f[4].toIntOrNull() ?: return "Invalid board settings reply."
        return "${f[1]} · SX${f[2]} · ${if (f[3] == "1") "radio active" else "radio inactive"}\n" +
            "${khz / 1000.0} MHz · ${f[5]} dBm · pairing fingerprint ${f[6]}\n" +
            "RX ${f[7]} · rejected ${f[8]} · TX failures ${f[9]} · UART drops ${f[10]}"
    }
}
