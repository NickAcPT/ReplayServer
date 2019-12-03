package com.replaymod.replayserver.api

import com.github.steveice10.packetlib.packet.Packet
import com.google.common.util.concurrent.ListenableFuture

/**
 * Selects the replay to use for a particular session.
 */
interface IReplaySelector {
    /**
     * Returns the unique id of the replay to be used for this session of the specified user.
     * If no replay id can be determined, the user shall be kicked using [IConnectedPlayer.kick] and the future shall
     * be resolved to `null`.
     * @param user The connected user1
     * @return Future for the id of the replay
     */
    fun getReplayId(user: IConnectedPlayer): ListenableFuture<String?>?

    /**
     * Handles an incoming packet.
     * @return true if the packet should be canceled, false otherwise
     */
    fun handlePacket(user: IConnectedPlayer, packet: Packet) : Boolean? {
        return false
    }
}