package io.github.nickacpt.replayserver.inventory

import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack
import com.github.steveice10.opennbt.tag.builtin.CompoundTag
import com.github.steveice10.opennbt.tag.builtin.ListTag
import com.github.steveice10.opennbt.tag.builtin.StringTag
import io.github.nickacpt.replayserver.serialize
import io.github.nickacpt.replayserver.toComponent
import io.github.nickacpt.replayserver.toMessage
import net.kyori.text.Component
import net.kyori.text.TextComponent

class ComplexItemStack(material: Material, var itemAmount: Int = 1) : ItemStack(material.id, itemAmount) {

    init {
        prepareNbtTags()
    }

    var name: Component? = null

    fun ensureNbtTagsAreSet() {
        applyDisplayNameComponent()
        applyDisplayLoreComponent()
    }

    private fun applyDisplayNameComponent() {
        if (name != null)
            nbt.get<CompoundTag>(DisplayTag).put(
                StringTag(
                    NameTag,
                    TextComponent.of("§r").append(name!!).serialize()
                )
            )
        else
            nbt.get<CompoundTag>(DisplayTag).remove(NameTag)
    }

    private fun applyDisplayLoreComponent() {
        if (lore.isNotEmpty())
            nbt.get<CompoundTag>(DisplayTag).put(ListTag(LoreTag, lore.map { StringTag("", TextComponent.of("§r").append(it).serialize()) }.toList())
            )
        else
            nbt.get<CompoundTag>(DisplayTag).remove(LoreTag)
    }

    var lore: MutableList<Component> = mutableListOf()

    private fun prepareNbtTags() {
        if (!nbt.contains(DisplayTag)) {
            nbt.put(CompoundTag(DisplayTag))
        }
    }
}