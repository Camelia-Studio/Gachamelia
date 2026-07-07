package org.camelia.studio.gachamelia.api.http;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static org.assertj.core.api.Assertions.assertThat;

class JavaHttpApiTransportTest {
    @Test
    void sendAppliesPerRequestTimeout() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        JavaHttpApiTransport transport = new JavaHttpApiTransport(httpClient, Duration.ofSeconds(42));

        transport.send(new ApiTransportRequest(
                "POST",
                URI.create("https://example.test/api/discord-servers"),
                Map.of("Accept", "application/json"),
                "{\"hello\":\"world\"}"
        ));

        assertThat(httpClient.lastRequest.timeout()).contains(Duration.ofSeconds(42));
    }

    private static final class RecordingHttpClient extends HttpClient {
        private HttpRequest lastRequest;

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
            lastRequest = request;
            return new HttpResponse<>() {
                @Override
                public int statusCode() {
                    return 200;
                }

                @Override
                public HttpRequest request() {
                    return request;
                }

                @Override
                public Optional<HttpResponse<T>> previousResponse() {
                    return Optional.empty();
                }

                @Override
                public HttpHeaders headers() {
                    return HttpHeaders.of(Map.of(), (name, value) -> true);
                }

                @Override
                public T body() {
                    return (T) "{}";
                }

                @Override
                public Optional<SSLSession> sslSession() {
                    return Optional.empty();
                }

                @Override
                public URI uri() {
                    return request.uri();
                }

                @Override
                public Version version() {
                    return Version.HTTP_1_1;
                }
            };
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
