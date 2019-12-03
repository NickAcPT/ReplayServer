package io.github.nickacpt.replayserver.sender

import com.github.steveice10.mc.protocol.data.game.MessageType
import com.github.steveice10.mc.protocol.data.message.Message

interface ICommandSender {
    /**
     * Sends a chat message to the user.
     * Equivalent to calling [.sendMessage] with [MessageType.CHAT].
     * @param message The message
     */
    fun sendMessage(
        message: String?,
        messageType: MessageType? = MessageType.CHAT
    )

    /**
     * Sends a chat message to the user.
     * Equivalent to calling [.sendMessage] with [MessageType.CHAT].
     * @param message The message
     */
    fun sendMessage(
        message: Message?,
        messageType: MessageType? = MessageType.CHAT
    )

    /**
     * Returns whether this sender is a player.
     * @return `true` if the sender is a player, `false otherwise`
     */
    val isPlayer: Boolean
}