package gg.hoglin.sdk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import gg.hoglin.sdk.models.analytic.Analytic;
import gg.hoglin.sdk.models.analytic.NamedAnalytic;
import gg.hoglin.sdk.models.analytic.RecordedAnalytic;
import gg.hoglin.sdk.models.error.ApiErrorResponse;
import gg.hoglin.sdk.models.experiment.ExperimentData;
import gg.hoglin.sdk.models.experiment.ExperimentEvaluationResponse;
import gg.hoglin.sdk.models.visualization.ImportedSnapshotEvaluation;
import gg.hoglin.sdk.models.visualization.SnapshotImport;
import gg.hoglin.sdk.serialization.HoglinAdapter;
import gg.hoglin.sdk.strategy.HoglinRetryStrategy;
import gg.hoglin.sdk.task.AnalyticBatchTask;
import gg.hoglin.sdk.task.ExperimentFetchTask;
import kong.unirest.core.Config;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.RequestBodyEntity;
import kong.unirest.core.UnirestInstance;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.WillClose;
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
     * Whether to auto-flush events periodically
     */
    @Builder.Default private boolean enableAutoFlush = true;

    /**
     * How often (ms) to send queued events
     */
    @Builder.Default private long autoFlushInterval = 15000;

    /**
     * Max events sent per batch
     */
    @Builder.Default private int maxBatchSize = 10000;

    /**
     * Whether to auto-fetch experiments periodically
     */
    @Builder.Default private boolean enableAutoExperimentFetch = true;

    /**
     * How often (ms) to refresh the experiment cache
     */
    @Builder.Default private long autoExperimentFetchInterval = 60000;

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

    /**
     * The event queue for storing recorded analytics before they are sent to the Hoglin API
     */
    @ToString.Exclude
    private final Queue<RecordedAnalytic<?>> eventQueue = new LinkedList<>();

    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    private final Map<String, ExperimentData> experimentCache = new ConcurrentHashMap<>();

    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    private @Nullable ScheduledFuture<?> autoFlushTask = null;

    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    private @Nullable ScheduledFuture<?> experimentFetchTask = null;

    private boolean closed = false;

    private void init() {
        if (httpClient == null) {
            httpClient = createDefaultHttpClient(baseUrl);
        }

        if (enableAutoFlush) {
            autoFlushTask = executor.scheduleAtFixedRate(new AnalyticBatchTask(this), autoFlushInterval, autoFlushInterval, TimeUnit.MILLISECONDS);
        }

        if (enableAutoExperimentFetch) {
            experimentFetchTask = executor.scheduleAtFixedRate(new ExperimentFetchTask(this), autoExperimentFetchInterval, autoExperimentFetchInterval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Creates a new Hoglin instance with the provided API key.
     *
     * @param apiKey The API key for Hoglin
     * @return A new Hoglin instance
     */
    public static HoglinBuilder builder(@NotNull final String apiKey) {
        return new HoglinBuilder().serverKey(apiKey);
    }

    /**
     * Flushes the event queue immediately. This method is typically used to ensure that all pending events are sent
     * to the Hoglin API without waiting for the auto-flush interval and being limited by the max batch size
     *
     * @apiNote This makes a blocking HTTP request to the Hoglin API
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

    /**
     * Manually instantly refreshes the experiment cache by fetching the latest experiment data from the Hoglin API.
     *
     * @apiNote This makes a blocking HTTP request to the Hoglin API
     */
    @Blocking
    public void refreshExperimentCache() {
        final HttpResponse<String> response = httpClient.get("/experiments/" + serverKey)
            .asString();

        if (!response.isSuccess()) {
            logger.error("Failed to refresh experiment cache: {}", response.getBody());
        }

        final ExperimentData[] experiments = gson.fromJson(response.getBody(), ExperimentData[].class);
        experimentCache.clear();

        for (final ExperimentData experiment : experiments) {
            experimentCache.put(experiment.getExperimentId(), experiment);
        }
    }

    /**
     * Gets an unmodifiable view of the current experiment cache.
     *
     * @return An unmodifiable map of experiment IDs to their corresponding {@link ExperimentData}
     */
    public Map<String, ExperimentData> getExperiments() {
        return Collections.unmodifiableMap(experimentCache);
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
     * @param preventDuplicate optional flag to prevent importing if the dashboard already has a visualization from
     *                          the same snapshot. This is similar to checking {@link #isSnapshotImported(UUID)}
     *                          before importing, but is done in within a singular import request within the API.
     * @apiNote This makes a blocking HTTP request to the Hoglin API
     * @return the {@link HttpResponse} from the Hoglin API for any further handling
     */
    @Blocking
    public HttpResponse<String> importSnapshot(final UUID snapshotId, @Nullable final String name, @Nullable final Boolean preventDuplicate) {
        if (closed) {
            throw new IllegalStateException("Attempted to import visualization snapshot whilst closed");
        }

        final RequestBodyEntity request = httpClient.post("/visualizations/" + serverKey + "/import")
            .body(gson.toJson(new SnapshotImport(snapshotId, name, preventDuplicate)));

        return request.asString();
    }

    /**
     * Imports a visualization snapshot to the Hoglin dashboard.
     *
     * @param snapshotId the ID of the visualization snapshot to import
     * @param preventDuplicate optional flag to prevent importing if the dashboard already has a visualization from
     *                          the same snapshot. This is similar to checking {@link #isSnapshotImported(UUID)}
     *                          before importing, but is done in within a singular import request within the API.
     * @apiNote This makes a blocking HTTP request to the Hoglin API
     * @see #importSnapshot(UUID, String, Boolean) for more options
     * @return the {@link HttpResponse} from the Hoglin API for any further handling
     */
    @Blocking
    public HttpResponse<String> importSnapshot(final UUID snapshotId, @Nullable final Boolean preventDuplicate) {
        return importSnapshot(snapshotId, null, preventDuplicate);
    }

    /**
     * Imports a visualization snapshot to the Hoglin dashboard.
     *
     * @param snapshotId the ID of the visualization snapshot to import
     * @param name optional new name for the imported visualization, or null to keep the original name
     * @apiNote This makes a blocking HTTP request to the Hoglin API
     * @see #importSnapshot(UUID, String, Boolean) for more options
     * @return the {@link HttpResponse} from the Hoglin API for any further handling
     */
    @Blocking
    public HttpResponse<String> importSnapshot(final UUID snapshotId, @Nullable final String name) {
        return importSnapshot(snapshotId, name, false);
    }

    /**
     * Imports a visualization snapshot to the Hoglin dashboard.
     *
     * @param snapshotId the ID of the visualization snapshot to import
     * @see #importSnapshot(UUID, String, Boolean) for more options
     * @return the {@link HttpResponse} from the Hoglin API for any further handling
     */
    public HttpResponse<String> importSnapshot(final UUID snapshotId) {
        return importSnapshot(snapshotId, null, false);
    }

    /**
     * <p>Checks if a visualization snapshot has already been imported. This is a shorthand version of
     * {@link #getImportedSnapshotInfo(UUID)} then checking {@link ImportedSnapshotEvaluation#isImported()}</p>
     *
     * <p>This is a safe check. If the request to check the snapshot fails, this method will just return false, with a
     * log in the console. If you would like to handle the response manually, look at
     * {@link Hoglin#getImportedSnapshotInfoRaw(UUID)}</p>
     *
     * @param snapshotId the ID of the visualization snapshot to check
     * @apiNote This makes a blocking HTTP request to the Hoglin API
     * @see #getImportedSnapshotInfo(UUID)
     * @return true if this Hoglin instance has a visualization imported from the specified snapshot, false otherwise
     */
    @Blocking
    public boolean isSnapshotImported(final UUID snapshotId) {
        final @Nullable ImportedSnapshotEvaluation evaluation = getImportedSnapshotInfo(snapshotId);
        if (evaluation == null) {
            return false;
        }

        return evaluation.isImported();
    }

    /**
     * <p>Gets information about whether a visualization snapshot has already been imported.</p>
     *
     * <p>This is a safe check. If the request to check the snapshot fails, this method will just return null, with a
     * log in the console. If you would like to handle the response manually, look at
     * {@link Hoglin#getImportedSnapshotInfoRaw(UUID)}</p>
     *
     * @param snapshotId the ID of the visualization snapshot to check
     * @return the {@link ImportedSnapshotEvaluation} containing information about the import status
     * @see #isSnapshotImported(UUID)
     * @see #getImportedSnapshotInfoRaw(UUID)
     * @apiNote This makes a blocking HTTP request to the Hoglin API
     */
    @Blocking
    public @Nullable ImportedSnapshotEvaluation getImportedSnapshotInfo(final UUID snapshotId) {
        final HttpResponse<String> response = getImportedSnapshotInfoRaw(snapshotId);
        if (!response.isSuccess()) {
            logger.error("Failed to get imported snapshot info: {}", constructErrorDescription(response));
            return new ImportedSnapshotEvaluation(false, null);
        }

        return gson.fromJson(response.getBody(), ImportedSnapshotEvaluation.class);
    }

    /**
     * <p>Gets information about whether a visualization snapshot has already been imported.</p>
     *
     * <p>This method returns the raw response from the Hoglin API, allowing you to handle errors yourself. Alternatively,
     * to parse the response to a {@link ImportedSnapshotEvaluation} and default to null when an error occurs, you may
     * use {@link Hoglin#getImportedSnapshotInfo(UUID)}</p>
     *
     * @param snapshotId the ID of the visualization snapshot to check
     * @return the {@link ImportedSnapshotEvaluation} containing information about the import status
     * @see #getImportedSnapshotInfo(UUID)
     * @apiNote This makes a blocking HTTP request to the Hoglin API
     */
    @Blocking
    public HttpResponse<String> getImportedSnapshotInfoRaw(final UUID snapshotId) {
        if (closed) {
            throw new IllegalStateException("Attempted to get imported snapshot info whilst closed");
        }

        return httpClient.get("/visualizations/imported/" + serverKey + "/" + snapshotId).asString();
    }

    /**
     * <p>Evaluates whether the specified experiment is currently enabled for this instance. This is a non-player-specific
     * experiment evaluation and will only evaluate as true if its rollout percentage is set to 100. For player-specific
     * evaluations, see {@link Hoglin#evaluateExperiment(String, UUID)}</p>
     *
     * <p>This is a safe evaluation. If the specified experiment ID cannot be resolved (is not in the cache), this
     * method will just return false, with a log in the console. If you would like to manually check if the experiment
     * exists, you should use {@link Hoglin#getExperiments()} beforehand. You may also consider using
     * {@link ExperimentData#evaluate()} to bypass the cache check.</p>
     *
     * @param experimentId the ID of the experiment to evaluate
     * @see #evaluateExperiment(String, UUID)
     * @see #getExperiments()
     * @see ExperimentData#evaluate()
     * @return true if this instance is part of the experiment, false otherwise
     */
    public boolean evaluateExperiment(final String experimentId) {
        final ExperimentData experiment = experimentCache.get(experimentId);
        if (experiment == null) return false;

        return experiment.evaluate();
    }

    /**
     * <p>Evaluates whether the player is part of the specified experiment. Unless the player is specifically added to the
     * allowlist for an experiment, they will randomly be pre-selected to be a part of it based on the experiment's
     * rollout percentage, as determined by the API.</p>
     *
     * <p>This is a safe evaluation. If the specified experiment ID cannot be resolved (is not in the cache), this
     * method will just return false, with a log in the console. If you would like to manually check if the experiment
     * exists, you should use {@link Hoglin#getExperiments()} beforehand.
     *
     * @param experimentId the ID of the experiment to evaluate
     * @param playerUUID the UUID of the player to evaluate the experiment for
     * @see #getExperiments()
     * @return true if the player is part of the experiment, false otherwise
     */
    @Blocking
    public boolean evaluateExperiment(final String experimentId, @NotNull final UUID playerUUID) {
        final HttpResponse<String> response =
                httpClient.get("/experiments/" + serverKey + "/" + experimentId + "/evalulate?playerUUID=" + playerUUID).asString();
        if (!response.isSuccess()) {
            logger.error("Failed to evaluate experiment {} for player {}: {}", experimentId, playerUUID, constructErrorDescription(response));
            return false;
        }
        ExperimentEvaluationResponse expEvalResp = gson.fromJson(response.getBody(), ExperimentEvaluationResponse.class);
        return expEvalResp.getInExperiment();
    }

    /**
     * Constructs a human-readable error description from the given HTTP response.
     *
     * @param response the HTTP response containing the error
     * @return a string description of the error, including the HTTP status and any parsed error details
     */
    public String constructErrorDescription(final HttpResponse<String> response) {
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
     * Gracefully stops all SDK tasks. This involves flushing the entire remaining event queue in one big batch
     * bypassing the max batch size parameter. Additionally, after calling this, no more events can be queued, and
     * no more requests from the Hoglin API can be made (e.g.: evaluating experiments). An example of when to use this
     * is for on server/application shutdown.
     *
     * @apiNote This makes a blocking HTTP request to the Hoglin API
     */
    @Blocking
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
            logger.error("Failed to flush {} queued events while closing: {}", take, constructErrorDescription(response));
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


