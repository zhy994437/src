package simulation;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced network behavior simulator that models real-world network conditions
 * including latency variations, packet loss, network partitions, and recovery patterns
 */
public class NetworkBehaviorSimulator implements INetworkSimulator {
    
    private final String memberId;
    private final Random random;
    private final ScheduledExecutorService behaviorExecutor;
    private final AtomicBoolean isActive;
    
    // Current behavior state
    private volatile NetworkProfile currentProfile;
    private volatile NetworkCondition currentCondition;
    private final AtomicLong totalMessagesSent;
    private final AtomicLong totalMessagesLost;
    private final AtomicLong totalDelay;
    
    // Behavior tracking
    private final Queue<NetworkEvent> recentEvents;
    private final Map<String, Long> peerLatencies;
    private final Set<String> partitionedPeers;
    
    /**
     * Detailed network profiles with realistic parameters
     */
    public enum NetworkProfile {
        // M1 - Ultra-responsive, always connected
        RELIABLE(
            new LatencyModel(0, 10, 2, 0.0),
            new FailureModel(0.0, 0.0, 0),
            new RecoveryModel(100, 0.99)
        ),
        
        // M2 - Poor connection, works well at cafe
        LATENT(
            new LatencyModel(800, 4000, 1200, 0.3),
            new FailureModel(0.15, 0.05, 3000),
            new RecoveryModel(2000, 0.6)
        ),
        
        // M3 - Intermittent connection, goes camping
        FAILURE(
            new LatencyModel(100, 1500, 400, 0.2),
            new FailureModel(0.35, 0.15, 5000),
            new RecoveryModel(1500, 0.4)
        ),
        
        // M4-M9 - Standard business connections
        STANDARD(
            new LatencyModel(30, 200, 80, 0.1),
            new FailureModel(0.05, 0.01, 1000),
            new RecoveryModel(500, 0.85)
        );
        
        private final LatencyModel latencyModel;
        private final FailureModel failureModel;
        private final RecoveryModel recoveryModel;
        
        NetworkProfile(LatencyModel latencyModel, FailureModel failureModel, RecoveryModel recoveryModel) {
            this.latencyModel = latencyModel;
            this.failureModel = failureModel;
            this.recoveryModel = recoveryModel;
        }
        
        public LatencyModel getLatencyModel() { return latencyModel; }
        public FailureModel getFailureModel() { return failureModel; }
        public RecoveryModel getRecoveryModel() { return recoveryModel; }
    }
    
    /**
     * Current network condition affecting behavior
     */
    public enum NetworkCondition {
        NORMAL,         // Standard operation
        DEGRADED,       // Reduced performance
        PARTITIONED,    // Isolated from some peers
        OFFLINE,        // Completely disconnected
        RECOVERING      // Coming back online
    }
    
    /**
     * Models network latency characteristics
     */
    public static class LatencyModel {
        private final int baseLatencyMs;
        private final int maxLatencyMs;
        private final int jitterMs;
        private final double spikeRate;
        
        public LatencyModel(int baseLatencyMs, int maxLatencyMs, int jitterMs, double spikeRate) {
            this.baseLatencyMs = baseLatencyMs;
            this.maxLatencyMs = maxLatencyMs;
            this.jitterMs = jitterMs;
            this.spikeRate = spikeRate;
        }
        
        public int getBaseLatency() { return baseLatencyMs; }
        public int getMaxLatency() { return maxLatencyMs; }
        public int getJitter() { return jitterMs; }
        public double getSpikeRate() { return spikeRate; }
    }
    
    /**
     * Models network failure patterns
     */
    public static class FailureModel {
        private final double messageDropRate;
        private final double connectionFailureRate;
        private final long averageDowntimeMs;
        
        public FailureModel(double messageDropRate, double connectionFailureRate, long averageDowntimeMs) {
            this.messageDropRate = messageDropRate;
            this.connectionFailureRate = connectionFailureRate;
            this.averageDowntimeMs = averageDowntimeMs;
        }
        
        public double getMessageDropRate() { return messageDropRate; }
        public double getConnectionFailureRate() { return connectionFailureRate; }
        public long getAverageDowntimeMs() { return averageDowntimeMs; }
    }
    
    /**
     * Models network recovery behavior
     */
    public static class RecoveryModel {
        private final long averageRecoveryTimeMs;
        private final double stabilityFactor;
        
        public RecoveryModel(long averageRecoveryTimeMs, double stabilityFactor) {
            this.averageRecoveryTimeMs = averageRecoveryTimeMs;
            this.stabilityFactor = stabilityFactor;
        }
        
        public long getAverageRecoveryTime() { return averageRecoveryTimeMs; }
        public double getStabilityFactor() { return stabilityFactor; }
    }
    
    /**
     * Represents a network event for tracking and analysis
     */
    public static class NetworkEvent {
        private final long timestamp;
        private final EventType type;
        private final String description;
        private final Map<String, Object> metadata;
        
        public enum EventType {
            MESSAGE_SENT, MESSAGE_DELAYED, MESSAGE_DROPPED,
            CONNECTION_FAILED, CONNECTION_RECOVERED,
            PARTITION_STARTED, PARTITION_ENDED,
            CONDITION_CHANGED
        }
        
        public NetworkEvent(EventType type, String description) {
            this.timestamp = System.currentTimeMillis();
            this.type = type;
            this.description = description;
            this.metadata = new HashMap<>();
        }
        
        public NetworkEvent withMetadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }
        
        // Getters
        public long getTimestamp() { return timestamp; }
        public EventType getType() { return type; }
        public String getDescription() { return description; }
        public Map<String, Object> getMetadata() { return metadata; }
        
        @Override
        public String toString() {
            return String.format("[%d] %s: %s", timestamp, type, description);
        }
    }
    
    /**
     * Constructor for NetworkBehaviorSimulator
     * 
     * @param memberId The member this simulator belongs to
     * @param initialProfile Initial network profile
     */
    public NetworkBehaviorSimulator(String memberId, NetworkProfile initialProfile) {
        this.memberId = memberId;
        this.random = new Random();
        this.behaviorExecutor = Executors.newScheduledThreadPool(2);
        this.isActive = new AtomicBoolean(true);
        
        this.currentProfile = initialProfile;
        this.currentCondition = NetworkCondition.NORMAL;
        this.totalMessagesSent = new AtomicLong(0);
        this.totalMessagesLost = new AtomicLong(0);
        this.totalDelay = new AtomicLong(0);
        
        this.recentEvents = new ConcurrentLinkedQueue<>();
        this.peerLatencies = new ConcurrentHashMap<>();
        this.partitionedPeers = ConcurrentHashMap.newKeySet();
        
        // Start dynamic behavior simulation
        startDynamicBehaviorSimulation();
        
        System.out.println(memberId + " network behavior simulator initialized with " + 
                         initialProfile + " profile");
    }
    
    /**
     * Simulates sending a message with realistic network behavior
     * 
     * @param targetPeer The target peer ID
     * @param messageSize Size of message in bytes (affects behavior)
     * @return MessageDeliveryResult indicating what happened
     */
    public MessageDeliveryResult simulateMessageSend(String targetPeer, int messageSize) {
        if (!isActive.get()) {
            return new MessageDeliveryResult(false, 0, "Simulator inactive");
        }
        
        totalMessagesSent.incrementAndGet();
        
        // Check if we're partitioned from this peer
        if (partitionedPeers.contains(targetPeer)) {
            recordEvent(NetworkEvent.EventType.MESSAGE_DROPPED, 
                "Message to " + targetPeer + " dropped due to partition");
            totalMessagesLost.incrementAndGet();
            return new MessageDeliveryResult(false, 0, "Network partition");
        }
        
        // Check for connection failure
        if (shouldSimulateConnectionFailure()) {
            simulateConnectionFailure();
            recordEvent(NetworkEvent.EventType.CONNECTION_FAILED, "Connection failed to " + targetPeer);
            totalMessagesLost.incrementAndGet();
            return new MessageDeliveryResult(false, 0, "Connection failure");
        }
        
        // Check for message drop
        if (shouldSimulateMessageDrop()) {
            recordEvent(NetworkEvent.EventType.MESSAGE_DROPPED, "Message to " + targetPeer + " dropped");
            totalMessagesLost.incrementAndGet();
            return new MessageDeliveryResult(false, 0, "Message dropped");
        }
        
        // Calculate latency
        long latency = calculateMessageLatency(targetPeer, messageSize);
        totalDelay.addAndGet(latency);
        
        if (latency > 0) {
            recordEvent(NetworkEvent.EventType.MESSAGE_DELAYED, 
                "Message to " + targetPeer + " delayed by " + latency + "ms");
        } else {
            recordEvent(NetworkEvent.EventType.MESSAGE_SENT, "Message sent to " + targetPeer);
        }
        
        return new MessageDeliveryResult(true, latency, "Delivered");
    }
    
    /**
     * Result of a message send simulation
     */
    public static class MessageDeliveryResult {
        private final boolean delivered;
        private final long latencyMs;
        private final String reason;
        
        public MessageDeliveryResult(boolean delivered, long latencyMs, String reason) {
            this.delivered = delivered;
            this.latencyMs = latencyMs;
            this.reason = reason;
        }
        
        public boolean isDelivered() { return delivered; }
        public long getLatencyMs() { return latencyMs; }
        public String getReason() { return reason; }
        
        @Override
        public String toString() {
            return String.format("MessageDelivery{delivered=%s, latency=%dms, reason='%s'}", 
                delivered, latencyMs, reason);
        }
    }
    
    /**
     * Changes the network profile dynamically
     * 
     * @param newProfile New network profile to use
     */
    public void changeNetworkProfile(NetworkProfile newProfile) {
        NetworkProfile oldProfile = this.currentProfile;
        this.currentProfile = newProfile;
        
        recordEvent(NetworkEvent.EventType.CONDITION_CHANGED, 
            "Profile changed from " + oldProfile + " to " + newProfile);
        
        System.out.println(memberId + " network profile changed to " + newProfile);
        
        // Special behavior for LATENT profile - simulate cafe connection
        if (newProfile == NetworkProfile.LATENT && random.nextDouble() < 0.3) {
            System.out.println(memberId + " found good connection at Sheoak Café!");
            simulateTemporaryImprovement(10000); // 10 seconds of good connection
        }
    }
    
    /**
     * Simulates network partition with specific peers
     * 
     * @param peers Set of peer IDs to partition from
     * @param durationMs How long the partition lasts
     */
    public void simulatePartition(Set<String> peers, long durationMs) {
        partitionedPeers.addAll(peers);
        recordEvent(NetworkEvent.EventType.PARTITION_STARTED, 
            "Partitioned from " + peers.size() + " peers: " + peers);
        
        System.out.println(memberId + " network partitioned from " + peers);
        
        // Schedule partition recovery
        behaviorExecutor.schedule(() -> {
            partitionedPeers.removeAll(peers);
            recordEvent(NetworkEvent.EventType.PARTITION_ENDED, 
                "Partition ended with " + peers.size() + " peers");
            System.out.println(memberId + " network partition recovered");
        }, durationMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Simulates going completely offline
     * 
     * @param durationMs How long to stay offline
     */
    public void simulateOffline(long durationMs) {
        NetworkCondition oldCondition = currentCondition;
        currentCondition = NetworkCondition.OFFLINE;
        
        recordEvent(NetworkEvent.EventType.CONDITION_CHANGED, 
            "Going offline for " + durationMs + "ms");
        
        System.out.println(memberId + " going offline for " + durationMs + "ms");
        
        // Schedule recovery
        behaviorExecutor.schedule(() -> {
            currentCondition = NetworkCondition.RECOVERING;
            recordEvent(NetworkEvent.EventType.CONNECTION_RECOVERED, "Coming back online");
            
            // Gradual recovery
            behaviorExecutor.schedule(() -> {
                currentCondition = NetworkCondition.NORMAL;
                recordEvent(NetworkEvent.EventType.CONDITION_CHANGED, "Fully recovered");
                System.out.println(memberId + " network fully recovered");
            }, currentProfile.getRecoveryModel().getAverageRecoveryTime(), TimeUnit.MILLISECONDS);
            
        }, durationMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Gets current network statistics
     * 
     * @return Formatted statistics string
     */
    public String getNetworkStatistics() {
        long totalMessages = totalMessagesSent.get();
        long lostMessages = totalMessagesLost.get();
        long avgDelay = totalMessages > 0 ? totalDelay.get() / totalMessages : 0;
        double lossRate = totalMessages > 0 ? (double) lostMessages / totalMessages * 100 : 0;
        
        return String.format(
            "Network Statistics for %s:\n" +
            "  Profile: %s, Condition: %s\n" +
            "  Messages Sent: %d\n" +
            "  Messages Lost: %d (%.1f%%)\n" +
            "  Average Latency: %dms\n" +
            "  Partitioned Peers: %d\n" +
            "  Recent Events: %d",
            memberId, currentProfile, currentCondition,
            totalMessages, lostMessages, lossRate, avgDelay,
            partitionedPeers.size(), recentEvents.size()
        );
    }
    
    /**
     * Gets recent network events
     * 
     * @param count Maximum number of events to return
     * @return List of recent events
     */
    public List<NetworkEvent> getRecentEvents(int count) {
        return recentEvents.stream()
            .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
            .limit(count)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Shuts down the network behavior simulator
     */
    public void shutdown() {
        isActive.set(false);
        behaviorExecutor.shutdown();
        
        try {
            if (!behaviorExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                behaviorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            behaviorExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println(memberId + " network behavior simulator shut down");
    }
    
    // Private helper methods
    
    private boolean shouldSimulateConnectionFailure() {
        if (currentCondition == NetworkCondition.OFFLINE) {
            return true;
        }
        
        double failureRate = currentProfile.getFailureModel().getConnectionFailureRate();
        
        // Increase failure rate in degraded conditions
        if (currentCondition == NetworkCondition.DEGRADED) {
            failureRate *= 2.0;
        } else if (currentCondition == NetworkCondition.RECOVERING) {
            failureRate *= 1.5;
        }
        
        return random.nextDouble() < failureRate;
    }
    
    private boolean shouldSimulateMessageDrop() {
        if (currentCondition == NetworkCondition.OFFLINE) {
            return true;
        }
        
        double dropRate = currentProfile.getFailureModel().getMessageDropRate();
        
        // Adjust drop rate based on current condition
        switch (currentCondition) {
            case DEGRADED:
                dropRate *= 3.0;
                break;
            case PARTITIONED:
                dropRate *= 5.0;
                break;
            case RECOVERING:
                dropRate *= 2.0;
                break;
        }
        
        return random.nextDouble() < dropRate;
    }
    
    private long calculateMessageLatency(String targetPeer, int messageSize) {
        if (currentCondition == NetworkCondition.OFFLINE) {
            return -1; // Indicate message cannot be sent
        }
        
        LatencyModel latency = currentProfile.getLatencyModel();
        long baseLatency = latency.getBaseLatency();
        
        // Add jitter
        long jitter = random.nextInt(latency.getJitter() * 2) - latency.getJitter();
        
        // Check for latency spikes
        if (random.nextDouble() < latency.getSpikeRate()) {
            long spike = baseLatency + random.nextInt((int)(latency.getMaxLatency() - baseLatency));
            baseLatency = spike;
        }
        
        // Message size affects latency (larger messages take longer)
        long sizeLatency = messageSize / 100; // 1ms per 100 bytes
        
        // Condition-based adjustments
        double conditionMultiplier = 1.0;
        switch (currentCondition) {
            case DEGRADED:
                conditionMultiplier = 2.0;
                break;
            case RECOVERING:
                conditionMultiplier = 1.5;
                break;
            case PARTITIONED:
                // Some messages might still get through with high delay
                conditionMultiplier = 5.0;
                break;
        }
        
        long totalLatency = (long) ((baseLatency + jitter + sizeLatency) * conditionMultiplier);
        
        // Update peer latency tracking
        peerLatencies.put(targetPeer, totalLatency);
        
        return Math.max(0, totalLatency);
    }
    
    private void simulateConnectionFailure() {
        long downtime = currentProfile.getFailureModel().getAverageDowntimeMs() +
                       random.nextInt((int) currentProfile.getFailureModel().getAverageDowntimeMs());
        
        NetworkCondition oldCondition = currentCondition;
        currentCondition = NetworkCondition.OFFLINE;
        
        behaviorExecutor.schedule(() -> {
            currentCondition = NetworkCondition.RECOVERING;
            
            // Gradual recovery
            behaviorExecutor.schedule(() -> {
                if (random.nextDouble() < currentProfile.getRecoveryModel().getStabilityFactor()) {
                    currentCondition = NetworkCondition.NORMAL;
                    recordEvent(NetworkEvent.EventType.CONNECTION_RECOVERED, "Connection restored");
                } else {
                    currentCondition = NetworkCondition.DEGRADED;
                    recordEvent(NetworkEvent.EventType.CONNECTION_RECOVERED, "Connection restored (degraded)");
                }
            }, currentProfile.getRecoveryModel().getAverageRecoveryTime(), TimeUnit.MILLISECONDS);
            
        }, downtime, TimeUnit.MILLISECONDS);
    }
    
    private void simulateTemporaryImprovement(long durationMs) {
        NetworkCondition oldCondition = currentCondition;
        currentCondition = NetworkCondition.NORMAL;
        
        recordEvent(NetworkEvent.EventType.CONDITION_CHANGED, "Temporary improvement for " + durationMs + "ms");
        
        behaviorExecutor.schedule(() -> {
            currentCondition = oldCondition;
            recordEvent(NetworkEvent.EventType.CONDITION_CHANGED, "Improvement ended, back to " + oldCondition);
        }, durationMs, TimeUnit.MILLISECONDS);
    }
    
    private void startDynamicBehaviorSimulation() {
        // Periodic condition changes for realistic behavior
        behaviorExecutor.scheduleAtFixedRate(() -> {
            if (!isActive.get()) return;
            
            // Simulate random condition changes based on profile
            if (currentProfile == NetworkProfile.FAILURE && random.nextDouble() < 0.1) {
                // M3 occasionally goes completely offline (camping)
                simulateOffline(3000 + random.nextInt(5000));
            } else if (currentProfile == NetworkProfile.LATENT && random.nextDouble() < 0.15) {
                // M2 occasionally has connection issues
                NetworkCondition oldCondition = currentCondition;
                currentCondition = NetworkCondition.DEGRADED;
                
                behaviorExecutor.schedule(() -> {
                    currentCondition = oldCondition;
                }, 2000 + random.nextInt(3000), TimeUnit.MILLISECONDS);
            }
            
        }, 10, 10, TimeUnit.SECONDS);
        
        // Cleanup old events
        behaviorExecutor.scheduleAtFixedRate(() -> {
            long cutoff = System.currentTimeMillis() - 60000; // Keep 1 minute of events
            recentEvents.removeIf(event -> event.getTimestamp() < cutoff);
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    private void recordEvent(NetworkEvent.EventType type, String description) {
        NetworkEvent event = new NetworkEvent(type, description);
        recentEvents.offer(event);
        
        // Keep only recent events
        if (recentEvents.size() > 100) {
            recentEvents.poll();
        }
    }
    
    // Getter methods
    public NetworkProfile getCurrentProfile() { return currentProfile; }
    public NetworkCondition getCurrentCondition() { return currentCondition; }
    public boolean isActive() { return isActive.get(); }
    public long getTotalMessagesSent() { return totalMessagesSent.get(); }
    public long getTotalMessagesLost() { return totalMessagesLost.get(); }
    public double getMessageLossRate() { 
        long total = totalMessagesSent.get();
        return total > 0 ? (double) totalMessagesLost.get() / total : 0.0;
    }
}