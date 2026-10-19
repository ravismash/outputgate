package io.github.ravismash.outputgate.ollama;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A local HTTP server that answers /api/chat with a fixed status and body, and records requests. */
final class FakeOllama implements AutoCloseable {

    record Request(String method, String path, String contentType, String body) {}

    private final HttpServer server;
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());

    private FakeOllama(int status, byte[] body, Duration delay) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            } catch (IOException ignored) {
                // the client gave up (timeout test)
            }
        });
        server.start();
    }

    static FakeOllama recorded(String resource) throws IOException {
        try (InputStream in = FakeOllama.class.getClassLoader().getResourceAsStream(resource)) {
            return new FakeOllama(200, in.readAllBytes(), Duration.ZERO);
        }
    }

    static FakeOllama answering(int status, String body) throws IOException {
        return new FakeOllama(status, body.getBytes(StandardCharsets.UTF_8), Duration.ZERO);
    }

    static FakeOllama slow(Duration delay, String body) throws IOException {
        return new FakeOllama(200, body.getBytes(StandardCharsets.UTF_8), delay);
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<Request> requests() {
        return List.copyOf(requests);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
