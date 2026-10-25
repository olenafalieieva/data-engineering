package org.replog.node;


import org.replog.dto.Message;
import org.replog.store.Interface.MessagesStore;
import java.util.List;

public class SecondaryNode {
    private final MessagesStore store;
    private final String nodeId;
    private final long delayMs;

    public SecondaryNode(String nodeId, MessagesStore store) {
        this(nodeId, store, 0L);
    }

    public SecondaryNode(String nodeId, MessagesStore store, long delayMs) {
        this.nodeId = nodeId;
        this.store = store;
        this.delayMs = delayMs;
    }

    public void append(Message message) {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[" + nodeId + "] Interrupted during delay, dropping message: " + message.getId());
                return;
            }
        }
        store.appendMsg(message);
        System.out.println("[" + nodeId + "] Received and stored message: " + message.getId());
    }

    public List<Message> getMessages() {
        return store.getAll();
    }

    public String getNodeId() {
        return nodeId;
    }
}
