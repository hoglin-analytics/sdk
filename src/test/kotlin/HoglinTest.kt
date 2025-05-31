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