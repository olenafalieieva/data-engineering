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

public class MasterHttpServer {
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

            int writeConcern = 3;
            if (request.containsKey("writeConcern")) {
                writeConcern = ((Number) request.get("writeConcern")).intValue();
            }

            AppendResponse response = masterNode.append(content, writeConcern);
            System.out.println("Append request processed successfully");
            sendJsonResponse(exchange, 200, response);
            System.out.printf("Append request with content: '%s' processed successfully%n", content);
        } catch (Exception e) {
            e.printStackTrace();
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
            System.out.printf("List request for messages: '%s' processed successfully", messages);
        } catch (Exception e) {
            e.printStackTrace();
            sendEmptyResponse(exchange, 500);
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object responseBody) {
        try {
            String jsonResponse = objectMapper.writeValueAsString(responseBody);
            System.out.println("Sending JSON response: " + jsonResponse);
            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
                System.out.println("Response sent successfully");
            }
        } catch (IOException e) {
            System.err.println("Client disconnected while writing response: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("An unexpected error occurred while processing the request: " + e.getMessage());
        } finally {
            exchange.close();
        }
    }

    private void sendEmptyResponse(HttpExchange exchange, int statusCode) {
        try {
            exchange.sendResponseHeaders(statusCode, -1);
            System.out.println("Empty response sent successfully");
        } catch (IOException e) {
            System.err.println("Client disconnected before response could be sent: " + e.getMessage());
        } finally {
            exchange.close();
        }
    }

    public void start() {
        server.start();
        System.out.println("Master HTTP Server started on port " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
    }
}
