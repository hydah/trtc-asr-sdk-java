package com.tencent.trtcasr;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.sun.net.httpserver.HttpServer;

/**
 * A mock HTTP server based on the JDK's built-in {@link HttpServer}, used by
 * the sentence/file recognizer tests.
 */
public class MockHttpServer implements AutoCloseable {
    public static class CapturedRequest {
        public final String method;
        public final String path;
        public final String body;
        private final com.sun.net.httpserver.Headers headers;
        private final String query;

        CapturedRequest(String method, String path, String query,
                com.sun.net.httpserver.Headers headers, String body) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.headers = headers;
            this.body = body;
        }

        public String header(String name) {
            return headers.getFirst(name);
        }

        /** Returns the decoded value of a query parameter, or null. */
        public String queryParam(String key) {
            if (query == null) {
                return null;
            }
            for (String pair : query.split("&")) {
                int idx = pair.indexOf('=');
                if (idx > 0 && SignatureParamsTest.percentDecode(pair.substring(0, idx)).equals(key)) {
                    return SignatureParamsTest.percentDecode(pair.substring(idx + 1));
                }
            }
            return null;
        }
    }

    public static class MockResponse {
        final int status;
        final String body;

        public MockResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public static MockResponse json(String body) {
            return new MockResponse(200, body);
        }
    }

    private final HttpServer server;
    private final List<CapturedRequest> requests = new CopyOnWriteArrayList<>();

    public MockHttpServer(Function<CapturedRequest, MockResponse> handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            CapturedRequest captured = new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getQuery(),
                    exchange.getRequestHeaders(),
                    body);
            requests.add(captured);
            MockResponse resp = handler.apply(captured);
            byte[] out = resp.body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(resp.status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<CapturedRequest> requests() {
        return new ArrayList<>(requests);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
