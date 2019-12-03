package io.github.nickacpt.replayserver.replay

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode
import com.github.steveice10.mc.protocol.data.game.world.notify.ClientNotification
import com.github.steveice10.mc.protocol.packet.ingame.server.*
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.*
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerCloseWindowPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerOpenWindowPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerSetSlotPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowItemsPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerNotifyClientPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerOpenTileEntityEditorPacket
import com.github.steveice10.packetlib.packet.Packet
import com.github.steveice10.packetlib.tcp.TcpServerSession
import com.google.common.base.Preconditions
import com.google.common.collect.Sets
import com.replaymod.replayserver.api.IReplaySession
import com.replaymod.replaystudio.PacketData
import com.replaymod.replaystudio.io.ReplayInputStream
import com.replaymod.replaystudio.replay.ReplayFile
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI

class ReplaySession(override val user: ReplayUser, override val replayFile: ReplayFile) : IReplaySession {
    private var inputStream: ReplayInputStream? = null
    /**
     * Last timestamp in milliseconds the [.process] method was called.
     * Invalid while [.paused].
     */
    private var nowRealTime: Long = 0
    /**
     * Timestamp when the replay has started, scaled with the current speed.
     * This timestamp changes whenever the speed is changed or when the replay is unpaused.
     * While not paused, the current replay time can be calculated as (nowRealTime - scaledStartTime) * speed
     * Invalid while [.paused].
     */
    private var scaledStartTime = System.currentTimeMillis()
    /**
     * Current replay time.
     * Valid regardless of [.paused].
     */
    override var time = 0
        private set
    /**
     * The next packet to be sent.
     */
    private var nextPacket: PacketData? = null
    /**
     * Whether the world has been loaded (and the user is no longer stuck in a dirt screen).
     */
    private var hasWorldLoaded = false

    /**
     * Resets the scaled start time, so that now replay time has passed since [.nowRealTime].
     */
    private fun resetScaledStartTime() {
        scaledStartTime = (nowRealTime - time / speed).toLong()
    }

    override fun setTime(time: Int, compact: Boolean) {}

    override var speed: Double = 1.0
        set(value) {
            Preconditions.checkArgument(value > 0, "Speed must be positive")
            field = value
            if (!isPaused) resetScaledStartTime()
        }


    override var isPaused: Boolean = false
        set(value) {
            if (field xor value) {
                if (!value) {
                    nowRealTime = System.currentTimeMillis()
                    resetScaledStartTime()
                }
                field = value
            }
        }


    @Throws(IOException::class)
    override fun close() {
        replayFile.close()
    }

    /**
     * Update the current time and send packets accordingly.
     * @return The time in milliseconds until this method should be called again, or 0 if the replay is paused
     */
    @Throws(IOException::class)
    override fun process(now: Long): Long {
        if (isPaused) {
            return 0
        }
        // Update current time
        nowRealTime = now
        val targetReplayTime = ((nowRealTime - scaledStartTime) * speed).toInt()
        if (targetReplayTime < time) { // Need to restart replay to go backwards in time
            if (inputStream != null) {
                inputStream!!.close()
                inputStream = null
                nextPacket = null
            }
        }
        while (true) {
            if (nextPacket == null) {
                if (inputStream == null) {
                    inputStream = replayFile.packetData
                }
                nextPacket = inputStream!!.readPacket()
                if (nextPacket == null) { // Reached end of replay
// TODO event
                    return 0
                }
            }
            nextPacket = if (nextPacket!!.time <= targetReplayTime) {
                processPacket(nextPacket!!.packet)
                null
            } else {
                break
            }
        }
        time = targetReplayTime
        return Math.max(((time - nextPacket!!.time) / speed).toLong(), 1)
    }

    private fun processPacket(packet: Packet) {
        var packet = packet
        if (BAD_PACKETS.contains(packet.javaClass)) {
            return
        }
        if (packet is ServerResourcePackSendPacket) {
            val uri = URI.create(packet.url)

            if (uri.scheme == "replay") {
                val file = replayFile.resourcePackIndex.getOrDefault(uri.host.toInt(), "")
                if (file.isEmpty() || file.contains("..")) {
                    return
                }
                var value = user.session.getFlag<ReplayServer>(ReplayUser.SERVER_FLAG).host
                if (value == "0.0.0.0" && user.session is TcpServerSession && user.session.localAddress is InetSocketAddress)
                    value = ((user.session.localAddress) as InetSocketAddress).address.toString().substring(1);

                val newHost = "http://$value:${user.resourcePackServer.port}/resourcepack/$file"

                packet = ServerResourcePackSendPacket(newHost, packet.hash)
            }
        }

        if (packet is ServerJoinGamePacket) {


            /*
            The following has been taken from the VelocityPowered proxy.
            In order to handle switching to another server, you will need to send three packets:

            - The join game packet from the backend server
            - A respawn packet with a different dimension
            - Another respawn with the correct dimension

            The two respawns with different dimensions are required, otherwise the client gets
            confused.

            Most notably, by having the client accept the join game packet, we can work around the need
            to perform entity ID rewrites, eliminating potential issues from rewriting packets and
            */
            packet = ServerJoinGamePacket(
                -1234567, packet.isHardcore, GameMode.SPECTATOR, packet.dimension, 0,
                packet.worldType, packet.viewDistance, packet.isReducedDebugInfo
            )

            val tempDim = if (packet.dimension == 0) -1 else 0

            user.sendPacket(packet)
            user.sendPacket(ServerRespawnPacket(tempDim, packet.gameMode, packet.worldType))
            user.sendPacket(ServerRespawnPacket(packet.dimension, packet.gameMode, packet.worldType))

            return
        }
        if (packet is ServerRespawnPacket) {
            packet = ServerRespawnPacket(
                packet.dimension,
                GameMode.SPECTATOR,
                packet.worldType
            )
        }
        if (packet is ServerPlayerPositionRotationPacket) {
            hasWorldLoaded = true
        }
        if (packet is ServerNotifyClientPacket) {
            when (packet.notification) {
                ClientNotification.START_RAIN, ClientNotification.STOP_RAIN, ClientNotification.RAIN_STRENGTH, ClientNotification.THUNDER_STRENGTH -> {
                }
                else -> return  // Bed message, change gamemode, etc.
            }
        }
        user.sendPacket(packet)
    }

    companion object {
        /**
         * Packets that are always filtered from the replay.
         */
        private val BAD_PACKETS: Set<Class<*>> =
            Sets.newHashSet<Class<*>>(
                ServerPlayerHealthPacket::class.java,
                ServerOpenWindowPacket::class.java,
                ServerPlayerChangeHeldItemPacket::class.java,
                ServerCloseWindowPacket::class.java,
                ServerSetSlotPacket::class.java,
                ServerWindowItemsPacket::class.java,
                ServerOpenTileEntityEditorPacket::class.java,
                ServerStatisticsPacket::class.java,  //            ServerSetExperiencePacket.class,
                ServerSwitchCameraPacket::class.java,
                ServerPlayerAbilitiesPacket::class.java,
                ServerTitlePacket::class.java,
                ServerPlayerSetExperiencePacket::class.java
//                ServerUnloadChunkPacket::class.java
            )
    }

}