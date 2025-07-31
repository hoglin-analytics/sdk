package gg.hoglin.sdk.models.error;

import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an error response from the Hoglin API.
 * Contains an error code, optional details about the error, and an optional message.
 */
@Data
public class ApiErrorResponse {
    private final String error;
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
            final List<String> detailStrings = new ArrayList<>();
            for (ApiErrorDetail detail : details) {
                detailStrings.add(detail.getField() + " - " + detail.getMessage());
            }
            result.append(String.join(", ", detailStrings));
        }

        return result.toString();
    }
}
