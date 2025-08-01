package gg.hoglin.sdk.models.error;

import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Represents an error response from the Hoglin API.
 */
@Data
@Accessors(fluent = true)
public class ApiErrorResponse {
    private final @NotNull String error;
    private final @Nullable List<ApiErrorDetail> details;
    private final @Nullable String message;

    /**
     * Constructs a human-readable description of the error.
     * This includes the error code, message, and details if available.
     *
     * @return A string representation of the error response.
     */
    public String parsedDescription() {
        final StringBuilder result = new StringBuilder(error);
        if (message != null && !message.isEmpty()) {
            result.append(": ").append(message);
        }

        if (details != null && !details.isEmpty()) {
            result.append(": ");
            final Collection<String> detailStrings = new ArrayList<>(details.size());
            for (final ApiErrorDetail detail : details) {
                detailStrings.add(detail.field() + " - " + detail.message());
            }
            result.append(String.join(", ", detailStrings));
        }

        return result.toString();
    }
}
