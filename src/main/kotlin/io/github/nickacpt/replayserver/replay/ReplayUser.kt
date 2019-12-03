package io.github.nickacpt.replayserver.replay

import com.github.steveice10.mc.protocol.MinecraftConstants
import com.github.steveice10.mc.protocol.data.game.MessageType
import com.github.steveice10.mc.protocol.data.game.PlayerListEntry
import com.github.steveice10.mc.protocol.data.game.PlayerListEntryAction
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode
import com.github.steveice10.mc.protocol.data.message.Message
import com.github.steveice10.mc.protocol.data.message.TextMessage
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerChatPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerDisconnectPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerPlayerListEntryPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket
import com.github.steveice10.packetlib.Session
import com.github.steveice10.packetlib.event.session.*
import com.github.steveice10.packetlib.packet.Packet
import com.replaymod.replayserver.api.IConnectedPlayer
import com.replaymod.replayserver.api.IReplayDatabase
import com.replaymod.replayserver.api.IReplaySelector
import com.replaymod.replayserver.api.IReplaySession
import com.replaymod.replaystudio.replay.ReplayFile
import com.replaymod.replaystudio.util.Location
import io.github.nickacpt.replayserver.resourcepacks.ResourcePackServer
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.function.Function
import java.util.logging.Level
import java.util.logging.Logger

class ReplayUser(private val server: ReplayServer, override val session: Session) :
    SessionAdapter(), Runnable, IConnectedPlayer, Executor {
    private val packetQueue: Queue<Packet> =
        ConcurrentLinkedQueue()
    private val workerThreadQueue: Queue<Runnable> = ConcurrentLinkedQueue()
    private var workerThread: Thread? = null
    override var replaySelector: IReplaySelector? = null
    override var replayDatabase: IReplayDatabase? = null
    override val resourcePackServer = ResourcePackServer(this)
    var packetListeners = hashMapOf<Class<*>, MutableList<Runnable>>()

    override fun sendPacket(packet: Packet?) {
        session.send(packet)
    }

    inline fun <reified T : Packet> onPacket(action: Runnable) {
        val value = T::class.java
        packetListeners.computeIfAbsent(value, Function { return@Function mutableListOf<Runnable>() }).run {
            this.add(action)
        }
    }

    override fun sendMessage(
        message: String?,
        messageType: MessageType?
    ) {
        sendPacket(ServerChatPacket(TextMessage(message), messageType!!))
    }

    override fun sendMessage(
        message: Message?,
        messageType: MessageType?
    ) {
        sendPacket(ServerChatPacket(message!!, messageType!!))
    }

    override fun kick(message: Message?) {
        sendPacket(ServerDisconnectPacket(message!!))
        session.disconnect(message.fullText, true)
    }

    override var replaySession: IReplaySession? = null

    override val isConnected: Boolean
        get() = session.isConnected

    override fun teleport(location: Location?) {
        sendPacket(
            ServerPlayerPositionRotationPacket(
                location!!.x, location.y, location.z, location.yaw, location.pitch, -12345
            )
        )
    }

    override fun packetReceived(event: PacketReceivedEvent) {
        packetQueue.offer(event.getPacket())
        workerThread!!.interrupt()
    }

    override fun disconnected(event: DisconnectedEvent) {
        resourcePackServer.stop()
        if (replaySession != null) {
            try {
                replaySession!!.close()
                replaySession = null
            } catch (t: Throwable) {
                logger.error("Error closing replay session:", t)
            }
        }
    }

    override fun run() {
        try {
            workerThread = Thread.currentThread()
            while (isConnected) { // Handle incoming packets
                while (!packetQueue.isEmpty()) {
                    server.notifyPacketHandlers(this, packetQueue.poll())
                }
                // Handle queued tasks
                while (!workerThreadQueue.isEmpty()) {
                    workerThreadQueue.poll().run()
                }
                var sleep: Long = 100
                if (replaySession != null) { // Send replay data
                    sleep = replaySession!!.process(System.currentTimeMillis())
                    if (sleep == 0L) { // Paused, sleep 100ms or until we get a new packet and are interrupted (which is more likely)
                        sleep = 100
                    }
                }
                try {
                    Thread.sleep(sleep)
                } catch (ignored: InterruptedException) {
                }
            }
        } catch (t: Throwable) {
            logger.error("Exception in user worker loop:", t)
            kick(TextMessage("Internal Server Error"))
        }
    }

    override fun execute(runnable: Runnable) {
        workerThreadQueue.offer(runnable)
        workerThread!!.interrupt()
    }

    fun init(replayFile: ReplayFile) {
        logger.info("Initializing session for $this with replay $replayFile")

        resourcePackServer.start()
        replaySession = ReplaySession(this, replayFile)
        // We need to send a player list entry for the spectator to be able to no-clip
        // This will inevitably show the spectator player as the last (?) player in the tablist, however there isn't any
        // sane way around this.
        sendPacket(
            ServerPlayerListEntryPacket(
                PlayerListEntryAction.ADD_PLAYER,
                arrayOf(
                    PlayerListEntry(
                        session.getFlag(
                            MinecraftConstants.PROFILE_KEY
                        ), GameMode.SPECTATOR
                    )
                )
            )
        )
    }

    override val isPlayer: Boolean
        get() = true

    companion object {
        const val SESSION_FLAG = "replay_user"
        const val SERVER_FLAG = "replay_server"
        private val logger =
            LogManager.getLogger(ReplayUser::class)
    }

    init {
        session.setFlag(SESSION_FLAG, this)
        session.addListener(this)
    }
}