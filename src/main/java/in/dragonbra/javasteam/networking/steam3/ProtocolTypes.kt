package `in`.dragonbra.javasteam.networking.steam3

import java.util.*

/**
 * @author lngtr
 * @since 2018-02-20
 */
enum class ProtocolTypes(val code: Int) {
    TCP(1),

    UDP(1 shl 1),

    WEB_SOCKET(1 shl 2),

    ;

    companion object {
        @JvmField
        val ALL: EnumSet<ProtocolTypes> = EnumSet.of(TCP, UDP, WEB_SOCKET)

        @JvmStatic
        fun from(code: Int): EnumSet<ProtocolTypes> = entries
            .filter { e -> (e.code and code) == e.code }
            .toCollection(EnumSet.noneOf(ProtocolTypes::class.java))

        @JvmStatic
        fun code(flags: EnumSet<ProtocolTypes>): Int = flags.fold(0) { acc, flag -> acc or flag.code }
    }
}
