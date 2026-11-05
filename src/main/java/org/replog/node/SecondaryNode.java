package org.replog.node;


import org.replog.dto.Message;
import org.replog.store.Interface.MessagesStore;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

public class SecondaryNode {
    private static final Logger logger = Logger.getLogger(SecondaryNode.class.getName());

    private final MessagesStore store;
    private final String nodeId;
    private volatile long delayMs;

    // Messages are applied to the store only in sequence order; early arrivals wait in pending
    private final Object orderLock = new Object();
    private long nextSequence = 1;
    private final SortedMap<Long, Message> pending = new TreeMap<>();

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

        synchronized (orderLock) {
            long sequence = message.getSequence();
            if (sequence < nextSequence) {
                logger.fine("[" + nodeId + "] Duplicate message ignored, sequence " + sequence);
            } else if (sequence > nextSequence) {
                pending.put(sequence, message);
                logger.info("[" + nodeId + "] Buffered message " + sequence + ", waiting for " + nextSequence);
            } else {
                store.appendMsg(message);
                nextSequence++;
                logger.info("[" + nodeId + "] Received and stored message: " + message.getId());
                while (!pending.isEmpty() && pending.firstKey() == nextSequence) {
                    store.appendMsg(pending.remove(pending.firstKey()));
                    nextSequence++;
                }
            }
        }
    }

    public List<Message> getMessages() {
        return store.getAll();
    }

    public String getNodeId() {
        return nodeId;
    }
}
