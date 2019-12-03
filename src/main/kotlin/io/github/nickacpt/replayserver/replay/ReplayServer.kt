@file:Suppress("UnstableApiUsage")

package io.github.nickacpt.replayserver.replay

import com.github.steveice10.mc.protocol.MinecraftConstants
import com.github.steveice10.mc.protocol.ServerLoginHandler
import com.github.steveice10.mc.protocol.data.game.PlayerListEntry
import com.github.steveice10.mc.protocol.data.game.PlayerListEntryAction
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode
import com.github.steveice10.mc.protocol.data.game.world.notify.ClientNotification
import com.github.steveice10.mc.protocol.data.message.TextMessage
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientChatPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerPlayerListEntryPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerNotifyClientPacket
import com.github.steveice10.packetlib.Server
import com.github.steveice10.packetlib.Session
import com.github.steveice10.packetlib.SessionFactory
import com.github.steveice10.packetlib.event.server.*
import com.github.steveice10.packetlib.packet.Packet
import com.github.steveice10.packetlib.packet.PacketProtocol
import com.google.common.util.concurrent.Futures
import com.replaymod.replayserver.api.IPacketHandler
import com.replaymod.replayserver.api.IReplayDatabase
import com.replaymod.replayserver.api.IReplaySelector
import io.github.nickacpt.replayserver.replay.selectors.FileReplayDatabase
import io.github.nickacpt.replayserver.replay.selectors.PlayerReplaySelector
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.concurrent.Executors

class ReplayServer(
    host: String?,
    port: Int,
    protocol: Class<out PacketProtocol?>?,
    factory: SessionFactory?
) : Server(host, port, protocol, factory),
    ServerListener {
    private val logger = LogManager.getLogger(javaClass.name)
    private val threadPool = Executors.newCachedThreadPool()
    private val packetHandlers: List<IPacketHandler> = ArrayList()

    override fun serverBound(event: ServerBoundEvent) {
        logger.info("Server bound")
    }

    override fun serverClosing(event: ServerClosingEvent) {
        logger.info("Server closing")
    }

    override fun serverClosed(event: ServerClosedEvent) {
        logger.info("Server closed")
    }

    override fun sessionAdded(event: SessionAddedEvent) {
        logger.info("New session: " + event.session)
        threadPool.submit(ReplayUser(this, event.session))
    }

    override fun sessionRemoved(event: SessionRemovedEvent) {
        logger.info("Session removed: " + event.session)
    }

    fun notifyPacketHandlers(user: ReplayUser?, packet: Packet?) {
        if (packet == null) return;

        for (packetHandler in packetHandlers) {
            packetHandler.handleMessage(user, packet)
        }

        user?.packetListeners?.get(packet::class.java)?.forEach { it.run() }

        if (user?.replaySelector?.handlePacket(user, packet) == true) return


        if (packet is ClientChatPacket) {

            user?.sendPacket(
                ServerPlayerListEntryPacket(
                    PlayerListEntryAction.UPDATE_GAMEMODE,
                    arrayOf(
                        PlayerListEntry(
                            user.session.getFlag(
                                MinecraftConstants.PROFILE_KEY
                            ), GameMode.CREATIVE
                        )
                    )
                )
            )

            user?.sendPacket(ServerNotifyClientPacket(ClientNotification.CHANGE_GAMEMODE, GameMode.CREATIVE))
//            if (message.equals(".")) {
//                user.getReplaySession().setPaused(!user.getReplaySession().isPaused());
//            } else {
//                user.getReplaySession().setSpeed(3);
//            }
        }
    }

    init {
        // TODO config
//        val selector = FixedReplaySelector::class.java.name
        val selector = PlayerReplaySelector::class.java.name
        val database = FileReplayDatabase::class.java.name
        val replaySelector = Class.forName(selector).getDeclaredConstructor().newInstance() as IReplaySelector
        val replayDatabase = Class.forName(database).getDeclaredConstructor().newInstance() as IReplayDatabase

        setGlobalFlag(MinecraftConstants.VERIFY_USERS_KEY, false)
        setGlobalFlag(MinecraftConstants.SERVER_COMPRESSION_THRESHOLD, 100)
        setGlobalFlag(
            MinecraftConstants.SERVER_LOGIN_HANDLER_KEY,
            ServerLoginHandler { session: Session ->
                val user = session.getFlag<ReplayUser>(ReplayUser.SESSION_FLAG) ?: return@ServerLoginHandler
                session.setFlag(ReplayUser.SERVER_FLAG, this)

                user.replaySelector = replaySelector
                user.replayDatabase = replayDatabase

                val idFuture = replaySelector.getReplayId(user)
                idFuture?.addListener(Runnable {
                    val id = Futures.getUnchecked(idFuture)
                    logger.info("Replay id for user $user determined to be $id")
                    if (id == null) {
                        user.kick(TextMessage("No such replay."))
                    } else {
                        val replayFile = replayDatabase.getReplayFile(user, id)
                        logger.info("Replay for user $user fetched from database: $replayFile")
                        if (replayFile == null) {
                            user.kick(TextMessage("Replay file not found."))
                        } else {
                            user.init(replayFile)
                        }
                    }
                }, user)
            }
        )
        addListener(this)
    }
}