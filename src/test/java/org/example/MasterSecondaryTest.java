package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.replog.dto.AppendResponse;
import org.replog.dto.Message;
import org.replog.network.QueueNetworkStub;
import org.replog.node.MasterNode;
import org.replog.node.SecondaryNode;
import org.replog.store.MessagesStoreRandomOrder;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class MasterSecondaryTest {
    private static final Logger logger = Logger.getLogger(MasterSecondaryTest.class.getName());

    private MasterNode masterNode;
    private SecondaryNode slave1;
    private SecondaryNode slave2;
    private QueueNetworkStub networkStub;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        logger.info("=== SETUP START ===");

        logger.info("[Step 1] Initializing network stub with slave nodes (slave1, slave2)");
        networkStub = new QueueNetworkStub("slave1", "slave2");

        logger.info("[Step 2] Creating message stores for master and slave nodes");
        MessagesStoreRandomOrder masterStore = new MessagesStoreRandomOrder();
        MessagesStoreRandomOrder slave1Store = new MessagesStoreRandomOrder();
        MessagesStoreRandomOrder slave2Store = new MessagesStoreRandomOrder();

        logger.info("[Step 3] Creating secondary (slave) node instances");
        slave1 = new SecondaryNode("slave1", slave1Store);
        slave2 = new SecondaryNode("slave2", slave2Store);

        logger.info("[Step 4] Creating thread pool executor with 4 threads for async operations");
        executor = Executors.newFixedThreadPool(4);

        logger.info("[Step 5] Starting message listeners for slave1 and slave2");
        startSlaveListeners(slave1);
        startSlaveListeners(slave2);

        logger.info("=== SETUP COMPLETE ===\n");
    }

    private void startSlaveListeners(SecondaryNode slave) {
        logger.fine("Starting listener thread for node: " + slave.getNodeId());
        executor.submit(() -> {
            try {
                while (true) {
                    var delivery = networkStub.receive(slave.getNodeId());
                    var msg = delivery.message();
                    logger.fine("Node " + slave.getNodeId() + " received message: " + msg.getContent());
                    slave.append(msg);
                    delivery.ack();
                }
            } catch (InterruptedException e) {
                logger.fine("Listener thread for " + slave.getNodeId() + " interrupted");
                Thread.currentThread().interrupt();
            }
        });
    }

    @Test
    void testAppendWithConsistencyLevelThree() {
        logger.info("\n>>> TEST: testAppendWithConsistencyLevelThree");
        logger.info("[Step 1] Creating master node with consistency level 3 (master + 2 slaves)");
        masterNode = new MasterNode(new MessagesStoreRandomOrder(), networkStub,
                Arrays.asList("slave1", "slave2"));

        logger.info("[Step 2] Appending message with consistency level 3 (waiting for 3 confirmations)");
        AppendResponse response = masterNode.append("Test message", 3);

        logger.info("[Step 3] Verifying response success and confirmation count");
        logger.info("  Response success: " + response.isSuccess() + ", Confirmations: " + response.getConfirmations());
        assertTrue(response.isSuccess());
        assertEquals(3, response.getConfirmations());
        logger.info("<<< TEST PASSED\n");
    }

    @Test
    void testMessagesReplicatedToSlavesNoDelay() throws Exception {
        logger.info("\n>>> TEST: testMessagesReplicatedToSlavesNoDelay");
        logger.info("[Step 1] Creating master node with 2 slaves");
        masterNode = new MasterNode(new MessagesStoreRandomOrder(), networkStub,
                Arrays.asList("slave1", "slave2"));

        logger.info("[Step 2] Appending message to master with consistency level 3");
        AppendResponse response = masterNode.append("Important message", 3);

        logger.info("[Step 3] Verifying append was successful");
        assertTrue(response.isSuccess());

        logger.info("[Step 4] Verifying message replicated to all nodes");
        logger.info("  Master messages: " + masterNode.getMessages().size());
        logger.info("  Slave1 messages: " + slave1.getMessages().size());
        logger.info("  Slave2 messages: " + slave2.getMessages().size());
        assertEquals(1, masterNode.getMessages().size());
        assertEquals(1, slave1.getMessages().size());
        assertEquals(1, slave2.getMessages().size());

        logger.info("[Step 5] Verifying message content and ID consistency across nodes");
        var msg = masterNode.getMessages().get(0);
        logger.info("  Message content: '" + msg.getContent() + "', ID: " + msg.getId());
        assertEquals("Important message", msg.getContent());
        assertEquals(msg.getId(), slave1.getMessages().get(0).getId());
        assertEquals(msg.getId(), slave2.getMessages().get(0).getId());
        logger.info("<<< TEST PASSED\n");
    }

    @Test
    void testListMessages() throws Exception {
        logger.info("\n>>> TEST: testListMessages");
        logger.info("[Step 1] Creating master node with 2 slaves");
        masterNode = new MasterNode(new MessagesStoreRandomOrder(), networkStub,
                Arrays.asList("slave1", "slave2"));

        logger.info("[Step 2] Appending 3 messages to master");
        masterNode.append("Message 1", 3);
        masterNode.append("Message 2", 3);
        masterNode.append("Message 3", 3);

        logger.info("[Step 3] Retrieving and verifying messages from master");
        var messages = masterNode.getMessages();
        logger.info("  Total messages in master: " + messages.size());
        assertEquals(3, messages.size());

        logger.info("[Step 4] Verifying message content and order");
        logger.info("  Message 0: '" + messages.get(0).getContent() + "'");
        logger.info("  Message 1: '" + messages.get(1).getContent() + "'");
        logger.info("  Message 2: '" + messages.get(2).getContent() + "'");
        assertEquals("Message 1", messages.get(0).getContent());
        assertEquals("Message 2", messages.get(1).getContent());
        assertEquals("Message 3", messages.get(2).getContent());
        logger.info("<<< TEST PASSED\n");
    }

    @Test
    void testTwoMsgsWithOneSlaveDelayed() throws Exception {
        logger.info("\n>>> TEST: testTwoMsgsWithOneSlaveDelayed");
        logger.info("[Step 1] Creating master node with 2 slaves");
        masterNode = new MasterNode(new MessagesStoreRandomOrder(), networkStub,
                Arrays.asList("slave1", "slave2"));

        logger.info("[Step 2] Setting slave2 delay to 1000ms");
        slave2.setDelayMs(1000);

        logger.info("[Step 3] Appending two messages to master in the background (append blocks until acked)");
        CompletableFuture<Void> appends = CompletableFuture.runAsync(() -> masterNode.append("Message 1", 3), executor)
                .thenRunAsync(() -> masterNode.append("Message 2", 3), executor);

        logger.info("[Step 4] Verifying slave2 has NO messages after 200ms (both delays pending)");
        Thread.sleep(200);
        logger.info("  Slave2 messages after 200ms: " + slave2.getMessages().size());
        assertEquals(0, slave2.getMessages().size());

        logger.info("[Step 5] Waiting for both appends to complete (1000ms delay each)");
        appends.get(5, TimeUnit.SECONDS);

        logger.info("[Step 6] Verifying both messages received on slave2");
        logger.info("  Slave2 messages after 2200ms total: " + slave2.getMessages().size());
        assertEquals(2, slave2.getMessages().size());
        String[] expectedContent = {"Message 1", "Message 2"};
        String[] actualContent = slave2.getMessages().stream().map(Message::getContent).toArray(String[]::new);
        logger.info("  Content check - Expected: " + java.util.Arrays.toString(expectedContent) +
                ", Actual: " + java.util.Arrays.toString(actualContent));
        assertArrayEquals(expectedContent, actualContent);

        logger.info("[Step 7] Removing delay from slave2 and appending third message");
        slave2.setDelayMs(0L);
        masterNode.append("Message 3", 3);
        logger.info("  Appended: Message 3 (no delay)");

        logger.info("[Step 8] Verifying all three messages on slave2");
        String[] expectedAll = {"Message 1", "Message 2", "Message 3"};
        String[] actualAll = slave2.getMessages().stream().map(Message::getContent).toArray(String[]::new);
        logger.info("  Content check - Expected: " + java.util.Arrays.toString(expectedAll) +
                ", Actual: " + java.util.Arrays.toString(actualAll));
        assertArrayEquals(expectedAll, actualAll);
        logger.info("<<< TEST PASSED\n");
    }

    @Test
    void testAppendBlocksUntilSlowestSlaveAcks() throws Exception {
        logger.info("\n>>> TEST: testAppendBlocksUntilSlowestSlaveAcks");
        logger.info("[Step 1] Creating master node with 2 slaves");
        masterNode = new MasterNode(new MessagesStoreRandomOrder(), networkStub,
                Arrays.asList("slave1", "slave2"));

        logger.info("[Step 2] Setting slave1 delay to 1500ms, slave2 delay to 2500ms (slave2 is slower)");
        slave1.setDelayMs(1500);
        slave2.setDelayMs(2500);

        logger.info("[Step 3] Measuring time for append() call - should block until slowest slave (2500ms) ACKs");
        long startTime = System.currentTimeMillis();
        masterNode.append("Blocking test message", 3);
        long duration = System.currentTimeMillis() - startTime;

        logger.info("  Append call duration: " + duration + "ms");
        logger.info("  Expected minimum: ~2500ms (slowest slave delay)");

        logger.info("[Step 4] Verifying append() was blocked for at least the slowest slave's delay");
        assertTrue(duration >= 2400, "Append should block ~2500ms, but took only " + duration + "ms. " +
                "Master may not be waiting for all ACKs!");
        assertTrue(duration < 3000, "Append took too long: " + duration + "ms (expected ~2500ms)");

        logger.info("[Step 5] Verifying message was replicated to all nodes after blocking completed");
        assertEquals(1, masterNode.getMessages().size(), "Message should be on master");
        assertEquals(1, slave1.getMessages().size(), "Message should be on slave1");
        assertEquals(1, slave2.getMessages().size(), "Message should be on slave2");

        logger.info("<<< TEST PASSED - Blocking behavior verified\n");
    }

    @Test
    void testAppendBlockingWithVariableDelays() throws Exception {
        logger.info("\n>>> TEST: testAppendBlockingWithVariableDelays");
        logger.info("[Step 1] Creating master node with 2 slaves");
        masterNode = new MasterNode(new MessagesStoreRandomOrder(), networkStub,
                Arrays.asList("slave1", "slave2"));

        logger.info("[Step 2] First append: Setting slave1 delay to 800ms, slave2 delay to 800ms");
        slave1.setDelayMs(800);
        slave2.setDelayMs(800);

        logger.info("[Step 3] Append message 1 - should block ~800ms");
        long start1 = System.currentTimeMillis();
        masterNode.append("Message 1", 3);
        long duration1 = System.currentTimeMillis() - start1;
        logger.info("  Message 1 append duration: " + duration1 + "ms");
        assertTrue(duration1 >= 750 && duration1 < 1200, "Message 1 should block ~800ms, got " + duration1 + "ms");

        logger.info("[Step 4] Changing delays: slave1 to 600ms, slave2 to 1200ms (slave2 now slower)");
        slave1.setDelayMs(600);
        slave2.setDelayMs(1200);

        logger.info("[Step 5] Append message 2 - should block ~1200ms (slowest slave)");
        long start2 = System.currentTimeMillis();
        masterNode.append("Message 2", 3);
        long duration2 = System.currentTimeMillis() - start2;
        logger.info("  Message 2 append duration: " + duration2 + "ms");
        assertTrue(duration2 >= 1100 && duration2 < 1600, "Message 2 should block ~1200ms, got " + duration2 + "ms");

        logger.info("[Step 6] Removing delays and append message 3 - should complete quickly");
        slave1.setDelayMs(0);
        slave2.setDelayMs(0);
        long start3 = System.currentTimeMillis();
        masterNode.append("Message 3", 3);
        long duration3 = System.currentTimeMillis() - start3;
        logger.info("  Message 3 append duration: " + duration3 + "ms (no delay)");
        assertTrue(duration3 < 500, "Message 3 should complete quickly without delay, but took " + duration3 + "ms");

        logger.info("[Step 7] Verifying all 3 messages replicated to all nodes");
        logger.info("  Master: " + masterNode.getMessages().size() + " messages");
        logger.info("  Slave1: " + slave1.getMessages().size() + " messages");
        logger.info("  Slave2: " + slave2.getMessages().size() + " messages");
        assertEquals(3, masterNode.getMessages().size());
        assertEquals(3, slave1.getMessages().size());
        assertEquals(3, slave2.getMessages().size());

        logger.info("<<< TEST PASSED - Variable blocking behavior verified\n");
    }
}
