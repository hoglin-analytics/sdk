package gg.hoglin.sdk.task;

import gg.hoglin.sdk.Hoglin;
import gg.hoglin.sdk.models.analytic.RecordedAnalytic;
import kong.unirest.core.Unirest;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;

/**
 * skibidi toilet
 */
@RequiredArgsConstructor
public class FlushTask implements Runnable {
    private final Hoglin hoglin;

    @Override
    public void run() {
        int take = Math.min(hoglin.maxBatchSize(), hoglin.eventQueue().size());
        if (take == 0) {
            return; // No events to flush
        }

        ArrayList<RecordedAnalytic<?>> events = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            RecordedAnalytic<?> event = hoglin.eventQueue().poll();
            if (event != null) {
                events.add(event);
            }
        }

        Unirest.put(hoglin.baseUrl() + "/analytics/" + hoglin.serverKey())
            .header("accept", "application/json")
            .header("Content-Type", "application/json")
            .body(hoglin.gson().toJson(events))
            .asEmpty();
    }
}
