package org.camelia.studio.gachamelia.api.http;

import java.net.URI;
import java.util.Map;

public record ApiTransportRequest(
        String method,
        URI uri,
        Map<String, String> headers,
        String body
) {
}
