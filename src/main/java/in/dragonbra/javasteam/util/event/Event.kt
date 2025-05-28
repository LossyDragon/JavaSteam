package `in`.dragonbra.javasteam.util.event

open class Event<T : EventArgs> {

    protected val handlers: HashSet<EventHandler<T>> = HashSet<EventHandler<T>>()

    fun addEventHandler(handler: EventHandler<T>) {
        synchronized(handlers) {
            handlers.add(handler)
        }
    }

    fun removeEventHandler(handler: EventHandler<T>) {
        synchronized(handlers) {
            handlers.remove(handler)
        }
    }

    fun handleEvent(sender: Any, e: T) {
        synchronized(handlers) {
            handlers.forEach { it.handleEvent(sender, e) }
        }
    }
}
