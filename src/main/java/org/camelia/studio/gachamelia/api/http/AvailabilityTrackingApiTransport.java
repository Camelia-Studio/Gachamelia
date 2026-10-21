package org.camelia.studio.gachamelia.api.http;

import java.io.IOException;
import java.util.Objects;

/**
 * Mémorise l'issue du dernier appel à l'API : une erreur réseau ou une réponse 5xx
 * rend l'API indisponible, toute autre réponse (y compris 4xx) la rend disponible.
 */
public class AvailabilityTrackingApiTransport implements ApiTransport {
    private final ApiTransport delegate;
    private volatile boolean available = true;

    public AvailabilityTrackingApiTransport(ApiTransport delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public ApiTransportResponse send(ApiTransportRequest request) throws IOException, InterruptedException {
        try {
            ApiTransportResponse response = delegate.send(request);
            available = response.statusCode() < 500;
            return response;
        } catch (IOException | RuntimeException exception) {
            available = false;
            throw exception;
        }
    }

    public boolean isAvailable() {
        return available;
    }
}
