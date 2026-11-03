package org.replog;


import org.replog.http.SecondaryHttpServer;
import org.replog.node.SecondaryNode;
import org.replog.store.Interface.MessagesStore;
import org.replog.store.MessagesStoreRandomOrder;

import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

public class SecondaryApplication {
    public static void main(String[] args) throws Exception {
        String nodeId = "slave1";
        int port = 8081;
        String storeType = "random";
        long delayMs = 0L;

        for (int i = 0; i < args.length; i++) {
            if ("--id".equals(args[i])) {
                nodeId = args[++i];
            } else if ("--port".equals(args[i])) {
                port = Integer.parseInt(args[++i]);
            } else if ("--store".equals(args[i])) {
                storeType = args[++i];
            } else if ("--delay".equals(args[i])) {
                delayMs = Long.parseLong(args[++i]);
            }
        }

        MessagesStore store = createStore();
        SecondaryNode slaveNode = new SecondaryNode(nodeId, store, delayMs);
        SecondaryHttpServer httpServer = new SecondaryHttpServer(port, slaveNode);
        httpServer.start();

        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║     Secondary Node Started                   ║");
        System.out.println("╠════════════════════════════════════════╣");
        System.out.println("║ ID: " + nodeId);
        System.out.println("║ Port: " + port);
        System.out.println("║ Store Type: " + storeType);
        System.out.println("║ Delay (ms): " + delayMs);
        System.out.println("╚════════════════════════════════════════╝\n");

        interactiveMode(slaveNode);

        httpServer.stop();
    }

    private static MessagesStore createStore() {
                return new MessagesStoreRandomOrder();
        }

    private static void interactiveMode(SecondaryNode slaveNode) throws InterruptedException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Commands: list, exit");

        while (true) {
            System.out.print("[" + slaveNode.getNodeId() + "]> ");
            if (!scanner.hasNextLine()) {
                // No terminal attached (e.g. docker run without -i): keep serving HTTP instead of exiting
                new CountDownLatch(1).await();
            }
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;

            if (input.equalsIgnoreCase("exit")) {
                scanner.close();
                break;
            } else if (input.equalsIgnoreCase("list")) {
                var messages = slaveNode.getMessages();
                System.out.println("Messages (" + messages.size() + "):");
                for (var msg : messages) {
                    System.out.println("  [" + msg.getId() + "] " + msg.getContent());
                }
            } else {
                System.out.println("Unknown command");
            }
        }

        scanner.close();
    }
}

