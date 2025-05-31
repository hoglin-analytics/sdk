package gg.hoglin.sdk

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

data class AnalyticsEvent(
    val timestamp: String,
    @SerializedName("event_type")
    val eventType: String,
    val properties: Map<String, Any> = emptyMap()
)

data class ErrorDetail(
    val field: String,
    val message: String
)

data class ApiErrorResponse(
    val error: String,
    val details: List<ErrorDetail>? = null,
    val message: String? = null
)

sealed class FlushResult {
    data object Success : FlushResult()
    data class Error(val message: String, val isRetryable: Boolean = true) : FlushResult()
}

class Hoglin private constructor(
    private val serverKey: String,
    private val baseUrl: String,
    private val autoFlushInterval: Long,
    private val maxBatchSize: Int,
    private val enableAutoFlush: Boolean
) {
    companion object {
        private val logger = LoggerFactory.getLogger(Hoglin::class.java)
    }

    class Builder(private val serverKey: String) {
        private var baseUrl = "http://localhost:3000"
        private var autoFlushInterval = 30_000L
        private var maxBatchSize = 1_000
        private var enableAutoFlush = true

        fun baseUrl(url: String) = apply { this.baseUrl = url }
        fun autoFlushInterval(interval: Long) = apply { this.autoFlushInterval = interval }
        fun maxBatchSize(size: Int) = apply { this.maxBatchSize = size }
        fun enableAutoFlush(enabled: Boolean) = apply { this.enableAutoFlush = enabled }

        fun build() = Hoglin(serverKey, baseUrl, autoFlushInterval, maxBatchSize, enableAutoFlush)
    }

    private val gson = Gson()
    private val eventQueue = ConcurrentLinkedQueue<AnalyticsEvent>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isShuttingDown = AtomicBoolean(false)
    private var autoFlushJob: Job? = null

    init {
        logger.trace("Initializing Hoglin SDK with baseUrl={}, autoFlushInterval={}ms, maxBatchSize={}, enableAutoFlush={}",
            baseUrl, autoFlushInterval, maxBatchSize, enableAutoFlush)
        if (enableAutoFlush) startAutoFlush()
    }

    private fun startAutoFlush() {
        autoFlushJob = coroutineScope.launch {
            logger.debug("Starting auto-flush coroutine with interval {}ms", autoFlushInterval)
            while (!isShuttingDown.get()) {
                try {
                    delay(autoFlushInterval)
                    if (eventQueue.isNotEmpty()) {
                        logger.debug("Auto-flush triggered with {} events queued", eventQueue.size)
                        flush()
                    }
                } catch (e: CancellationException) {
                    logger.debug("Auto-flush coroutine cancelled")
                    break
                } catch (e: Exception) {
                    logger.error("Error during auto-flush", e)
                }
            }
        }
    }

    /**
     * Adds an event into the Hoglin Queue
     *
     * @param eventType - The type of event, this string can be anything identifiable
     * @param properties - An optional list of properties to add onto this tracked event
     */
    fun track(eventType: String, properties: Map<String, Any> = emptyMap()) {
        logger.debug("Tracking event: type={}, properties={}", eventType, properties)

        if (isShuttingDown.get()) {
            logger.warn("Cannot track event '{}', SDK is shutting down", eventType)
            return
        }

        val event = AnalyticsEvent(
            timestamp = Instant.now().toString(),
            eventType = eventType,
            properties = properties
        )
        eventQueue.offer(event)

        logger.trace("Event queued. Current queue size: {}", eventQueue.size)

        // if the queue size is reached, flush all the events
        // (regardless of if auto-flush is disabled)
        if (eventQueue.size >= maxBatchSize) {
            coroutineScope.launch {
                logger.trace("Batch size limit reached ({}), triggering immediate flush", eventQueue.size)
                flush()
            }
        }
    }

    /**
     * Parses an error response body
     */
    private fun parseErrorResponse(responseBody: String): String {
        return try {
            val errorResponse = gson.fromJson(responseBody, ApiErrorResponse::class.java)
            buildString {
                append(errorResponse.error)
                errorResponse.details?.let { details ->
                    if (details.isNotEmpty()) {
                        append(": ")
                        append(details.joinToString(", ") { "${it.field} - ${it.message}" })
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to parse error response as JSON", e)
            responseBody.takeIf { it.isNotBlank() } ?: "Unknown error"
        }
    }

    /**
     * Manually triggers a flush of the queue and pushes all events into Hoglin
     */
    suspend fun flush(): FlushResult {
        if (eventQueue.isEmpty()) {
            logger.trace("Flush called but queue is empty")
            return FlushResult.Success
        }

        val events = mutableListOf<AnalyticsEvent>()

        // allows us to only take the configured batch size, rather than everything
        // the leftovers will be sent in the next flush
        repeat(maxBatchSize) {
            eventQueue.poll()?.let { events.add(it) }
        }

        if (events.isEmpty()) {
            logger.trace("No events to flush after polling queue")
            return FlushResult.Success
        }

        val jsonBody = gson.toJson(events)
        logger.trace("Sending {} events to analytics endpoint", events.size)

        val (_, response, result) = Fuel.put("$baseUrl/analytics/$serverKey")
            .jsonBody(jsonBody)
            .awaitStringResponseResult()

        return result.fold(
            success = { _ ->
                logger.trace("Successfully sent {} events (HTTP {})", events.size, response.statusCode)
                FlushResult.Success
            },
            failure = { error ->
                val errorMessage = if (response.data.isNotEmpty()) {
                    parseErrorResponse(String(response.data))
                } else {
                    error.message ?: "Network error"
                }

                logger.error("Failed to send {} events (HTTP {}): {}",
                    events.size, response.statusCode, errorMessage)
                logger.debug("Full error details", error)

                val isRetryable = response.statusCode in listOf(-1, 408, 429, 500, 502, 503, 504)

                if (isRetryable) {
                    logger.warn("Error is retryable, re-queuing {} events", events.size)
                    events.reversed().forEach { eventQueue.offer(it) }
                } else {
                    logger.error("Error is not retryable, {} events will be dropped", events.size)
                }

                FlushResult.Error(errorMessage, isRetryable)
            }
        )
    }

    fun flushAsync(onResult: ((FlushResult) -> Unit)? = null) {
        logger.debug("Starting async flush")
        coroutineScope.launch {
            val result = flush()
            onResult?.invoke(result)
            logger.debug("Async flush completed with result: {}", result::class.simpleName)
        }
    }

    /**
     * Gracefully stops all SDK events
     *
     * - Cancels the auto flush j ob
     * - Attempts to flush all remaining events
     */
    suspend fun shutdown() {
        logger.trace("Shutting down Hoglin SDK...")
        isShuttingDown.set(true)

        autoFlushJob?.cancel()
        logger.debug("Auto-flush job cancelled")

        var attempts = 0
        val maxAttempts = 3
        while (eventQueue.isNotEmpty() && attempts < maxAttempts) {
            attempts++
            logger.trace("Final flush attempt {}/{}, {} events remaining", attempts, maxAttempts, eventQueue.size)

            when (val result = flush()) {
                is FlushResult.Success -> {
                    if (eventQueue.isEmpty()) {
                        logger.trace("All events flushed successfully during shutdown")
                        break
                    }
                }
                is FlushResult.Error -> {
                    logger.error("Error during shutdown flush attempt {}: {}", attempts, result.message)
                    if (!result.isRetryable) {
                        logger.warn("Error is not retryable, stopping flush attempts")
                        break
                    }
                }
            }

            if (attempts < maxAttempts) delay(1000)
        }

        if (eventQueue.isNotEmpty()) {
            logger.warn("Shutdown complete with {} events still in queue", eventQueue.size)
        }

        coroutineScope.cancel()
        logger.trace("Hoglin SDK shutdown complete")
    }

    fun shutdownBlocking() {
        logger.debug("Starting blocking shutdown")
        runBlocking { shutdown() }
    }
}

