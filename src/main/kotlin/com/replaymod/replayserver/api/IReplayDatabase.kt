package com.replaymod.replayserver.api

import com.replaymod.replaystudio.replay.ReplayFile

/**
 * Given a unique key (or name), instances of this interface provide the replay associated with that id.
 */
interface IReplayDatabase {
    /**
     * Returns a replay file given its unique id.
     * The returned replay file may be readonly and must support all methods until [ReplayFile.close] is called.
     * If the replay file cannot be found, `null` shall be returned and the user shall be kicked.
     * @param id Unique id of the replay file
     * @param user The connecting user
     * @return The replay file
     */
    fun getReplayFile(user: IConnectedPlayer?, id: String?): ReplayFile?

    /**
     * Returns a list of available replay files by their id.
     * @return The available replay files
     */
    fun getAvailableReplays(): List<String>?
}