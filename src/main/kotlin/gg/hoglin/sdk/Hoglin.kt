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

package gg.hoglin.sdk

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
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

data class ExperimentEvaluationResponse(
    val inExperiment: Boolean
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
    enableAutoFlush: Boolean,
    private val gson: Gson
) {
    companion object {
        private val logger = LoggerFactory.getLogger(Hoglin::class.java)
    }

    class Builder(private val serverKey: String) {
        private var baseUrl = "https://api.hoglin.gg"
        private var autoFlushInterval = 30_000L
        private var maxBatchSize = 1_000
        private var enableAutoFlush = true

        private val customSerializers = mutableListOf<Pair<Class<*>, Any>>()

        fun baseUrl(url: String) = apply { this.baseUrl = url }
        fun autoFlushInterval(interval: Long) = apply { this.autoFlushInterval = interval }
        fun maxBatchSize(size: Int) = apply { this.maxBatchSize = size }
        fun enableAutoFlush(enabled: Boolean) = apply { this.enableAutoFlush = enabled }

        /**
         * Register a custom type adapter (serializer) for Gson.
         *
         * @param type The class type to register the adapter for.
         * @param adapter The serializer
         */
        fun <T> registerSerializer(type: Class<T>, adapter: Any) = apply {
            customSerializers.add(type to adapter)
        }

        fun build(): Hoglin {
            val gsonBuilder = GsonBuilder()

            for ((type, adapter) in customSerializers) {
                gsonBuilder.registerTypeAdapter(type, adapter)
            }

            val gson = gsonBuilder.create()
            return Hoglin(
                serverKey,
                baseUrl,
                autoFlushInterval,
                maxBatchSize,
                enableAutoFlush,
                gson
            )
        }
    }

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
     * Checks if a player is in an experiment asynchronously
     *
     * @param experimentId - The experiment ID to check
     * @param playerUUID - The player UUID (null for non-player-specific rollout)
     * @param callback - Callback function that receives the result (true if in experiment, false otherwise)
     */
    fun hasExperiment(experimentId: String, playerUUID: UUID?, callback: (Boolean) -> Unit) {
        coroutineScope.launch {
            val result = internalEvaluateExperiment(experimentId, playerUUID)
            callback(result)
        }
    }

    fun hasExperimentSync(experimentId: String, playerUUID: UUID? = null): Boolean =
        runBlocking { internalEvaluateExperiment(experimentId, playerUUID) }

    /**
     * Overloaded version for non-player-specific experiments
     */
    fun hasExperiment(experimentId: String, callback: (Boolean) -> Unit) = hasExperiment(experimentId, null, callback)

    private suspend fun internalEvaluateExperiment(experimentId: String, playerUUID: UUID?): Boolean {
        if (isShuttingDown.get()) {
            logger.warn("Cannot evaluate experiment '{}', SDK is shutting down", experimentId)
            return false
        }

        logger.debug("Evaluating experiment: experimentId={}, playerUUID={}", experimentId, playerUUID)

        val url = buildString {
            append("$baseUrl/experiments/$serverKey/$experimentId/evaluate")
            if (playerUUID != null) {
                append("?playerUUID=$playerUUID")
            }
        }

        val (_, response, result) = Fuel.get(url).awaitStringResponseResult()

        return result.fold(
            success = { responseBody ->
                try {
                    val evaluationResponse = gson.fromJson(responseBody, ExperimentEvaluationResponse::class.java)
                    logger.trace("Experiment evaluation successful: experimentId={}, inExperiment={}",
                        experimentId, evaluationResponse.inExperiment)
                    evaluationResponse.inExperiment
                } catch (e: Exception) {
                    logger.error("Failed to parse experiment evaluation response", e)
                    false
                }
            },
            failure = { error ->
                val errorMessage = if (response.data.isNotEmpty()) {
                    parseErrorResponse(String(response.data))
                } else {
                    error.message ?: "Network error"
                }

                logger.error("Failed to evaluate experiment '{}' (HTTP {}): {}",
                    experimentId, response.statusCode, errorMessage)
                false
            }
        )
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

    fun flushSync(): FlushResult = runBlocking { flush() }

    /**
     * Gracefully stops all SDK events
     *
     * - Cancels the auto flush job
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