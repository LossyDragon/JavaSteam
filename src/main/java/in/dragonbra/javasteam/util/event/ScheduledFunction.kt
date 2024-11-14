package `in`.dragonbra.javasteam.util.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * @author lngtr
 * @since 2018-02-20
 */
fun interface ScheduledAction {
    fun execute()
}

class ScheduledFunction(
    private val coroutineScope: CoroutineScope,
    private var delay: Long,
    private val action: ScheduledAction,
) {

    private var job: Job? = null

    fun setDelay(delay: Long) {
        this.delay = delay
    }

    fun start() {
        job?.cancel()
        job = coroutineScope.launch {
            while (isActive) {
                action.execute()
                delay(delay)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
