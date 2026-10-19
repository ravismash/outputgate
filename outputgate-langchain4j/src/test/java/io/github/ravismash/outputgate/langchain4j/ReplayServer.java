package io.github.ravismash.outputgate.langchain4j;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Serves a recorded Ollama /api/chat response on localhost, and remembers what it was sent. */
final class ReplayServer implements AutoCloseable {

    record Request(String method, String path, String body) {}

    private final HttpServer server;
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());

    ReplayServer(String recordedResource) throws IOException {
        byte[] body;
        try (InputStream in = ReplayServer.class.getClassLoader().getResourceAsStream(recordedResource)) {
            body = in.readAllBytes();
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getPath();
            requests.add(new Request(exchange.getRequestMethod(), path, sent));
            boolean chat = "POST".equals(exchange.getRequestMethod()) && "/api/chat".equals(path);
            byte[] out = chat ? body : "{\"error\":\"not recorded\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(chat ? 200 : 404, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
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
