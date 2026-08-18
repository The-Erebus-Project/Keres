package io.github.vizanarkonin.keres.testutil;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A configurable mock HTTP server backed by JDK's com.sun.net.httpserver.HttpServer.
 * Supports serving configured status/body/latency per endpoint, including
 * 204 No Content, 304 Not Modified (null body), and slow responses.
 */
public class MockHttpTarget implements AutoCloseable {
    private HttpServer server;
    private int port;

    public static class EndpointConfig {
        public String path;
        public int statusCode = 200;
        public String body = "";
        public long delayMs = 0;

        public EndpointConfig(String path, int statusCode, String body) {
            this.path = path;
            this.statusCode = statusCode;
            this.body = body;
        }

        public static EndpointConfig ok(String path, String body) {
            return new EndpointConfig(path, 200, body);
        }

        public static EndpointConfig noContent(String path) {
            return new EndpointConfig(path, 204, "");
        }

        public static EndpointConfig notModified(String path) {
            return new EndpointConfig(path, 304, null);
        }

        public static EndpointConfig slow(String path, long delayMs) {
            EndpointConfig ec = new EndpointConfig(path, 200, "slow");
            ec.delayMs = delayMs;
            return ec;
        }
    }

    public MockHttpTarget() {
        try {
            port = findFreePort();
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create mock server", e);
        }
    }

    public String getHost() {
        return "localhost";
    }

    public int getPort() {
        return port;
    }

    public String getUrl(String path) {
        return "http://localhost:" + port + path;
    }

    public void configure(EndpointConfig... endpoints) {
        for (EndpointConfig ec : endpoints) {
            final String responseBody = ec.body;
            server.createContext(ec.path, new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    requestCount.incrementAndGet();
                    
                    if (ec.delayMs > 0) {
                        try { Thread.sleep(ec.delayMs); } catch (InterruptedException ignored) {}
                    }

                    String response = responseBody == null ? "" : responseBody;
                    byte[] bytes = response.getBytes();

                    if (ec.statusCode == 204) {
                        exchange.sendResponseHeaders(204, -1);
                    } else if (ec.statusCode == 304) {
                        exchange.getResponseHeaders().set("Content-Length", "0");
                        exchange.sendResponseHeaders(304, 0);
                    } else {
                        exchange.getResponseHeaders().set("Content-Type", "text/plain");
                        exchange.sendResponseHeaders(ec.statusCode, bytes.length);
                    }

                    try (OutputStream os = exchange.getResponseBody()) {
                        if (responseBody != null) {
                            os.write(bytes);
                        }
                    }
                    exchange.close();
                }
            });
        }

        // Default handler for unknown paths
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String body = "Not found";
                byte[] bytes = body.getBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(404, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                exchange.close();
            }
        });

        server.setExecutor(null); // use default executor
        server.start();
    }

    /**
     * Count the number of requests received so far.
     */
    public AtomicInteger requestCounter() {
        return requestCount;
    }

    private AtomicInteger requestCount = new AtomicInteger(0);

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /**
     * Find a free port on localhost.
     */
    private static int findFreePort() {
        try (var s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("No free port found", e);
        }
    }
}
