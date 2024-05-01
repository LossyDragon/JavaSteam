package `in`.dragonbra.javasteam.util

/**
 * @author lngtr
 * @since 2018-02-19
 */
object CollectionUtils {
    @JvmStatic
    fun <T, E> getKeyByValue(map: Map<T, E>, value: E): T? {
        return map.entries.find { it.value == value }?.key
    }
}
