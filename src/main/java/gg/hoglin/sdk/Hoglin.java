package gg.hoglin.sdk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gg.hoglin.sdk.models.analytic.Analytic;
import gg.hoglin.sdk.models.analytic.NamedAnalytic;
import gg.hoglin.sdk.models.analytic.RecordedAnalytic;
import gg.hoglin.sdk.models.error.ApiErrorResponse;
import gg.hoglin.sdk.models.experiment.ExperimentEvaluation;
import gg.hoglin.sdk.serialzation.HoglinAdapter;
import gg.hoglin.sdk.task.FlushTask;
import kong.unirest.core.*;
import lombok.*;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Hoglin instance
 */
@Builder(builderMethodName = "", buildMethodName = "_build")
@Accessors(fluent = true)
@Getter
@ToString
public class Hoglin {
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
    public final Logger logger = Logger.getLogger("Hoglin");


    /**
     * The server key for Hoglin
     */
    @ToString.Exclude
    @NotNull private String serverKey;

    /**
     * How often (ms) to send queued events
     */
    @Builder.Default private long autoFlushInterval = 30000;

    /**
     * Max events sent per batch
     */
    @Builder.Default private int maxBatchSize = 1000;

    /**
     * Whether to auto-flush events
     */
    @Builder.Default private boolean enableAutoFlush = true;

    /**
     * The API endpoint for Hoglin
     */
    @Builder.Default @NotNull private String baseUrl = DEFAULT_BASE_URL;

    /**
     * The executor used to create threads for Hoglin
     */
    @ToString.Exclude
    @Builder.Default @NotNull private ScheduledExecutorService executor = Executors.newScheduledThreadPool(0, DEFAULT_THREAD_FACTORY);

    /**
     * The {@link UnirestInstance} used for making HTTP requests
     */
    @ToString.Exclude
    @Builder.Default @NotNull private UnirestInstance httpClient = Unirest.primaryInstance();

    /**
     * The Gson instance used for serialization and deserialization
     */
    @ToString.Exclude
    @Builder.Default @NotNull private Gson gson = createDefaultGson();

    private final Queue<RecordedAnalytic<?>> eventQueue = new LinkedList<>();

    private void init() {
        executor.scheduleAtFixedRate(new FlushTask(this), autoFlushInterval, autoFlushInterval, TimeUnit.MILLISECONDS);
    }

    /**
     * Creates a new Hoglin instance with the provided API key.
     *
     * @param apiKey The API key for Hoglin
     * @return A new Hoglin instance
     */
    static HoglinBuilder builder(@NotNull String apiKey) {
        return new HoglinBuilder().serverKey(apiKey);
    }

    /**
     * Flushes the event queue immediately. This method is typically used to ensure that all pending events are sent
     * to the Hoglin API without waiting for the auto-flush interval and being limited by the max batch size
     *
     * @apiNote This method is blocking
     * @return The {@link HttpResponse} from the Hoglin API, or null if there are no queued events (no request would've
     * been made)
     */
    @Blocking
    public @Nullable HttpResponse<String> flush() {
        final int take = eventQueue.size();
        if (take == 0) return null; // No events to flush

        final ArrayList<RecordedAnalytic<?>> events = new ArrayList<>(eventQueue);
        eventQueue.clear();

        return Unirest.put(baseUrl + "/analytics/" + serverKey)
            .header("accept", "application/json")
            .header("Content-Type", "application/json")
            .body(gson.toJson(events))
            .asString();
    }

    /**
     * Tracks an analytic event with the given eventType and properties.
     *
     * @param eventType the type of event to track
     * @param properties a map of properties associated with the event
     */
    public void track(@NotNull String eventType, @NotNull Map<String, Object> properties) {
        track(new RecordedAnalytic<>(eventType, Instant.now(), properties));
    }

    /**
     * Tracks an analytic event with the given eventType and no properties.
     *
     * @param eventType the type of event to track
     */
    public void track(@NotNull String eventType) {
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
    public <T extends Analytic> void track(String eventType, T analytic) {
        track(new RecordedAnalytic<>(eventType, Instant.now(), analytic));
    }

    /**
     * Tracks an analytic event using a {@link NamedAnalytic} object. The event type will be retrieved from the named
     * analytic object.
     *
     * @param analytic the named analytic object to track
     * @param <T> the type of the named analytic object
     */
    public <T extends NamedAnalytic> void track(T analytic) {
        track(new RecordedAnalytic<>(analytic.getEventType(), Instant.now(), analytic));
    }

    /**
     * Tracks a recorded analytic event. This method is meant for sending raw event data, consider using the other track
     * methods for convenience unless necessary.
     *
     * @param analytic the recorded analytic event to track
     */
    public void track(RecordedAnalytic<?> analytic) {
        eventQueue.add(analytic);
    }

    public boolean getExperiment(final String experimentId) {
        return getExperiment(experimentId, null);
    }

    public boolean getExperiment(final String experimentId, @Nullable final UUID playerUUID) {
        final GetRequest request = Unirest.get(baseUrl + "/experiments/" + serverKey + "/" + experimentId + "/evaluate")
            .header("accept", "application/json")
            .header("Content-Type", "application/json");

        if (playerUUID != null) {
            request.queryString("playerUUID", playerUUID.toString());
        }

        final HttpResponse<String> response = request.asString();
        if (!response.isSuccess()) {
            final ApiErrorResponse error = gson.fromJson(response.getBody(), ApiErrorResponse.class);

            logger.severe("Failed to evaluate experiment '%s', defaulting to false. (HTTP %s) %s".formatted(
                experimentId, response.getStatus(), error.parsedDescription()
            ));
            return false;
        }

        final ExperimentEvaluation evaluation = gson.fromJson(response.getBody(), ExperimentEvaluation.class);
        return evaluation.isInExperiment();
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

    public static class HoglinBuilder {
        public Hoglin build() {
            final Hoglin hoglin = _build();
            hoglin.init();
            return hoglin;
        }
    }
}


