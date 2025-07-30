package gg.hoglin.sdk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gg.hoglin.sdk.models.analytic.Analytic;
import gg.hoglin.sdk.models.analytic.NamedAnalytic;
import gg.hoglin.sdk.models.analytic.RecordedAnalytic;
import gg.hoglin.sdk.serialzation.InstantSerializer;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Builder for Hoglin instance
 */
@Builder(builderMethodName = "")
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
     * Creates a new Hoglin instance with the provided API key.
     *
     * @param apiKey The API key for Hoglin
     * @return A new Hoglin instance
     */
    static HoglinBuilder builder(@NotNull String apiKey) {
        return new HoglinBuilder().serverKey(apiKey);
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
        Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantSerializer())
            .create();

        String json = gson.toJson(analytic);

        Unirest.put(baseUrl + "/analytics/" + serverKey)
            .header("accept", "application/json")
            .header("Content-Type", "application/json")
            .body(json)
            .asEmpty();
    }
}

@AllArgsConstructor
class PlayerJoin implements NamedAnalytic {
    private UUID uuid;
    private String hostname;

    @NotNull
    @Override
    public String getEventType() {
        return "player_join";
    }
}

@AllArgsConstructor
class PlayerJoin2 implements Analytic {
    private UUID uuid;
    private String hostname;
}

class Test {

    public static void main(String[] args) {
        Hoglin hoglin = Hoglin.builder("meow")
            .build();

        hoglin.track(new PlayerJoin(UUID.randomUUID(), "meowmeow.com"));
        hoglin.track("player_join", new PlayerJoin2(UUID.randomUUID(), "meowmeow.com"));

    }

}
