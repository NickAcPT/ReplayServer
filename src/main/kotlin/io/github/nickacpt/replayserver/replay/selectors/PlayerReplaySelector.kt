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
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket
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
import io.github.nickacpt.replayserver.replay.ReplayUser
import net.kyori.text.TextComponent
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class PlayerReplaySelector : IReplaySelector {
    lateinit var playerSelectorCompletableFuture: PlayerSelectorCompletableFuture

    class PlayerSelectorCompletableFuture(var user: IConnectedPlayer) : ListenableFuture<String?> {
        lateinit var runnable: Runnable

        lateinit var executor: Executor

        override fun addListener(p0: Runnable, p1: Executor) {
            runnable = p0
            executor = p1

            kotlin.run {
                spawnPlayer()
                (user as? ReplayUser)?.onPacket<ClientCloseWindowPacket>(Runnable {
                    user.kick(TextMessage("Please pick a replay to watch!"))
                })

                sendWindow()
            }
        }

        private fun sendWindow() {
            val inv = Inventory(WindowType.GENERIC_9X3)
            inv.setSlot(0, ComplexItemStack(Material.PAPER).apply {
                this.name = TextComponent.of("Testing")
                this.lore.add(TextComponent.of("Test message"))
            })
            inv.show(user)
        }

        override fun isDone(): Boolean {
            return false
        }

        override fun get(): String {
            return ""
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
                    -12345, false, GameMode.ADVENTURE, 0, 100,
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
            for (x in -2..1) {
                for (z in -2..1) {
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
            return false
        }
    }

    override fun handlePacket(user: IConnectedPlayer, packet: Packet): Boolean? {
        return playerSelectorCompletableFuture.handlePacket(packet)
    }

    override fun getReplayId(user: IConnectedPlayer): ListenableFuture<String?>? {
        playerSelectorCompletableFuture = PlayerSelectorCompletableFuture(user)
        return playerSelectorCompletableFuture
    }
}