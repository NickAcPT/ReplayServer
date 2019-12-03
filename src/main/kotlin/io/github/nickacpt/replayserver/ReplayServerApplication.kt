package io.github.nickacpt.replayserver

import io.github.nickacpt.replayserver.console.ReplayServerConsole
import io.github.nickacpt.replayserver.replay.ReplayServer
import org.apache.logging.log4j.LogManager

open class ReplayServerApplication {
    companion object {
        lateinit var server: ReplayServer;
        var logger = LogManager.getLogger("ReplayServer")!!
        var isRunning = false;
        private var console = ReplayServerConsole()

        fun start() {
            isRunning = true
            console.start()
        }
    }
}

fun main() {
    System.setProperty("org.jline.terminal.dumb.color", "false")
    ReplayServerApplication.start()
}
