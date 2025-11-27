package gg.hoglin.sdk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import gg.hoglin.sdk.models.analytic.Analytic;
import gg.hoglin.sdk.models.analytic.NamedAnalytic;
import gg.hoglin.sdk.models.analytic.RecordedAnalytic;
import gg.hoglin.sdk.models.error.ApiErrorResponse;
import gg.hoglin.sdk.models.experiment.ExperimentEvaluation;
import gg.hoglin.sdk.models.visualization.VisualizationImport;
import gg.hoglin.sdk.strategy.HoglinRetryStrategy;
import gg.hoglin.sdk.serialization.HoglinAdapter;
import gg.hoglin.sdk.task.AnalyticBatchTask;
import kong.unirest.core.*;
import lombok.*;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.WillClose;
import java.beans.PersistenceDelegate;
import java.io.Closeable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Hoglin instance
 */
@Builder(builderMethodName = "", buildMethodName = "_build")
@Accessors(fluent = true)
@Getter
@ToString
public class Hoglin implements Closeable {
    /**
     * The default API endpoint for Hoglin
     */
    public static final String DEFAULT_BASE_URL = "https://api.hoglin.gg";

    /**
     * The default thread factory for Hoglin
     */
    public static final ThreadFactory DEFAULT_THREAD_FACTORY = Thread.ofVirtual().name("Hoglin").factory();

    /**
     * The logger used for Hoglin
     */
    private static final Logger logger = LoggerFactory.getLogger(Hoglin.class);

    /**
     * The server key for Hoglin
     */
    @ToString.Exclude
    @NotNull private String serverKey;

    /**
     * How often (ms) to send queued events
     */
    @Builder.Default private long autoFlushInterval = 15000;

    /**
     * Max events sent per batch
     */
    @Builder.Default private int maxBatchSize = 10000;

    /**
     * Whether to auto-flush events
     */
    @Builder.Default private boolean enableAutoFlush = true;

    /**
     * Whether to requeue failed flushes. When true, if a flush fails (network failure, server error, etc.), all the
     * events will be added back to the *end* of the event queue to be retried later.
     */
    @Builder.Default private boolean requeueFailedFlushes = true;

    /**
     * The API endpoint for Hoglin
     */
    @Builder.Default @NotNull private String baseUrl = DEFAULT_BASE_URL;

    /**
     * The executor used to create threads for Hoglin
     */
    @ToString.Exclude
    @Builder.Default @NotNull private ScheduledExecutorService executor = Executors.newScheduledThreadPool(8, DEFAULT_THREAD_FACTORY);

    /**
     * The {@link UnirestInstance} used for making HTTP requests
     */
    @ToString.Exclude
    @WillClose
    private UnirestInstance httpClient;

    /**
     * The Gson instance used for serialization and deserialization
     */
    @ToString.Exclude
    @Builder.Default @NotNull private Gson gson = createDefaultGson();

    @ToString.Exclude
    private final Queue<RecordedAnalytic<?>> eventQueue = new LinkedList<>();

    @ToString.Exclude
    private @Nullable ScheduledFuture<?> autoFlushTask = null;

    private boolean closed = false;

    private void init() {
        if (httpClient == null) {
            httpClient = createDefaultHttpClient(baseUrl);
        }

        if (enableAutoFlush) {
            autoFlushTask = executor.scheduleAtFixedRate(new AnalyticBatchTask(this), autoFlushInterval, autoFlushInterval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Creates a new Hoglin instance with the provided API key.
     *
     * @param apiKey The API key for Hoglin
     * @return A new Hoglin instance
     */
    public static HoglinBuilder builder(@NotNull String apiKey) {
        return new HoglinBuilder().serverKey(apiKey);
    }

    /**
     * Flushes the event queue immediately. This method is typically used to ensure that all pending events are sent
     * to the Hoglin API without waiting for the auto-flush interval and being limited by the max batch size
     *
     * @apiNote this method makes a blocking http request
     * @return The {@link HttpResponse} from the Hoglin API, or null if there are no queued events (no request would've
     * been made)
     */
    @Blocking
    public @Nullable HttpResponse<String> flush() {
        if (closed) {
            throw new IllegalStateException("Attempted to flush events whilst closed");
        }

        return _flush();
    }

    private @Nullable HttpResponse<String> _flush() {
        final int take = eventQueue.size();
        if (take == 0) return null; // No events to flush

        final ArrayList<RecordedAnalytic<?>> events = new ArrayList<>(eventQueue);
        eventQueue.clear();

        final HttpResponse<String> response = httpClient.put("/analytics/" + serverKey)
            .body(gson.toJson(events))
            .asString();

        if (requeueFailedFlushes && !response.isSuccess()) {
            trackMany(events);
        }

        return response;
    }

    /**
     * Clears the event queue, removing all queued events without sending them to the Hoglin API.
     */
    public void clearQueue() {
        eventQueue.clear();
    }

    /**
     * Tracks an analytic event with the given eventType and properties.
     *
     * @param eventType the type of event to track
     * @param properties a map of properties associated with the event
     */
    public void track(@NotNull final String eventType, @NotNull final Map<String, Object> properties) {
        track(new RecordedAnalytic<>(eventType, Instant.now(), properties));
    }

    /**
     * Tracks an analytic event with the given eventType and no properties.
     *
     * @param eventType the type of event to track
     */
    public void track(@NotNull final String eventType) {
        track(new RecordedAnalytic<>(eventType, Instant.now(), Collections.emptyMap()));
    }

    /**
     * Tracks an analytic event with the given eventType and object.
     *
     * @apiNote If the object is an instance of {@link NamedAnalytic}, it will use the event type from this method
     * instead.
     * @param eventType the type of event to track
     * @param analytic the analytic object containing properties
     * @param <T> the type of the analytic object
     * @see #track(NamedAnalytic)
     */
    public <T extends Analytic> void track(final String eventType, final T analytic) {
        track(new RecordedAnalytic<>(eventType, Instant.now(), analytic));
    }

    /**
     * Tracks an analytic event using a {@link NamedAnalytic} object. The event type will be retrieved from the named
     * analytic object.
     *
     * @param analytic the named analytic object to track
     * @param <T> the type of the named analytic object
     */
    public <T extends NamedAnalytic> void track(final T analytic) {
        track(new RecordedAnalytic<>(analytic.getEventType(), Instant.now(), analytic));
    }

    /**
     * Tracks a recorded analytic event. This method is meant for sending raw event data, consider using the other track
     * methods for convenience unless necessary.
     *
     * @param analytic the recorded analytic event to track
     */
    public void track(final RecordedAnalytic<?> analytic) {
        if (closed) {
            throw new IllegalStateException("Attempted to track event whilst closed");
        }
        eventQueue.add(analytic);
    }

    /**
     * Tracks multiple recorded analytic events at once. This method is useful for manual batching. Consider using the
     * other track methods for convenience unless necessary.
     *
     * @param analytics the collection of recorded analytic events to track
     */
    public void trackMany(final Collection<RecordedAnalytic<?>> analytics) {
        eventQueue.addAll(analytics);
    }

    /**
     * Imports a visualization to the Hoglin dashboard.
     *
     * @param snapshotId the ID of the visualization snapshot to import
     * @param name optional new name for the imported visualization, or null to keep the original name
     * @apiNote This makes a blocking HTTP request to the Hoglin API
     * @return the {@link HttpResponse} from the Hoglin API for any further handling
     */
    public HttpResponse<String> importVisualization(final String snapshotId, @Nullable final String name) {
        if (closed) {
            throw new IllegalStateException("Attempted to import visualization whilst closed");
        }

        final RequestBodyEntity request = httpClient.post("/visualizations/" + serverKey + "/import")
            .body(gson.toJson(new VisualizationImport(snapshotId, name)));

        return request.asString();
    }

    /**
     * Imports a visualization snapshot to the Hoglin dashboard.
     *
     * @param snapshotId the ID of the visualization snapshot to import
     * @apiNote This makes a blocking HTTP request to the Hoglin API
     * @see #importVisualization(String, String) to specify a new name for the imported visualization
     * @return  the {@link HttpResponse} from the Hoglin API for any further handling
     */
    public HttpResponse<String> importVisualization(final String snapshotId) {
        return importVisualization(snapshotId, null);
    }

    /**
     * <p>Evaluates whether the specified experiment is currently enabled for this instance. This is a non-player-specific
     * experiment evaluation and will only evaluate as true if its rollout percentage is set to 100.</p>
     *
     * <p>This is a safe evaluation. If the request to evaluate the experiment fails (invalid experiment id, network
     * error, etc), this method will just return false, with a log in the console. If you would like to handle the
     * response manually, look at {@link Hoglin#evaluateExperimentRaw(String)} </p>
     *
     * @param experimentId the ID of the experiment to evaluate
     * @apiNote This makes a blocking HTTP request to the Hoglin API
     * @see #getExperimentSafe(String, UUID)
     * @see #evaluateExperimentRaw(String)
     * @return true if this instance is part of the experiment, false otherwise
     */
    public boolean evaluateExperiment(final String experimentId) {
        return getExperimentSafe(experimentId, null);
    }

    /**
     * <p>Evaluates whether the player is part of the specified experiment. Unless the player is specifically added to the
     * allowlist for an experiment, they will randomly be pre-selected to be a part of it based on the experiment's
     * rollout percentage.</p>
     *
     * <p>This is a safe evaluation. If the request to evaluate the experiment fails (invalid experiment id, network
     * error, etc), this method will just return false, with a log in the console. If you would like to handle the
     * response manually, look at {@link Hoglin#evaluateExperimentRaw(String, UUID)}  </p>
     *
     * @param experimentId the ID of the experiment to evaluate
     * @param playerUUID the UUID of the player to evaluate the experiment for
     * @apiNote This makes a blocking HTTP request to the Hoglin API
     * @see #getExperimentSafe(String, UUID)
     * @see #evaluateExperimentRaw(String, UUID)
     * @return true if the player is part of the experiment, false otherwise
     */
    public boolean evaluateExperiment(final String experimentId, @NotNull final UUID playerUUID) {
        return getExperimentSafe(experimentId, playerUUID);
    }


    /**
     * <p>Evaluates whether the specified experiment is currently enabled for this instance. This is a non-player-specific
     * experiment evaluation and will only evaluate as true if its rollout percentage is set to 100.</p>
     *
     * <p>This method returns the raw response from the Hoglin API, allowing you to handle errors yourself. Alternatively,
     * to default to false when an error occurs, you may use {@link Hoglin#evaluateExperimentRaw(String)}</p>
     *
     * @param experimentId the ID of the experiment to evaluate
     * @apiNote This makes a blocking HTTP request to the Hoglin API
     * @see #getExperimentRaw(String, UUID)
     * @see #evaluateExperiment(String)
     * @return the raw {@link HttpResponse} from the experiment evaluation call to Hoglin API
     */
    public HttpResponse<Boolean> evaluateExperimentRaw(final String experimentId) {
        return getExperimentRaw(experimentId, null);
    }

    /**
     * <p>Evaluates whether the player is part of the specified experiment. Unless the player is specifically added to the
     * allowlist for an experiment, they will randomly be pre-selected to be a part of it based on the experiment's
     * rollout percentage.</p>
     *
     * <p>This method returns the raw response from the Hoglin API, allowing you to handle errors yourself. Alternatively,
     * to default to false when an error occurs, you may use {@link Hoglin#evaluateExperimentRaw(String, UUID)}</p>
     *
     * @param experimentId the ID of the experiment to evaluate
     * @param playerUUID the UUID of the player to evaluate the experiment for
     * @apiNote This makes a blocking HTTP request to the Hoglin API
     * @see #getExperimentRaw(String, UUID)
     * @see #evaluateExperiment(String, UUID)
     * @return the raw {@link HttpResponse} from the experiment evaluation call to Hoglin API
     */
    public HttpResponse<Boolean> evaluateExperimentRaw(final String experimentId, @NotNull final UUID playerUUID) {
        return getExperimentRaw(experimentId, playerUUID);
    }

    private boolean getExperimentSafe(final String experimentId, @Nullable final UUID playerUUID) {
        if (closed) {
            throw new IllegalStateException("Attempted to evaluate experiment whilst closed");
        }

        final GetRequest request = httpClient.get("/experiments/" + serverKey + "/" + experimentId + "/evaluate");

        if (playerUUID != null) {
            request.queryString("playerUUID", playerUUID.toString());
        }

        final HttpResponse<String> response = request.asString();
        if (!response.isSuccess()) {
                logger.error("Failed to evaluate experiment '{}', defaulting to false. {}",
                    experimentId, contructErrorDescription(response)
                );
                return false;
        }

        final ExperimentEvaluation evaluation = gson.fromJson(response.getBody(), ExperimentEvaluation.class);
        return evaluation.isInExperiment();
    }

    private HttpResponse<Boolean> getExperimentRaw(final String experimentId, @Nullable final UUID playerUUID) {
        if (closed) {
            throw new IllegalStateException("Attempted to evaluate experiment whilst closed");
        }

        final GetRequest request = httpClient.get("/experiments/" + serverKey + "/" + experimentId + "/evaluate");

        if (playerUUID != null) {
            request.queryString("playerUUID", playerUUID.toString());
        }

        return request.asObject(Boolean.class);
    }

    /**
     * Constructs a human-readable error description from the given HTTP response.
     *
     * @param response the HTTP response containing the error
     * @return a string description of the error, including the HTTP status and any parsed error details
     */
    public String contructErrorDescription(final HttpResponse<String> response) {
        final String httpStatus = "(HTTP " + response.getStatus()+ "): ";
        try {
            final ApiErrorResponse error = gson.fromJson(response.getBody(), ApiErrorResponse.class);
            return httpStatus + error.parsedDescription();
        } catch (final JsonSyntaxException e) {
            return httpStatus + "Received unstructured error response: " + response.getBody();
        } catch (final Exception e) {
            return httpStatus + "An unexpected error occurred while processing the response: " + response.getBody() + " e: " + e.getMessage();
        }
    }

    @SuppressWarnings("rawtypes")
    private static Gson createDefaultGson() {
        final GsonBuilder builder = new GsonBuilder();
        final ServiceLoader<HoglinAdapter> adapters = ServiceLoader.load(HoglinAdapter.class, Hoglin.class.getClassLoader());
        for (final HoglinAdapter adapter : adapters) {
            builder.registerTypeAdapter(adapter.getType(), adapter);
        }
        return builder.create();
    }

    private static UnirestInstance createDefaultHttpClient(final String baseUrl) {
        final Config config = new Config();
        config.defaultBaseUrl(baseUrl);
        config.retryAfter(new HoglinRetryStrategy());
        config.addDefaultHeader("accept", "application/json");
        config.addDefaultHeader("Content-Type", "application/json");
        config.addDefaultHeader("User-Agent", "Hoglin/Java (Hoglin SDK)");

        return new UnirestInstance(config);
    }

    /**
     * Gracefully stops all SDK tasks. This involves flushing the entire remaining event queue in one big batch,
     * bypassing the max batch size parameter. Additionally, after calling this no more events can be queued, and
     * no more requests from the Hoglin API can be made (eg: evaluating experiments). An example of when to use this
     * is for on server/application shutdown.
     *
     * @apiNote this method makes a blocking http request
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;

        if (autoFlushTask != null) {
            autoFlushTask.cancel(true);
        }

        final int take = eventQueue.size();
        final HttpResponse<String> response = _flush();
        if (response != null && !response.isSuccess()) {
            logger.error("Failed to flush {} queued events while closing: {}", take, contructErrorDescription(response));
        }

        httpClient.close();
        logger.trace("Successfully closed Hoglin");
    }

    public static class HoglinBuilder {
        public Hoglin build() {
            final Hoglin hoglin = _build();
            hoglin.init();
            return hoglin;
        }

        private HoglinBuilder closed(final boolean closed) {
            return this;
        }

        private HoglinBuilder autoFlushTask(final ScheduledFuture<?> autoFlushTask) {
            return this;
        }
    }
}


