package io.github.denberg28.telerc

/** USB class 14 is video; class 0/239 composite devices expose it on interfaces. */
object UsbVideoPolicy {
    fun isVideo(deviceClass: Int, interfaceClasses: List<Int>): Boolean =
        deviceClass == 14 || 14 in interfaceClasses
}
