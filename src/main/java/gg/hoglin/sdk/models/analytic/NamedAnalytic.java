package gg.hoglin.sdk.models.analytic;

import org.jetbrains.annotations.NotNull;

/**
 * Represents data for an analytic event with a specific type. This interface extends the base Analytic interface and
 * adds a method to retrieve the event type. Ensure that your implementations are serializable with GSON.
 */
public interface NamedAnalytic extends Analytic {
    /**
     * Retrieves the type of the analytic event.
     * @return the event type as a non-null string
     */
    @NotNull String getEventType();
}
