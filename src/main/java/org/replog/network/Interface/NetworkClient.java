package org.replog.network.Interface;

import org.replog.dto.Message;

import java.util.concurrent.CompletableFuture;

public interface NetworkClient {
    CompletableFuture<Void> appendMessage(String slaveNodeId, Message message);
    CompletableFuture<Void> close();
}
