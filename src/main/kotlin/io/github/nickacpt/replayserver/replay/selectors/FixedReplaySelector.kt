package io.github.nickacpt.replayserver.replay.selectors

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.replaymod.replayserver.api.IConnectedPlayer
import com.replaymod.replayserver.api.IReplaySelector

/**
 * A replay selector that always returns the same configurable replay.
 */
class FixedReplaySelector : IReplaySelector {
    private val theId: String = System.getProperty("fixedreplayselector.id", "replay.mcpr")

    override fun getReplayId(user: IConnectedPlayer): ListenableFuture<String?>? {
        return Futures.immediateFuture(theId)
    }

}