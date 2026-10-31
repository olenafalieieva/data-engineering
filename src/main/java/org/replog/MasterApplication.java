package org.replog;


import org.replog.dto.AppendResponse;
import org.replog.http.MasterHttpServer;
import org.replog.network.HttpNetworkClient;
import org.replog.network.Interface.NetworkClient;
import org.replog.network.QueueNetworkStub;
import org.replog.node.MasterNode;
import org.replog.store.Interface.MessagesStore;
import org.replog.store.MessagesStoreRandomOrder;

import java.util.*;

public class MasterApplication {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        boolean useHttp = true;
        String networkType = "queue";
        String storeType = "random";
        Map<String, String> slaveEndpoints = null;

        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i])) {
                port = Integer.parseInt(args[++i]);
            } else if ("--network".equals(args[i])) {
                networkType = args[++i];
                useHttp = "http".equals(networkType);
            } else if ("--store".equals(args[i])) {
                storeType = args[++i];
            } else if ("--slave-endpoints".equals(args[i])) {
                slaveEndpoints = parseEndpoints(args[++i]);
            }
        }

        List<String> slaveNodeIds = slaveEndpoints != null
                ? new java.util.ArrayList<>(slaveEndpoints.keySet())
                : Arrays.asList("slave1", "slave2");
        MessagesStore store = createStore();
        NetworkClient networkClient = createNetworkClient(networkType, slaveNodeIds, slaveEndpoints);

        MasterNode masterNode = new MasterNode(store, networkClient, slaveNodeIds);
        MasterHttpServer httpServer = new MasterHttpServer(port, masterNode);
        httpServer.start();

        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║     Master Node Started                  ║");
        System.out.println("╠════════════════════════════════════════╣");
        System.out.println("║ Port: " + port);
        System.out.println("║ Network Type: " + networkType);
        System.out.println("║ Store Type: " + storeType);
        System.out.println("║ Default Write Concern: 3");
        System.out.println("║ Slaves: " + String.join(", ", slaveNodeIds));
        System.out.println("╚════════════════════════════════════════╝\n");

        interactiveMode(masterNode);

        masterNode.close();
        httpServer.stop();
    }

    private static MessagesStore createStore() {
        return new MessagesStoreRandomOrder();
        }

    private static NetworkClient createNetworkClient(String networkType, List<String> slaveNodeIds,
                                                     Map<String, String> slaveEndpoints) {
        switch (networkType.toLowerCase()) {
            case "http":
                Map<String, String> endpoints = slaveEndpoints != null ? slaveEndpoints : defaultEndpoints();
                return new HttpNetworkClient(endpoints);
            case "queue":
            default:
                return new QueueNetworkStub(slaveNodeIds.toArray(new String[0]));
        }
    }

    private static Map<String, String> defaultEndpoints() {
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("slave1", "http://localhost:8081");
        endpoints.put("slave2", "http://localhost:8082");
        return endpoints;
    }

    private static Map<String, String> parseEndpoints(String arg) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : arg.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }

    private static void interactiveMode(MasterNode masterNode) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Commands:");
        System.out.println("  append <msg>              - Append message (default writeConcern=3)");
        System.out.println("  append <msg> <concern>    - Append with specific write concern (1-3)");
        System.out.println("  list                      - Show all messages");
        System.out.println("  exit                      - Exit\n");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;

            if (input.equalsIgnoreCase("exit")) {
                break;
            } else if (input.startsWith("append ")) {
                String rest = input.substring(7).trim();
                int writeConcern = 3;
                String content = rest;

                String[] parts = rest.split("\\s+(?=\\d+$)");
                if (parts.length == 2 && parts[1].matches("\\d+")) {
                    content = parts[0];
                    writeConcern = Integer.parseInt(parts[1]);
                    if (writeConcern < 1 || writeConcern > 3) {
                        System.out.println("✗ Write concern must be 1-3");
                        continue;
                    }
                }

                AppendResponse response = masterNode.append(content, writeConcern);
                if (response.isSuccess()) {
                    System.out.println("✓ Message appended. ID: " + response.getMessageId() +
                            ", Write Concern: " + writeConcern +
                            ", Confirmations: " + response.getConfirmations());
                } else {
                    System.out.println("✗ Failed: " + response.getError());
                }
            } else if (input.equalsIgnoreCase("list")) {
                var messages = masterNode.getMessages();
                System.out.println("Messages (" + messages.size() + "):");
                for (var msg : messages) {
                    System.out.println("  [" + msg.getId() + "] " + msg.getContent());
                }
            } else {
                System.out.println("Unknown command. Type 'append <msg>', 'list', or 'exit'");
            }
        }

        scanner.close();
    }
}

