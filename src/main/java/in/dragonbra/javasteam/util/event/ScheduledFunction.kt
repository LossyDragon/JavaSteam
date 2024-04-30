package `in`.dragonbra.javasteam.util.event

import java.util.*

/**
 * @author lngtr
 * @since 2018-02-20
 */
class ScheduledFunction(private val func: Runnable, var delay: Long) {

    private var timer: Timer? = null

    private var bStarted = false

    private val timerTask = object : TimerTask() {
        override fun run() {
            func.run()
        }
    }

    fun start() {
        if (!bStarted) {
            timer = Timer()
            timer!!.scheduleAtFixedRate(timerTask, 0, delay)
            bStarted = true
        }
    }

    fun stop() {
        if (bStarted) {
            timer!!.cancel()
            timer = null
            bStarted = false
        }
    }
}
