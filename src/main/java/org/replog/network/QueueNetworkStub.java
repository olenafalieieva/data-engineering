package org.replog.network;

import org.replog.dto.Message;
import org.replog.network.Interface.NetworkClient;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CompletableFuture;

public class QueueNetworkStub implements NetworkClient {
    private final Map<String, LinkedBlockingQueue<Message>> queues = new ConcurrentHashMap<>();
    private volatile boolean closed = false;

    public QueueNetworkStub(String... slaveNodeIds) {
        for (String id : slaveNodeIds) {
            queues.put(id, new LinkedBlockingQueue<>());
        }
    }

    @Override
    public CompletableFuture<Void> appendMessage(String slaveNodeId, Message message) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Network stub is closed"));
        }

        LinkedBlockingQueue<Message> queue = queues.get(slaveNodeId);
        if (queue == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown slave node: " + slaveNodeId));
        }

        try {
            queue.put(message);
            return CompletableFuture.completedFuture(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        }
    }

    public Message getMessage(String slaveNodeId) throws InterruptedException {
        LinkedBlockingQueue<Message> queue = queues.get(slaveNodeId);
        if (queue == null) {
            throw new IllegalArgumentException("Unknown slave node: " + slaveNodeId);
        }
        return queue.take();
    }

    @Override
    public CompletableFuture<Void> close() {
        closed = true;
        return CompletableFuture.completedFuture(null);
    }
}
