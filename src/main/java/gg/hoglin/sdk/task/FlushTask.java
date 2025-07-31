package gg.hoglin.sdk.task;

import gg.hoglin.sdk.Hoglin;
import gg.hoglin.sdk.models.analytic.RecordedAnalytic;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;

/**
 * A task responsible for flushing a batch of events from the event queue to the Hoglin API.
 */
@RequiredArgsConstructor
public class FlushTask implements Runnable {
    private final Hoglin hoglin;

    @Override
    public void run() {
        final int take = Math.min(hoglin.maxBatchSize(), hoglin.eventQueue().size());
        if (take == 0) {
            return; // No events to flush
        }

        final ArrayList<RecordedAnalytic<?>> events = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            RecordedAnalytic<?> event = hoglin.eventQueue().poll();
            if (event != null) {
                events.add(event);
            }
        }

        final HttpResponse<String> response = Unirest.put(hoglin.baseUrl() + "/analytics/" + hoglin.serverKey())
            .header("accept", "application/json")
            .header("Content-Type", "application/json")
            .body(hoglin.gson().toJson(events))
            .asString();

        if (!response.isSuccess()) {
            hoglin.logger().severe("Failed to flush %s queued events: %s".formatted(take, hoglin.contructErrorDescription(response)));
        }
    }
}
