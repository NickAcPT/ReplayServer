package com.replaymod.replayserver.api

import com.github.steveice10.mc.protocol.data.message.Message
import com.github.steveice10.packetlib.Session
import com.github.steveice10.packetlib.packet.Packet
import com.replaymod.replaystudio.util.Location
import io.github.nickacpt.replayserver.sender.ICommandSender

/**
 * A sender connected to the replay server.
 */
interface IConnectedPlayer : ICommandSender {
    /**
     * Send a packet to the sender.
     * @param packet The packet to send
     */
    fun sendPacket(packet: Packet?)

    /**
     * Teleports the sender to the specified location.
     * @param location The location to teleport to
     */
    fun teleport(location: Location?)

    /**
     * Kicks and disconnects the user.
     * @param message The kick message
     */
    fun kick(message: Message?)

    /**
     * Returns the replay session this use is in.
     * @return The session, may be `null` if the sender hasn't been assigned a replay yet
     */
    val replaySession: IReplaySession?

    /**
     * Returns the network session of this sender.
     * Note: Listeners registered for the session object must never be blocking or interact with any not thread-safe
     * methods as they are called from the network thread.
     * @return The session
     */
    val session: Session?

    /**
     * Returns the replay selector of this player.
     * @return The replay selector used by this player
     */
    var replaySelector: IReplaySelector?

    /**
     * Returns whether this sender is still connected.
     * Once the sender disconnects, this function will never return `true` again.
     * @return `true` if the sender is connected, `false otherwise`
     */
    val isConnected: Boolean


}
