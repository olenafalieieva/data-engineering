package org.replog.network.Interface;

import org.replog.dto.Message;

import java.util.concurrent.CompletableFuture;

public interface NetworkClient {
    CompletableFuture<Integer> appendMessage(String slaveNodeId, Message message);
    CompletableFuture<Void> close();
    Message getMessage(String slaveNodeId) throws InterruptedException;
}
