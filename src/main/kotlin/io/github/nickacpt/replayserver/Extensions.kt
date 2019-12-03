package io.github.nickacpt.replayserver

import com.github.steveice10.mc.protocol.data.message.Message
import com.github.steveice10.opennbt.tag.builtin.CompoundTag
import com.github.steveice10.opennbt.tag.builtin.Tag
import net.kyori.text.Component
import net.kyori.text.serializer.gson.GsonComponentSerializer
import net.md_5.bungee.api.ChatColor


operator fun ChatColor.plus(s: String): String? {
    return this.toString() + s
}

fun Component.toMessage() : Message {
    return Message.fromString(serialize())
}

fun Component.serialize() : String {
    return GsonComponentSerializer.INSTANCE.serialize(this)
}

fun Tag.toComponent() : Component {
    return GsonComponentSerializer.INSTANCE.deserialize(this.toString())
}