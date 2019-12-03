package io.github.nickacpt.replayserver.inventory

import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack
import com.github.steveice10.mc.protocol.data.game.window.WindowType
import com.github.steveice10.mc.protocol.data.message.TextMessage
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerOpenWindowPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowItemsPacket
import com.github.steveice10.netty.util.collection.IntObjectHashMap
import com.github.steveice10.netty.util.collection.IntObjectMap
import com.github.steveice10.opennbt.tag.builtin.CompoundTag
import com.replaymod.replayserver.api.IConnectedPlayer
import io.github.nickacpt.replayserver.plus
import net.md_5.bungee.api.ChatColor
import org.apache.commons.lang3.reflect.FieldUtils
import kotlin.properties.Delegates

class Inventory {
    constructor(size: Int) {
        this.windowId = getNewWindowId()
        this.windowType = if ((size % 9) == 0 && size <= 54) WindowType.valueOf("GENERIC_9X${size / 9}")
        else throw RuntimeException("Invalid inventory size!")
        this.contents = IntObjectHashMap()
    }

    constructor(type: WindowType) {
        this.windowId = getNewWindowId()
        this.contents = IntObjectHashMap()
        this.windowType = type
    }

    companion object {
        private var windowCount: Int = 0

        private fun getNewWindowId(): Int {
            return ++windowCount
        }
    }

    private var windowType: WindowType
        set(value) {
            field = value
            contentsSize = when (windowType) {
                WindowType.GENERIC_3X3 -> 9
                WindowType.GENERIC_9X1 -> 9
                WindowType.GENERIC_9X2 -> 18
                WindowType.GENERIC_9X3 -> 27
                WindowType.GENERIC_9X4 -> 36
                WindowType.GENERIC_9X5 -> 45
                WindowType.GENERIC_9X6 -> 54
                else -> 0
            }
        }

    private var contentsSize by Delegates.notNull<Int>()

    private val windowId: Int
    var name: String = "Chest"

    private val contents: IntObjectMap<ItemStack>

    fun setSlot(slot: Int, item: ItemStack) {
        contents[slot] = item
    }

    fun show(player: IConnectedPlayer) {

        var abc = ServerOpenWindowPacket(
            windowId,
            windowType,
            TextMessage(ChatColor.BLACK + name).toJsonString()
        )

        player.sendPacket(
            ServerOpenWindowPacket(
                windowId,
                windowType,
                TextMessage(ChatColor.BLACK + name).toJsonString()
            )
        )

        val air = ItemStack(0, 1, CompoundTag(""))
        val items = Array(contentsSize) { return@Array air }
        contents.forEach {
            val slot = it.key
            val item = it.value

            items[slot] = item.apply {
                (this as? ComplexItemStack)?.ensureNbtTagsAreSet()
                if (this.nbt == null) FieldUtils.writeDeclaredField(this, "nbt", CompoundTag(""), true)
            }
        }



        player.sendPacket(ServerWindowItemsPacket(windowId, items))
        player.sendPacket(ServerWindowItemsPacket(windowId, items))
    }

}