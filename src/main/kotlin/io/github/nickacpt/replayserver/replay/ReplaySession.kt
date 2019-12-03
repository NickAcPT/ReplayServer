package io.github.nickacpt.replayserver.replay

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode
import com.github.steveice10.mc.protocol.data.game.world.notify.ClientNotification
import com.github.steveice10.mc.protocol.packet.ingame.server.*
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerAbilitiesPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerCloseWindowPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerOpenWindowPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerSetSlotPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowItemsPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerNotifyClientPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerOpenTileEntityEditorPacket
import com.github.steveice10.packetlib.packet.Packet
import com.google.common.base.Preconditions
import com.google.common.collect.Sets
import com.replaymod.replayserver.api.IReplaySession
import com.replaymod.replaystudio.PacketData
import com.replaymod.replaystudio.io.ReplayInputStream
import com.replaymod.replaystudio.replay.ReplayFile
import java.io.IOException

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
        if (packet is ServerResourcePackSendPacket) { // TODO
        }
        if (packet is ServerJoinGamePacket) {
            val p =
                packet
            // Change entity id to invalid value and force gamemode to spectator
            packet = ServerJoinGamePacket(
                -1789435,
                p.isHardcore,
                GameMode.SPECTATOR,
                p.dimension,
                p.maxPlayers,
                p.worldType,
                p.viewDistance,
                p.isReducedDebugInfo
            )
        }
        if (packet is ServerRespawnPacket) {
            val p =
                packet
            // Force gamemode to spectator
            packet = ServerRespawnPacket(
                p.dimension,
                GameMode.SPECTATOR,
                p.worldType
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
            Sets.newHashSet<Class<*>>( //            ServerUpdateHealthPacket.class,
                ServerOpenWindowPacket::class.java,
                ServerCloseWindowPacket::class.java,
                ServerSetSlotPacket::class.java,
                ServerWindowItemsPacket::class.java,
                ServerOpenTileEntityEditorPacket::class.java,
                ServerStatisticsPacket::class.java,  //            ServerSetExperiencePacket.class,
//            ServerUpdateHealthPacket.class,
//            ServerChangeHeldItemPacket.class,
                ServerSwitchCameraPacket::class.java,
                ServerPlayerAbilitiesPacket::class.java,
                ServerTitlePacket::class.java
            )
    }

}