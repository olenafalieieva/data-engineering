package org.replog.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.replog.dto.Message;
import org.replog.network.Interface.NetworkClient;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class HttpNetworkClient implements NetworkClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, String> slaveEndpoints;
    private volatile boolean closed = false;

    public HttpNetworkClient(Map<String, String> slaveEndpoints) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.slaveEndpoints = slaveEndpoints;
    }

    @Override
    public CompletableFuture<Void> appendMessage(String slaveNodeId, Message message) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Network client is closed"));
        }

        String endpoint = slaveEndpoints.get(slaveNodeId);
        if (endpoint == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown slave node: " + slaveNodeId));
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(message);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/append"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenApply(response -> {
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            throw new RuntimeException("HTTP error: " + response.statusCode());
                        }
                        return null;
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<Void> close() {
        closed = true;
        return CompletableFuture.completedFuture(null);
    }
}
