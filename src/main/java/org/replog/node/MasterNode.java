package org.replog.node;


import org.replog.dto.AppendResponse;
import org.replog.dto.Message;
import org.replog.network.Interface.NetworkClient;
import org.replog.store.Interface.MessagesStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class MasterNode {
    private static final Logger logger = Logger.getLogger(MasterNode.class.getName());

    private final MessagesStore store;
    private final NetworkClient networkClient;
    private final List<String> slaveNodeIds;
    // Sequence assignment and store insert happen under one lock, so the master's order is the total order
    private final Object sequenceLock = new Object();
    private long nextSequence = 1;

    public MasterNode(MessagesStore store, NetworkClient networkClient, List<String> slaveNodeIds) {
        this.store = store;
        this.networkClient = networkClient;
        this.slaveNodeIds = slaveNodeIds;
    }

    // Write concern w ranges from 1 (master only) to the number of nodes (master + all secondaries)
    public int getMaxWriteConcern() {
        return slaveNodeIds.size() + 1;
    }

    public AppendResponse append(String content, int writeConcern) {
        if (writeConcern < 1 || writeConcern > getMaxWriteConcern()) {
            return AppendResponse.failure("Write concern must be between 1 and " + getMaxWriteConcern());
        }

        Message message;
        synchronized (sequenceLock) {
            message = new Message(store.generateId(), nextSequence++, content, writeConcern);
            store.appendMsg(message);
        }

        // w=1 needs no secondary ACKs; w=n waits for ACKs from n-1 secondaries
        int confirmations = 1 + replicateToSlaves(message, writeConcern - 1);

        if (confirmations >= writeConcern) {
            return AppendResponse.success(message.getId(), confirmations);
        } else {
            return AppendResponse.failure("Not enough confirmations. Got " + confirmations +
                    ", required " + writeConcern);
        }
    }

    // Sends to every secondary, but returns as soon as requiredAcks have stored the message.
    // Sends that are still in flight keep running in the background.
    // Returns the number of secondary ACKs counted when the decision was made.
    private int replicateToSlaves(Message message, int requiredAcks) {
        AtomicInteger ackCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        int maxFailures = slaveNodeIds.size() - requiredAcks;
        CompletableFuture<Boolean> decided = new CompletableFuture<>();
        if (requiredAcks == 0) {
            decided.complete(true);
        }

        List<CompletableFuture<Integer>> sends = new ArrayList<>();
        for (String slaveId : slaveNodeIds) {
            sends.add(networkClient.appendMessage(slaveId, message)
                    .handle((ignored, error) -> {
                        if (error != null) {
                            logger.warning("Failed to replicate to slave " + slaveId + ": " + error.getMessage());
                            if (failureCount.incrementAndGet() > maxFailures) {
                                decided.complete(false);
                            }
                            return 0;
                        }
                        if (ackCount.incrementAndGet() >= requiredAcks) {
                            decided.complete(true);
                        }
                        return 1;
                    }));
        }

        boolean satisfied = decided.join();
        if (!satisfied) {
            // Let the remaining sends settle so the error reports the final ACK count
            CompletableFuture.allOf(sends.toArray(new CompletableFuture[0])).join();
        }
        return ackCount.get();
    }

    public List<Message> getMessages() {
        return store.getAll();
    }

    public void close() throws Exception {
        networkClient.close();
    }
}
