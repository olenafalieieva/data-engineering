package org.replog.http;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.replog.dto.Message;
import org.replog.node.SecondaryNode;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SecondaryHttpServer {
    private static final Logger logger = Logger.getLogger(SecondaryHttpServer.class.getName());

    private final HttpServer server;
    private final SecondaryNode slaveNode;
    private final ObjectMapper objectMapper;

    public SecondaryHttpServer(int port, SecondaryNode slaveNode) throws Exception {
        this.slaveNode = slaveNode;
        this.objectMapper = new ObjectMapper();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/append", this::handleAppend);
        server.createContext("/list", this::handleList);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
    }

    private void handleAppend(HttpExchange exchange) {
        try {
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.sendResponseHeaders(405, 0);
                exchange.close();
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            Message message = objectMapper.readValue(body, Message.class);
            slaveNode.append(message);

            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Append request failed", e);
            try {
                byte[] err = ("Error: " + e).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, err.length);
                exchange.getResponseBody().write(err);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Failed to send error response", ex);
            }
        }
    }

    private void handleList(HttpExchange exchange) {
        try {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(405, 0);
                exchange.close();
                return;
            }

            var messages = slaveNode.getMessages();
            byte[] responseBytes = objectMapper.writeValueAsString(messages)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            // Content-Length must be the byte count, not the character count
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (Exception e) {
            try {
                exchange.sendResponseHeaders(500, 0);
                exchange.close();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Failed to send error response", ex);
            }
        }
    }

    public void start() {
        server.start();
        logger.info("[" + slaveNode.getNodeId() + "] HTTP Server started on port " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        // The request pool's threads are non-daemon; without this the JVM keeps running after stop
        ((ExecutorService) server.getExecutor()).shutdownNow();
    }
}

