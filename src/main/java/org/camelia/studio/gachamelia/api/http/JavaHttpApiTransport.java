package org.camelia.studio.gachamelia.api.http;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class JavaHttpApiTransport implements ApiTransport {
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public JavaHttpApiTransport(HttpClient httpClient) {
        this(httpClient, DEFAULT_REQUEST_TIMEOUT);
    }

    public JavaHttpApiTransport(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public ApiTransportResponse send(ApiTransportRequest request) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .timeout(requestTimeout);
        request.headers().forEach(builder::header);

        HttpRequest.BodyPublisher bodyPublisher = request.body() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(request.body());

        HttpRequest httpRequest = builder.method(request.method(), bodyPublisher).build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        return new ApiTransportResponse(response.statusCode(), response.body());
    }
}
