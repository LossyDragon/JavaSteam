package `in`.dragonbra.javasteam.util

import `in`.dragonbra.javasteam.util.compat.ObjectsCompat

/**
 * @author lngtr
 * @since 2018-02-19
 */
object CollectionUtils {
    @JvmStatic
    fun <T, E> getKeyByValue(map: Map<T, E>, value: E?): T? =
        map.entries.find { it.value == value }?.key

    /**
     * Compat method for Android API below KitKat (19).
     */
    @JvmStatic
    fun <T, E> getKeyByValueCompat(map: Map<T, E>, value: E?): T? =
        map.entries.find { ObjectsCompat.equals(it.value, value) }?.key
}
