package org.camelia.studio.gachamelia.api.http;

import java.io.IOException;

public interface ApiTransport {
    ApiTransportResponse send(ApiTransportRequest request) throws IOException, InterruptedException;
}
