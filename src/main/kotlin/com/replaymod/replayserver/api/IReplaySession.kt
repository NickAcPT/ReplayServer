package com.replaymod.replayserver.api

import com.replaymod.replaystudio.replay.ReplayFile
import java.io.IOException

/**
 * A replay session.
 */
interface IReplaySession {
    /**
     * Returns the user in this session.
     * @return The user
     */
    val user: IConnectedPlayer?

    /**
     * Returns the replay file played in this session.
     * @return The replay file
     */
    val replayFile: ReplayFile?

    /**
     * Returns the timestamp in the replay.
     * @return Timestamp in milliseconds
     */
    val time: Int

    /**
     * Set the timestamp in the replay.
     *
     * Note that this method has to process all packets from the current timestamp to the target one or in case of
     * jumping backwards in time, every packet from the start to the target timestamp.
     *
     * To not transmit unnecessary world changes, the packets can first be compacted on the server side before sending.
     * This will however put significant load on the server as it has to retain a part of the replay in memory while
     * processing it (the world in particular and other misc. packets).
     *
     * To prevent the player from getting stuck inside a Downloading Terrain screen, this may jump further than actually
     * specified. The actual timestamp may subsequently be obtained by calling [.getTime].
     *
     * @param compact Whether packets should be compacted on the server side before sending
     */
    fun setTime(time: Int, compact: Boolean)

    /**
     * Returns the speed at which the replay is played.
     * This value is ignored while the replay [.isPaused].
     * @return The speed, 1 being normal, 2 being twice as fast, 0.5 being half as fast
     */
    /**
     * Sets the speed at which the replay is played.
     * This value is ignored while the replay [.isPaused].
     * @param speed The speed, 1 being normal, 2 being twice as fast, 0.5 being half as fast
     */
    var speed: Double

    /**
     * Returns whether the playback is currently paused.
     * @return `true` if playback is paused, `false` otherwise
     */
    /**
     * Sets whether the playback should be paused.
     * @param paused `true` if playback should be paused, `false` otherwise
     */
    var isPaused: Boolean

    @Throws(IOException::class)
    fun close()

    /**
     * Update the current time and send packets accordingly.
     * @return The time in milliseconds until this method should be called again, or 0 if the replay is paused
     */
    @Throws(IOException::class)
    fun process(now: Long): Long
}