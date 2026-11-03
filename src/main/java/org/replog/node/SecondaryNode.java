package org.replog.node;


import org.replog.dto.Message;
import org.replog.store.Interface.MessagesStore;
import java.util.List;
import java.util.logging.Logger;

public class SecondaryNode {
    private static final Logger logger = Logger.getLogger(SecondaryNode.class.getName());

    private final MessagesStore store;
    private final String nodeId;
    private volatile long delayMs;

    public SecondaryNode(String nodeId, MessagesStore store) {
        this(nodeId, store, 0L);
    }

    public SecondaryNode(String nodeId, MessagesStore store, long delayMs) {
        this.nodeId = nodeId;
        this.store = store;
        this.delayMs = delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void append(Message message) {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("[" + nodeId + "] Interrupted during delay, dropping message: " + message.getId());
                return;
            }
        }
        store.appendMsg(message);
        logger.info("[" + nodeId + "] Received and stored message: " + message.getId());
    }

    public List<Message> getMessages() {
        return store.getAll();
    }

    public String getNodeId() {
        return nodeId;
    }
}
