package org.replog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.replog.dto.AppendResponse;
import org.replog.dto.Message;
import org.replog.network.QueueNetworkStub;
import org.replog.node.MasterNode;
import org.replog.node.SecondaryNode;
import org.replog.store.MessagesStoreInMemory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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
        MessagesStoreInMemory masterStore = new MessagesStoreInMemory();
        MessagesStoreInMemory slave1Store = new MessagesStoreInMemory();
        MessagesStoreInMemory slave2Store = new MessagesStoreInMemory();

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

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
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
        masterNode = new MasterNode(new MessagesStoreInMemory(), networkStub,
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
        masterNode = new MasterNode(new MessagesStoreInMemory(), networkStub,
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
        masterNode = new MasterNode(new MessagesStoreInMemory(), networkStub,
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
        logger.info("  Message 1: '" + messages.get(0).getContent() + "'");
        logger.info("  Message 2: '" + messages.get(1).getContent() + "'");
        logger.info("  Message 3: '" + messages.get(2).getContent() + "'");

        assertEquals("Message 1", messages.get(0).getContent());
        assertEquals("Message 2", messages.get(1).getContent());
        assertEquals("Message 3", messages.get(2).getContent());

        logger.info("[Step 5] Verifying master and both slaves hold messages in the order they were appended");
        String[] expectedOrder = {"Message 1", "Message 2", "Message 3"};
        assertArrayEquals(expectedOrder, contents(masterNode.getMessages()), "Master order");
        assertArrayEquals(expectedOrder, contents(slave1.getMessages()), "Slave1 order");
        assertArrayEquals(expectedOrder, contents(slave2.getMessages()), "Slave2 order");

        logger.info("<<< TEST PASSED\n");
    }

    @Test
    void testTwoMsgsWithOneSlaveDelayed() throws Exception {
        logger.info("\n>>> TEST: testTwoMsgsWithOneSlaveDelayed");
        logger.info("[Step 1] Creating master node with 2 slaves");
        masterNode = new MasterNode(new MessagesStoreInMemory(), networkStub,
                Arrays.asList("slave1", "slave2"));

        logger.info("[Step 2] Setting slave2 delay to 1000ms (slave1 has no delay)");
        slave2.setDelayMs(1000);

        logger.info("[Step 3] Appending Message 1 - should block ~1000ms until slave2 ACKs");
        long start1 = System.currentTimeMillis();
        AppendResponse response1 = masterNode.append("Message 1", 3);
        long duration1 = System.currentTimeMillis() - start1;
        logger.info("  Message 1 append duration: " + duration1 + "ms");
        assertTrue(response1.isSuccess());
        assertTrue(duration1 >= 900 && duration1 < 1500,
                "Append should block ~1000ms for slave2, but took " + duration1 + "ms");

        logger.info("[Step 4] Verifying slave2 already stored Message 1 when append returned (ACK after store)");
        assertEquals(1, slave2.getMessages().size());

        logger.info("[Step 5] Appending Message 2 - should block ~1000ms again");
        long start2 = System.currentTimeMillis();
        AppendResponse response2 = masterNode.append("Message 2", 3);
        long duration2 = System.currentTimeMillis() - start2;
        logger.info("  Message 2 append duration: " + duration2 + "ms");
        assertTrue(response2.isSuccess());
        assertTrue(duration2 >= 900 && duration2 < 1500,
                "Append should block ~1000ms for slave2, but took " + duration2 + "ms");

        logger.info("[Step 6] Verifying both messages are on slave2 in order");
        String[] expectedContent = {"Message 1", "Message 2"};
        String[] actualContent = slave2.getMessages().stream().map(Message::getContent).toArray(String[]::new);
        logger.info("  Content check - Expected: " + Arrays.toString(expectedContent) +
                ", Actual: " + Arrays.toString(actualContent));
        assertArrayEquals(expectedContent, actualContent);

        logger.info("[Step 7] Removing slave2 delay and appending Message 3 - should complete quickly");
        slave2.setDelayMs(0L);
        long start3 = System.currentTimeMillis();
        masterNode.append("Message 3", 3);
        long duration3 = System.currentTimeMillis() - start3;
        logger.info("  Message 3 append duration: " + duration3 + "ms (no delay)");
        assertTrue(duration3 < 500, "Message 3 should complete quickly without delay, but took " + duration3 + "ms");

        logger.info("[Step 8] Verifying all three messages on slave2 in order");
        String[] expectedAll = {"Message 1", "Message 2", "Message 3"};
        String[] actualAll = slave2.getMessages().stream().map(Message::getContent).toArray(String[]::new);
        logger.info("  Content check - Expected: " + Arrays.toString(expectedAll) +
                ", Actual: " + Arrays.toString(actualAll));
        assertArrayEquals(expectedAll, actualAll);

        logger.info("<<< TEST PASSED\n");
    }

    @Test
    void testAppendBlocksUntilSlowestSlaveAcks() throws Exception {
        logger.info("\n>>> TEST: testAppendBlocksUntilSlowestSlaveAcks");
        logger.info("[Step 1] Creating master node with 2 slaves");
        masterNode = new MasterNode(new MessagesStoreInMemory(), networkStub,
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
        masterNode = new MasterNode(new MessagesStoreInMemory(), networkStub,
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

        logger.info("[Step 8] Verifying master and both slaves hold messages in the order they were appended");
        String[] expectedOrder = {"Message 1", "Message 2", "Message 3"};
        assertArrayEquals(expectedOrder, contents(masterNode.getMessages()), "Master order");
        assertArrayEquals(expectedOrder, contents(slave1.getMessages()), "Slave1 order");
        assertArrayEquals(expectedOrder, contents(slave2.getMessages()), "Slave2 order");

        logger.info("<<< TEST PASSED - Variable blocking behavior verified\n");
    }

    @Test
    void testAcceptanceScenarioWithDelayedSecondary() throws Exception {
        logger.info("\n>>> TEST: testAcceptanceScenarioWithDelayedSecondary");
        logger.info("[Step 1] Start master, slave1 (no delay), slave2 (1000ms delay)");
        masterNode = new MasterNode(new MessagesStoreInMemory(), networkStub,
                Arrays.asList("slave1", "slave2"));
        slave2.setDelayMs(1000);

        logger.info("[Step 2] send (Msg1, W=1) - Ok");
        assertTrue(masterNode.append("Msg1", 1).isSuccess());

        logger.info("[Step 3] send (Msg2, W=2) - Ok, returns after slave1 ACK without waiting for slave2");
        long start2 = System.currentTimeMillis();
        assertTrue(masterNode.append("Msg2", 2).isSuccess());
        long duration2 = System.currentTimeMillis() - start2;
        assertTrue(duration2 < 500, "W=2 should not wait for delayed slave2, took " + duration2 + "ms");

        logger.info("[Step 4] send (Msg3, W=3) - waits for slave2 because of its delay");
        CompletableFuture<AppendResponse> msg3 =
                CompletableFuture.supplyAsync(() -> masterNode.append("Msg3", 3), executor);
        Thread.sleep(200);
        assertFalse(msg3.isDone(), "W=3 should still be waiting for slave2");

        logger.info("[Step 5] send (Msg4, W=1) - Ok, must not be blocked by step 4");
        long start4 = System.currentTimeMillis();
        AppendResponse response4 = masterNode.append("Msg4", 1);
        long duration4 = System.currentTimeMillis() - start4;
        assertTrue(response4.isSuccess());
        assertTrue(duration4 < 500, "W=1 should not be blocked by Msg3, took " + duration4 + "ms");
        assertFalse(msg3.isDone(), "Msg3 should still be waiting after Msg4 completed");

        logger.info("[Step 6] Check slave2 while Msg3 is pending: must not contain Msg3, and must be a prefix of [Msg1, Msg2, Msg3]");
        String[] slave2Now = contents(slave2.getMessages());
        assertTrue(slave2Now.length <= 2, "Msg3 should not be on slave2 yet, got " + Arrays.toString(slave2Now));
        String[] order = {"Msg1", "Msg2", "Msg3"};
        for (int i = 0; i < slave2Now.length; i++) {
            assertEquals(order[i], slave2Now[i]);
        }

        logger.info("  Waiting for step 4 to complete");
        assertTrue(msg3.get(5, TimeUnit.SECONDS).isSuccess());
        awaitContents(slave2::getMessages, "Msg1", "Msg2", "Msg3");

        logger.info("[Step 7] Check master and slave1: [Msg1, Msg2, Msg3, Msg4] (Msg4 replicates in the background)");
        awaitContents(masterNode::getMessages, "Msg1", "Msg2", "Msg3", "Msg4");
        awaitContents(slave1::getMessages, "Msg1", "Msg2", "Msg3", "Msg4");

        logger.info("<<< TEST PASSED\n");
    }

    @Test
    void testSecondaryAppliesReorderedMessagesInSequenceOrder() throws Exception {
        logger.info("\n>>> TEST: testSecondaryAppliesReorderedMessagesInSequenceOrder");
        logger.info("[Step 1] Reorder the next 2 messages to slave1 (delivered in reverse)");
        networkStub.reorderMessages("slave1", 2);
        masterNode = new MasterNode(new MessagesStoreInMemory(), networkStub,
                Arrays.asList("slave1", "slave2"));

        logger.info("[Step 2] Append m1 and m2 with W=1");
        masterNode.append("m1", 1);
        masterNode.append("m2", 1);

        logger.info("[Step 3] slave1 must list m1, m2 in the order the master appended them");
        awaitContents(slave1::getMessages, "m1", "m2");
        assertArrayEquals(new String[]{"m1", "m2"}, contents(masterNode.getMessages()));

        logger.info("<<< TEST PASSED\n");
    }

    @Test
    void testSecondaryHoldsOutOfOrderMessagesAndIgnoresDuplicates() {
        logger.info("\n>>> TEST: testSecondaryHoldsOutOfOrderMessagesAndIgnoresDuplicates");
        SecondaryNode node = new SecondaryNode("solo", new MessagesStoreInMemory());

        logger.info("[Step 1] Deliver sequence 2 first - must be held back");
        node.append(new Message(2, 2L, "second", 1));
        assertEquals(0, node.getMessages().size());

        logger.info("[Step 2] Deliver sequence 1 - applies 1, then the held 2");
        node.append(new Message(1, 1L, "first", 1));
        assertArrayEquals(new String[]{"first", "second"}, contents(node.getMessages()));

        logger.info("[Step 3] Deliver sequence 1 again - ignored as a duplicate");
        node.append(new Message(1, 1L, "first", 1));
        assertArrayEquals(new String[]{"first", "second"}, contents(node.getMessages()));

        logger.info("<<< TEST PASSED\n");
    }

    @Test
    void testWriteConcernOutOfRangeIsRejected() {
        logger.info("\n>>> TEST: testWriteConcernOutOfRangeIsRejected");
        masterNode = new MasterNode(new MessagesStoreInMemory(), networkStub,
                Arrays.asList("slave1", "slave2"));

        AppendResponse response = masterNode.append("x", 4);

        assertFalse(response.isSuccess());
        assertEquals("Write concern must be between 1 and 3", response.getError());
        assertEquals(0, masterNode.getMessages().size());
        logger.info("<<< TEST PASSED\n");
    }

    // Eventual consistency: polls until the node lists exactly the expected contents
    private static void awaitContents(Supplier<List<Message>> nodeMessages, String... expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!Arrays.equals(expected, contents(nodeMessages.get())) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertArrayEquals(expected, contents(nodeMessages.get()));
    }

    private static String[] contents(List<Message> messages) {
        return messages.stream().map(Message::getContent).toArray(String[]::new);
    }
}
