package `in`.dragonbra.javasteam.types

@Suppress("unused")
class BitVector64 {

    var data: Long? = null

    constructor()

    constructor(value: Long) {
        data = value
    }

    fun getMask(bitOffset: Short, valueMask: Long): Long {
        return data!! shr bitOffset.toInt() and valueMask
    }

    fun setMask(bitOffset: Short, valueMask: Long, value: Long) {
        data = (data!! and (valueMask shl bitOffset.toInt()).inv()) or ((value and valueMask) shl bitOffset.toInt())
    }
}
