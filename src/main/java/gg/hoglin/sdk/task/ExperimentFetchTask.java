package gg.hoglin.sdk.task;

import gg.hoglin.sdk.Hoglin;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * A task responsible for fetching and caching experiments from the Hoglin API.
 */
@RequiredArgsConstructor
public class ExperimentFetchTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ExperimentFetchTask.class);
    private final Hoglin hoglin;

    @Override
    public void run() {
        CompletableFuture.runAsync(hoglin::refreshExperimentCache, hoglin.executor());
    }
}
