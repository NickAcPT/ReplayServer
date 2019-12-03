package io.github.nickacpt.replayserver.resourcepacks

import io.github.nickacpt.replayserver.replay.ReplayUser
import spark.kotlin.Http
import spark.kotlin.ignite
import javax.servlet.ServletOutputStream
import kotlin.properties.Delegates


class ResourcePackServer(var replayUser: ReplayUser) {
    var port by Delegates.notNull<Int>()

    private lateinit var http: Http
    fun start() {
        http = ignite()
        http.get("/resourcepack/:id") {
            val resourcePack = replayUser.replaySession?.replayFile?.getResourcePack(request.params("id"))

            val inputStream = resourcePack?.orNull()

            if (inputStream != null) {

                response.type("application/zip");

                val out: ServletOutputStream = response.raw().outputStream
                inputStream.copyTo(out)
                out.close()

                return@get 200
            } else {
                return@get 404
            }


        }
        http.service.awaitInitialization()

        port = http.port()
    }

    fun stop() {
        http.stop()
    }
}