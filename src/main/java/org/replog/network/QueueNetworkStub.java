package org.replog.network;

import org.replog.dto.Message;
import org.replog.network.Interface.NetworkClient;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CompletableFuture;

public class QueueNetworkStub implements NetworkClient {
    private final Map<String, LinkedBlockingQueue<Delivery>> queues = new ConcurrentHashMap<>();
    private volatile boolean closed = false;

    public QueueNetworkStub(String... slaveNodeIds) {
        for (String id : slaveNodeIds) {
            queues.put(id, new LinkedBlockingQueue<>());
        }
    }

    @Override
    public CompletableFuture<Integer> appendMessage(String slaveNodeId, Message message) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Network stub is closed"));
        }

        LinkedBlockingQueue<Delivery> queue = queues.get(slaveNodeId);
        if (queue == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown slave node: " + slaveNodeId));
        }

        // The returned future completes only when the receiver acks the delivery,
        // so the master waits for the slave to actually store the message.
        CompletableFuture<Integer> ack = new CompletableFuture<>();
        try {
            queue.put(new Delivery(message, ack));
            return ack;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public Message getMessage(String slaveNodeId) throws InterruptedException {
        Delivery delivery = receive(slaveNodeId);
        delivery.ack();
        return delivery.message();
    }

    public Delivery receive(String slaveNodeId) throws InterruptedException {
        LinkedBlockingQueue<Delivery> queue = queues.get(slaveNodeId);
        if (queue == null) {
            throw new IllegalArgumentException("Unknown slave node: " + slaveNodeId);
        }
        return queue.take();
    }

    public record Delivery(Message message, CompletableFuture<Integer> ackFuture) {
        public void ack() {
            ackFuture.complete(message.getId());
        }
    }

    @Override
    public CompletableFuture<Void> close() {
        closed = true;
        return CompletableFuture.completedFuture(null);
    }
}
