package io.github.nickacpt.replayserver.replay.selectors

import com.github.steveice10.mc.protocol.data.game.chunk.Chunk
import com.github.steveice10.mc.protocol.data.game.chunk.Column
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode
import com.github.steveice10.mc.protocol.data.game.window.WindowType
import com.github.steveice10.mc.protocol.data.game.world.WorldType
import com.github.steveice10.mc.protocol.data.game.world.block.BlockChangeRecord
import com.github.steveice10.mc.protocol.data.game.world.block.BlockState
import com.github.steveice10.mc.protocol.data.message.TextMessage
import com.github.steveice10.mc.protocol.packet.ingame.client.window.ClientCloseWindowPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.window.ClientWindowActionPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerCloseWindowPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerBlockChangePacket
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerChunkDataPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerSpawnPositionPacket
import com.github.steveice10.opennbt.tag.builtin.CompoundTag
import com.github.steveice10.opennbt.tag.builtin.LongArrayTag
import com.github.steveice10.packetlib.packet.Packet
import com.google.common.util.concurrent.ListenableFuture
import com.replaymod.replayserver.api.IConnectedPlayer
import com.replaymod.replayserver.api.IReplaySelector
import io.github.nickacpt.replayserver.inventory.ComplexItemStack
import io.github.nickacpt.replayserver.inventory.Inventory
import io.github.nickacpt.replayserver.inventory.Material
import net.kyori.text.TextComponent
import net.kyori.text.format.TextColor
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class PlayerReplaySelector : IReplaySelector {
    lateinit var playerSelectorCompletableFuture: PlayerSelectorCompletableFuture

    class PlayerSelectorCompletableFuture(var user: IConnectedPlayer) : ListenableFuture<String?> {
        lateinit var runnable: Runnable
        var result: String = ""
        private lateinit var executor: Executor
        var replays = user.replayDatabase?.getAvailableReplays()

        override fun addListener(p0: Runnable, p1: Executor) {
            runnable = p0
            executor = p1

            kotlin.run {
                spawnPlayer()
                sendWindow()
            }
        }

        private fun sendWindow() {
            val inv = Inventory(WindowType.GENERIC_9X3)
            replays?.withIndex()?.forEach {
                val item = ComplexItemStack(Material.PAPER, it.index + 1)
                item.name = TextComponent.of(it.value)
                item.lore.apply { clear() }.add(TextComponent.of("Click to watch this replay!", TextColor.GREEN))

                inv.setSlot(it.index, item)
            }
            inv.show(user)
        }

        override fun isDone(): Boolean {
            return result.isNotEmpty()
        }

        override fun get(): String {
            return result
        }

        override fun get(timeout: Long, unit: TimeUnit): String {
            return get()
        }

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            return false
        }

        override fun isCancelled(): Boolean {
            return false
        }

        private fun spawnPlayer() {
            user.sendPacket(
                ServerJoinGamePacket(
                    -1234567, false, GameMode.ADVENTURE, 0, 100,
                    WorldType.DEFAULT, 1, false
                )
            )

            sendPlayerChunkData()

            //Stone block below player
            val record =
                BlockChangeRecord(Position(0, 99, 0), BlockState(1))

            user.sendPacket(ServerBlockChangePacket(record))

            //Send spawn position to player
            user.sendPacket(ServerSpawnPositionPacket(Position(0, 100, 0)))

            user.sendPacket(ServerPlayerPositionRotationPacket(0.5, 100.0, 0.5, 90F, 0F, 1))

        }

        private fun sendPlayerChunkData() {
            // Send chunk data
            val chunk = Chunk()
            for (x in -1..1) {
                for (z in -1..1) {
                    run {
                        val col =
                            Column(
                                x, z, arrayOf(
                                    chunk, chunk, chunk, chunk, chunk, chunk, chunk, chunk,
                                    chunk, chunk, chunk, chunk, chunk, chunk, chunk, chunk
                                ), emptyArray(), CompoundTag(
                                    "MOTION_BLOCKING",
                                    mapOf((Pair("MOTION_BLOCKING", LongArrayTag("MOTION_BLOCKING", LongArray(36)))))
                                ), IntArray(256)
                            )
                        user.sendPacket(ServerChunkDataPacket(col))
                    }
                }
            }
        }

        fun handlePacket(packet: Packet): Boolean {
            if (packet is ClientCloseWindowPacket) {
                user.kick(TextMessage("Please pick a replay to watch!"))
                return true
            } else if (packet is ClientWindowActionPacket) {
                if (packet.windowId != 0) {
                    val res = replays?.getOrNull(packet.slot)
                    if (res != null) {
                        user.sendPacket(ServerCloseWindowPacket(packet.windowId))
                        //Notify of result
                        result = res
                        executor.execute(runnable)
                        sendPlayerChunkData()
                    }
                }
            }
            return false
        }
    }

    override fun handlePacket(user: IConnectedPlayer, packet: Packet): Boolean? {
        if (!playerSelectorCompletableFuture.isDone)
            return playerSelectorCompletableFuture.handlePacket(packet)
        return false
    }

    override fun getReplayId(user: IConnectedPlayer): ListenableFuture<String?>? {
        playerSelectorCompletableFuture = PlayerSelectorCompletableFuture(user)
        return playerSelectorCompletableFuture
    }
}