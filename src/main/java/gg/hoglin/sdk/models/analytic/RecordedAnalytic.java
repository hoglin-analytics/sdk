package gg.hoglin.sdk.models.analytic;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * Represents metadata and properties of an analytic event that has been recorded.
 * @param <T> the type of the analytic object
 */
@Data
@Accessors(fluent = true)
public class RecordedAnalytic<T> {
    @SerializedName("event_type") private final String eventType;
    private final Instant timestamp;
    private final T properties;
}
