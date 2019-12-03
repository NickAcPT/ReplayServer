package io.github.nickacpt.replayserver.console

import com.github.steveice10.mc.protocol.MinecraftProtocol
import com.github.steveice10.mc.protocol.data.game.MessageType
import com.github.steveice10.mc.protocol.data.message.Message
import com.github.steveice10.packetlib.tcp.TcpSessionFactory
import io.github.nickacpt.replayserver.ReplayServerApplication
import io.github.nickacpt.replayserver.replay.ReplayServer
import io.github.nickacpt.replayserver.sender.ICommandSender
import net.minecrell.terminalconsole.SimpleTerminalConsole
import org.apache.logging.log4j.Level

class ReplayServerConsole : SimpleTerminalConsole(), ICommandSender {
    init {
        startServer()
    }

    fun startServer() {
        ReplayServerApplication.server =
            ReplayServer("0.0.0.0", 25565, MinecraftProtocol::class.java, TcpSessionFactory())
                .also {
                    it.bind()
                }

    }


    override fun isRunning(): Boolean {
        return ReplayServerApplication.isRunning
    }

    override fun runCommand(command: String?) {
    }

    override fun shutdown() {
        ReplayServerApplication.isRunning = false
    }

    override fun sendMessage(message: String?, messageType: MessageType?) {
        ReplayServerApplication.logger.log(Level.INFO, message.toString())
    }

    override fun sendMessage(message: Message?, messageType: MessageType?) {
        ReplayServerApplication.logger.log(Level.INFO, message.toString())
    }

    override val isPlayer: Boolean
        get() = false
}