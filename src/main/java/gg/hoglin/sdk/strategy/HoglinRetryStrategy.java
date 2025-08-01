package gg.hoglin.sdk.strategy;

import kong.unirest.core.HttpResponse;
import kong.unirest.core.RetryStrategy;

import java.util.List;

public class HoglinRetryStrategy implements RetryStrategy {
    @Override
    public boolean isRetryable(final HttpResponse<?> response) {
        return List.of(-1, 408, 429, 500, 502, 503, 504)
            .contains(response.getStatus());
    }

    @Override
    public long getWaitTime(final HttpResponse<?> response) {
        try {
            return Long.parseLong(response.getHeaders().getFirst("Retry-After")) * 1000;
        } catch (final Exception e) {
            return 5000;
        }
    }

    @Override
    public int getMaxAttempts() {
        return 5;
    }
}
