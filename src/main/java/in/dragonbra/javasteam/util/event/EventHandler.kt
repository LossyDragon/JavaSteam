package `in`.dragonbra.javasteam.util.event

interface EventHandler<T : EventArgs> {
    fun handleEvent(sender: Any, e: T)
}
