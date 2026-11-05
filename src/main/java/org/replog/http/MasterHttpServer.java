package org.replog.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.replog.dto.AppendResponse;
import org.replog.node.MasterNode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MasterHttpServer {
    private static final Logger logger = Logger.getLogger(MasterHttpServer.class.getName());

    private final HttpServer server;
    private final MasterNode masterNode;
    private final ObjectMapper objectMapper;

    public MasterHttpServer(int port, MasterNode masterNode) throws Exception {
        this.masterNode = masterNode;
        this.objectMapper = new ObjectMapper();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/append", this::handleAppend);
        server.createContext("/list", this::handleList);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
    }

    private void handleAppend(HttpExchange exchange) {
        try {
            if (!exchange.getRequestMethod().equals("POST")) {
                sendEmptyResponse(exchange, 405);
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> request = objectMapper.readValue(body, Map.class);
            String content = (String) request.get("content");
            if (content == null) {
                sendEmptyResponse(exchange, 400);
                return;
            }

            int writeConcern = 3;
            if (request.containsKey("writeConcern")) {
                writeConcern = ((Number) request.get("writeConcern")).intValue();
            }
            if (writeConcern < 1 || writeConcern > masterNode.getMaxWriteConcern()) {
                sendEmptyResponse(exchange, 400);
                return;
            }

            AppendResponse response = masterNode.append(content, writeConcern);
            // 503 when the write concern was not reached; the body still carries the details
            int status = response.isSuccess() ? 200 : 503;
            sendJsonResponse(exchange, status, response);
            logger.info(String.format("Append request with content: '%s' processed successfully", content));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Append request failed", e);
            sendEmptyResponse(exchange, 500);
        }
    }

    private void handleList(HttpExchange exchange) {
        try {
            if (!exchange.getRequestMethod().equals("GET")) {
                sendEmptyResponse(exchange, 405);
                return;
            }

            var messages = masterNode.getMessages();
            sendJsonResponse(exchange, 200, messages);
            logger.fine(String.format("List request for messages: '%s' processed successfully", messages));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "List request failed", e);
            sendEmptyResponse(exchange, 500);
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object responseBody) {
        try {
            String jsonResponse = objectMapper.writeValueAsString(responseBody);
            logger.fine("Sending JSON response: " + jsonResponse);
            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
                logger.fine("Response sent successfully");
            }
        } catch (IOException e) {
            logger.warning("Client disconnected while writing response: " + e.getMessage());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "An unexpected error occurred while processing the request", e);
        } finally {
            exchange.close();
        }
    }

    private void sendEmptyResponse(HttpExchange exchange, int statusCode) {
        try {
            exchange.sendResponseHeaders(statusCode, -1);
            logger.fine("Empty response sent successfully");
        } catch (IOException e) {
            logger.warning("Client disconnected before response could be sent: " + e.getMessage());
        } finally {
            exchange.close();
        }
    }

    public void start() {
        server.start();
        logger.info("Master HTTP Server started on port " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        // The request pool's threads are non-daemon; without this the JVM keeps running after stop
        ((ExecutorService) server.getExecutor()).shutdownNow();
    }
}
