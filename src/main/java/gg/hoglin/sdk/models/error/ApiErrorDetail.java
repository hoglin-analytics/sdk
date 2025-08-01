package gg.hoglin.sdk.models.error;

import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;


/**
 * Represents extra details for errors from the Hoglin API.
 */
@Data
@Accessors(fluent = true)
public class ApiErrorDetail {
    private final @NotNull String field;
    private final @NotNull String message;
}
