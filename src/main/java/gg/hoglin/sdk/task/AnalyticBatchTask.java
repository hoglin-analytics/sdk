package gg.hoglin.sdk.task;

import gg.hoglin.sdk.Hoglin;
import gg.hoglin.sdk.models.analytic.RecordedAnalytic;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * A task responsible for sending a batch of events from the event queue to the Hoglin API.
 */
@RequiredArgsConstructor
public class AnalyticBatchTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticBatchTask.class);
    private final Hoglin hoglin;

    @Override
    public void run() {
        final int take = Math.min(hoglin.maxBatchSize(), hoglin.eventQueue().size());
        if (take == 0) {
            return; // No events to flush
        }

        final Collection<RecordedAnalytic<?>> events = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            final RecordedAnalytic<?> event = hoglin.eventQueue().poll();
            if (event != null) {
                events.add(event);
            }
        }

        CompletableFuture.supplyAsync(() ->
            hoglin.httpClient().put("/analytics/" + hoglin.serverKey())
                .body(hoglin.gson().toJson(events))
                .asString(), hoglin.executor()
        ).thenAccept(response -> {
            if (response.isSuccess()) return;
            if (hoglin.requeueFailedFlushes()) {
                hoglin.trackMany(events);
                logger.error("Failed to flush {} queued events, added back to the end of the queue: {}", take, hoglin.constructErrorDescription(response));
            } else {
                logger.error("Failed to flush {} queued events: {}", take, hoglin.constructErrorDescription(response));
            }
        });
    }
}
