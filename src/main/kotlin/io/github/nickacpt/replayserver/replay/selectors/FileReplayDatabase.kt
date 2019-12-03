package io.github.nickacpt.replayserver.replay.selectors

import com.github.steveice10.mc.protocol.data.message.TextMessage
import com.google.common.io.PatternFilenameFilter
import com.replaymod.replayserver.api.IConnectedPlayer
import com.replaymod.replayserver.api.IReplayDatabase
import com.replaymod.replaystudio.replay.ReplayFile
import com.replaymod.replaystudio.replay.ZipReplayFile
import com.replaymod.replaystudio.studio.ReplayStudio
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import java.io.File
import java.io.FilenameFilter
import java.io.IOException

/**
 * A replay database serving replay ids as files from a configurable folder.
 */
class FileReplayDatabase : IReplayDatabase {
    private val folder: File = File(System.getProperty("filereplaydatabase.folder", "replays"))

    override fun getReplayFile(user: IConnectedPlayer?, id: String?): ReplayFile? {
        if (id == null) {
            logger.info("User disconnected due to non selected replay: ")
            user!!.kick(TextMessage("No replay file specified."))
            return null
        }

        val replayStudio = ReplayStudio()
        replayStudio.isWrappingEnabled = false // Server does not support wrapping
        val file = File(folder, id)
        if (!file.exists() || !file.isFile) {
            logger.info("User disconnected due to non existant replay: " + file.absolutePath)
            user!!.kick(TextMessage("No such replay: $id"))
            return null
        }
        return try {
            ZipReplayFile(replayStudio, File(folder, id))
        } catch (e: IOException) {
            logger.log(
                Level.WARN,
                "Error creating replay file with id $id",
                e
            )
            user!!.kick(TextMessage("Replay file corrupted: $id"))
            null
        }
    }

    override fun getAvailableReplays(): List<String>? {
        return folder.listFiles { dir, name -> name.endsWith(".mcpr") }?.map { it.name }?.toList()
    }

    companion object {
        private val logger = LogManager.getLogger(
            FileReplayDatabase::class.java.name
        )
    }

    init {
        require(folder.exists()) { "Folder does not exists: " + folder.absolutePath }
    }
}