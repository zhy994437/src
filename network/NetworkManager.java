package network;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages network communication for council members using TCP sockets
 * Handles both server (listening) and client (sending) functionality
 */
public class NetworkManager {
    
    private final String memberId;
    private final int port;
    private final Map<String, String> memberHosts;
    private final Map<String, Integer> memberPorts;
    private final ExecutorService messageProcessor;
    private final BlockingQueue<PaxosMessage> incomingMessages;
    private ServerSocket serverSocket;
    private volatile boolean isRunning;
    private final Object socketLock = new Object();
    
    /**
     * Interface for handling incoming messages
     */
    public interface MessageHandler {
        /**
         * Process an incoming Paxos message
         * 
         * @param message The received message
         */
        void handleMessage(PaxosMessage message);
    }
    
    private MessageHandler messageHandler;
    
    /**
     * Constructor for NetworkManager
     * 
     * @param memberId The ID of this council member (e.g., "M1")
     * @param port The port to listen on
     */
    public NetworkManager(String memberId, int port) {
        this.memberId = memberId;
        this.port = port;
        this.memberHosts = new HashMap<>();
        this.memberPorts = new HashMap<>();
        this.messageProcessor = Executors.newFixedThreadPool(5);
        this.incomingMessages = new LinkedBlockingQueue<>();
        this.isRunning = false;
    }
    
    /**
     * Loads network configuration from file
     * Expected format: M1,localhost,9001
     * 
     * @param configFile Path to the configuration file
     * @throws IOException If file reading fails
     */
    public void loadConfiguration(String configFile) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Skip empty lines and comments
                }
                
                String[] parts = line.split(",");
                if (parts.length != 3) {
                    System.err.println("Invalid configuration line: " + line);
                    continue;
                }
                
                String id = parts[0].trim();
                String host = parts[1].trim();
                int memberPort = Integer.parseInt(parts[2].trim());
                
                memberHosts.put(id, host);
                memberPorts.put(id, memberPort);
            }
        }
        
        System.out.println("Loaded configuration for " + memberHosts.size() + " members");
    }
    
    /**
     * Sets the message handler for processing incoming messages
     * 
     * @param handler The message handler
     */
    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }
    
    /**
     * Starts the network manager (begins listening for incoming connections)
     * 
     * @throws IOException If server socket creation fails
     */
    public void start() throws IOException {
        synchronized (socketLock) {
            if (isRunning) {
                return;
            }
            
            serverSocket = new ServerSocket(port);
            isRunning = true;
            
            System.out.println(memberId + " listening on port " + port);
            
            // Start server thread to accept connections
            Thread serverThread = new Thread(this::runServer);
            serverThread.setDaemon(true);
            serverThread.start();
            
            // Start message processing thread
            Thread processingThread = new Thread(this::processMessages);
            processingThread.setDaemon(true);
            processingThread.start();
        }
    }
    
    /**
     * Stops the network manager and closes all connections
     */
    public void stop() {
        synchronized (socketLock) {
            isRunning = false;
            
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing server socket: " + e.getMessage());
                }
            }
            
            messageProcessor.shutdown();
            try {
                if (!messageProcessor.awaitTermination(5, TimeUnit.SECONDS)) {
                    messageProcessor.shutdownNow();
                }
            } catch (InterruptedException e) {
                messageProcessor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Sends a message to a specific council member
     * 
     * @param recipientId The ID of the recipient member
     * @param message The message to send
     * @return true if message was sent successfully, false otherwise
     */
    public boolean sendMessage(String recipientId, PaxosMessage message) {
        if (!memberHosts.containsKey(recipientId) || !memberPorts.containsKey(recipientId)) {
            System.err.println("Unknown recipient: " + recipientId);
            return false;
        }
        
        String host = memberHosts.get(recipientId);
        int recipientPort = memberPorts.get(recipientId);
        
        try (Socket socket = new Socket(host, recipientPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            
            String serializedMessage = message.serialize();
            out.println(serializedMessage);
            
            System.out.println(memberId + " sent to " + recipientId + ": " + message.getType());
            return true;
            
        } catch (IOException e) {
            System.err.println("Failed to send message to " + recipientId + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Broadcasts a message to all other council members
     * 
     * @param message The message to broadcast
     * @return The number of members that successfully received the message
     */
    public int broadcastMessage(PaxosMessage message) {
        int successCount = 0;
        
        for (String recipientId : memberHosts.keySet()) {
            if (!recipientId.equals(memberId)) { // Don't send to self
                if (sendMessage(recipientId, message)) {
                    successCount++;
                }
            }
        }
        
        System.out.println(memberId + " broadcast " + message.getType() + " to " + successCount + " members");
        return successCount;
    }
    
    /**
     * Gets the next incoming message from the queue
     * 
     * @param timeout Timeout in milliseconds
     * @return The next message, or null if timeout occurs
     * @throws InterruptedException If interrupted while waiting
     */
    public PaxosMessage getNextMessage(long timeout) throws InterruptedException {
        return incomingMessages.poll(timeout, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Gets the next incoming message from the queue (blocking)
     * 
     * @return The next message
     * @throws InterruptedException If interrupted while waiting
     */
    public PaxosMessage getNextMessage() throws InterruptedException {
        return incomingMessages.take();
    }
    
    /**
     * Checks if there are any pending messages
     * 
     * @return true if messages are available
     */
    public boolean hasMessages() {
        return !incomingMessages.isEmpty();
    }
    
    /**
     * Gets the list of all known member IDs
     * 
     * @return Set of member IDs
     */
    public Set<String> getKnownMembers() {
        return new HashSet<>(memberHosts.keySet());
    }
    
    /**
     * Server loop that accepts incoming connections
     */
    private void runServer() {
        while (isRunning && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                // Handle each connection in a separate thread
                messageProcessor.execute(() -> handleConnection(clientSocket));
                
            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Handles an individual client connection
     * 
     * @param clientSocket The client socket
     */
    private void handleConnection(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()))) {
            
            String messageStr = in.readLine();
            if (messageStr != null && !messageStr.trim().isEmpty()) {
                try {
                    PaxosMessage message = PaxosMessage.deserialize(messageStr);
                    incomingMessages.offer(message);
                    
                    System.out.println(memberId + " received from " + message.getSenderId() + 
                                     ": " + message.getType());
                    
                } catch (IllegalArgumentException e) {
                    System.err.println("Invalid message format: " + messageStr);
                }
            }
            
        } catch (IOException e) {
            System.err.println("Error handling connection: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
    }
    
    /**
     * Processes incoming messages using the registered handler
     */
    private void processMessages() {
        while (isRunning) {
            try {
                PaxosMessage message = incomingMessages.poll(1, TimeUnit.SECONDS);
                if (message != null && messageHandler != null) {
                    messageHandler.handleMessage(message);
                }
            } catch (InterruptedException e) {
                if (isRunning) {
                    System.err.println("Message processing interrupted");
                }
                break;
            } catch (Exception e) {
                System.err.println("Error processing message: " + e.getMessage());
            }
        }
    }
    
    /**
     * Gets the member ID
     * 
     * @return The member ID
     */
    public String getMemberId() {
        return memberId;
    }
    
    /**
     * Gets the listening port
     * 
     * @return The port number
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Checks if the network manager is running
     * 
     * @return true if running
     */
    public boolean isRunning() {
        return isRunning;
    }
}