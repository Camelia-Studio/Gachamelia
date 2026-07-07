package org.camelia.studio.gachamelia.api.http;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class JavaHttpApiTransport implements ApiTransport {
    private final HttpClient httpClient;

    public JavaHttpApiTransport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public ApiTransportResponse send(ApiTransportRequest request) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri());
        request.headers().forEach(builder::header);

        HttpRequest.BodyPublisher bodyPublisher = request.body() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(request.body());

        HttpRequest httpRequest = builder.method(request.method(), bodyPublisher).build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        return new ApiTransportResponse(response.statusCode(), response.body());
    }
}
