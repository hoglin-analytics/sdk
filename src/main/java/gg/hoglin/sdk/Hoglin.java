package gg.hoglin.sdk;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.ls.LSOutput;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Builder for Hoglin instance
 */
@Builder
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
     * The API key for Hoglin
     */
    @NotNull private String apiKey;

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
    @Builder.Default @NotNull private ScheduledExecutorService executor = Executors.newScheduledThreadPool(0, DEFAULT_THREAD_FACTORY);

    /**
     * The OkHttpClient used for making HTTP requests
     */
    @Builder.Default @NotNull private OkHttpClient okHttpClient = new OkHttpClient(new OkHttpClient.Builder());

}
