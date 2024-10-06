package `in`.dragonbra.javasteam.util.event

class Event<T : EventArgs> {

    private val handlers = hashSetOf<EventHandler<T>>()

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

    fun handleEvent(sender: Any, e: T?) {
        synchronized(handlers) {
            handlers.forEach { handler ->
                handler.handleEvent(sender, e)
            }
        }
    }
}
