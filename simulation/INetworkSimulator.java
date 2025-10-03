package simulation;

import java.util.Set;

public interface INetworkSimulator {
    MessageDeliveryResult simulateMessageSend(String targetPeer, int messageSize);
    void changeNetworkProfile(NetworkBehaviorSimulator.NetworkProfile profile);
    void simulatePartition(Set<String> peers, long durationMs);
    void simulateOffline(long durationMs);
    String getNetworkStatistics();
    long getTotalMessagesSent();
    long getTotalMessagesLost();
    double getMessageLossRate();
    void shutdown();
    
    // Inner class for result
    class MessageDeliveryResult {
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
    }
}