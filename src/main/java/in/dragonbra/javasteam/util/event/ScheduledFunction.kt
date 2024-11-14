package `in`.dragonbra.javasteam.util.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * @author lngtr
 * @since 2018-02-20
 */
fun interface ScheduledAction {
    fun execute()
}

class ScheduledFunction(
    private val coroutineScope: CoroutineScope,
    private var delay: Duration,
    private val action: ScheduledAction
) {

    constructor(
        coroutineScope: CoroutineScope,
        duration: Long,
        action: ScheduledAction
    ) : this(coroutineScope, duration.seconds, action)

    private var job: Job? = null

    fun setDelay(delay: Long) {
        this.delay = delay.seconds
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
