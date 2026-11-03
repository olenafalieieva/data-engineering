package org.replog.node;


import org.replog.dto.AppendResponse;
import org.replog.dto.Message;
import org.replog.network.Interface.NetworkClient;
import org.replog.store.Interface.MessagesStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class MasterNode {
    private static final Logger logger = Logger.getLogger(MasterNode.class.getName());

    private final MessagesStore store;
    private final NetworkClient networkClient;
    private final List<String> slaveNodeIds;

    public MasterNode(MessagesStore store, NetworkClient networkClient, List<String> slaveNodeIds) {
        this.store = store;
        this.networkClient = networkClient;
        this.slaveNodeIds = slaveNodeIds;
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
        // Send to all slaves at once; each ack becomes 1 (stored) or 0 (failed),
        // so one failed slave only lowers the confirmation count.
        List<CompletableFuture<Integer>> acks = new ArrayList<>();
        for (String slaveId : slaveNodeIds) {
            acks.add(networkClient.appendMessage(slaveId, message)
                    .handle((ignored, error) -> {
                        if (error != null) {
                            logger.warning("Failed to replicate to slave " + slaveId + ": " + error.getMessage());
                            return 0;
                        }
                        return 1;
                    }));
        }

        CompletableFuture.allOf(acks.toArray(new CompletableFuture[0])).join();
        return acks.stream().mapToInt(CompletableFuture::join).sum();
    }

    public List<Message> getMessages() {
        return store.getAll();
    }

    public void close() throws Exception {
        networkClient.close();
    }
}
