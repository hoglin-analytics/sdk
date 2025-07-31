package gg.hoglin.sdk.models.error;

import lombok.Data;

@Data
public class ApiErrorDetail {
    private final String field;
    private final String message;
}
