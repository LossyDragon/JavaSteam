package `in`.dragonbra.javasteam.util.compat

import java.util.Objects

/**
 * A helper class for [Objects] mostly for compatibility for Android API < 19
 *
 * @author steev
 * @since 2018-03-21
 */
object ObjectsCompat {

    /**
     * Returns `true` if the arguments are equal to each other and `false` otherwise.
     */
    @JvmStatic
    fun equals(a: Any?, b: Any?): Boolean = Objects.equals(a, b)
}
