/**
 *
 * Hoglin - analytics platform geared towards Minecraft
 * Copyright (C) 2025 flowergardn
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

import org.slf4j.LoggerFactory
import gg.hoglin.sdk.Hoglin
import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

fun main() = runBlocking {
    val logger = LoggerFactory.getLogger("HoglinExample")
    val dotenv = Dotenv.configure()
        .directory("src/test/resources")
        .filename(".env")
        .load()

    logger.info("Starting Hoglin SDK example")

    val analytics = Hoglin.Builder(dotenv["HOGLIN_SECRET_KEY"])
        .baseUrl("http://localhost:3000")
        .autoFlushInterval(5_000L)
        .maxBatchSize(50)
        .enableAutoFlush(true)
        .build()

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutdown hook triggered")
        analytics.shutdownBlocking()
    })

    logger.info("Tracking example event")
    analytics.track("player_action", mapOf(
        "action" to "block_place",
        "block_type" to "stone",
    ))

    logger.info("Waiting for flush....")

    delay(6000)

    logger.info("Example completed")
}