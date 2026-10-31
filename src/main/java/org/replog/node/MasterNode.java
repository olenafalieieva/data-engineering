package org.replog.node;


import org.replog.dto.AppendResponse;
import org.replog.dto.Message;
import org.replog.network.Interface.NetworkClient;
import org.replog.store.Interface.MessagesStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MasterNode {
    private final MessagesStore store;
    private final NetworkClient networkClient;
    private final List<String> slaveNodeIds;
    private final ExecutorService replicationExecutor;

    public MasterNode(MessagesStore store, NetworkClient networkClient, List<String> slaveNodeIds) {
        this.store = store;
        this.networkClient = networkClient;
        this.slaveNodeIds = slaveNodeIds;
        this.replicationExecutor = Executors.newFixedThreadPool(slaveNodeIds.size());
    }

    public AppendResponse append(String content, int writeConcern) {
        Integer messageId = store.append(content, writeConcern);
        Message message = new Message(messageId, content, writeConcern);

        int confirmations = 1;

        if (writeConcern == 1) {
            return AppendResponse.success(messageId, confirmations);
        }

        if (writeConcern > 1) {
            confirmations += replicateToSlaves(message);
        }

        if (confirmations >= writeConcern) {
            return AppendResponse.success(messageId, confirmations);
        } else {
            return AppendResponse.failure("Not enough confirmations. Got " + confirmations +
                    ", required " + writeConcern);
        }
    }

    private int replicateToSlaves(Message message) {
        List<CompletableFuture<Integer>> futures = new ArrayList<>();

        for (String slaveId : slaveNodeIds) {
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Integer res = networkClient.appendMessage(slaveId, message).join();
                    return res;

                } catch (Exception e) {
                    System.err.println("Failed to replicate to slave " + slaveId + ": " + e.getMessage());
                    return 0;
                }
            }, replicationExecutor);
            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            int confirmations = 0;
            for (CompletableFuture<Integer> future : futures) {
                if (future.join() != 0) {
                    confirmations++;
                }
            }
            return confirmations;
        } catch (Exception e) {
            System.err.println("Replication interrupted: " + e.getMessage());
            return 0;
        }
    }

    public List<Message> getMessages() {
        return store.getAll();
    }

    public void close() throws Exception {
        networkClient.close();
        replicationExecutor.shutdown();
    }
}
