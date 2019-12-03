package com.replaymod.replayserver.api

import com.github.steveice10.packetlib.packet.Packet

/**
 * Handles packets received from users.
 */
interface IPacketHandler {
    /**
     * Handle a packet received from a user.
     * All packet handlers are called sequentially, one after another.
     * Be aware that performing blocking operations during this method will prevent the replay from being played
     * properly.
     * @param packet The packet
     */
    fun handleMessage(user: IConnectedPlayer?, packet: Packet?)
}