package core;

public final class PaxosConstants {
    // Network configuration constants
    public static final int BASE_PORT = 9000;
    public static final int TOTAL_MEMBERS = 9;
    public static final int MAJORITY_SIZE = (TOTAL_MEMBERS / 2) + 1;
    
    // Timeout configuration
    public static final long PHASE_TIMEOUT_MS = 5000;
    public static final long CONSENSUS_TIMEOUT_MS = 30000;
    public static final long MESSAGE_TIMEOUT_MS = 1000;
    public static final long CLEANUP_INTERVAL_MS = 30000;
    
    // Network behavior parameters
    public static final long MIN_BACKOFF_MS = 100;
    public static final long MAX_BACKOFF_MS = 5000;
    public static final double BACKOFF_MULTIPLIER = 1.5;
    
    // Message format
    public static final String MESSAGE_DELIMITER = ":";
    public static final String CONSENSUS_PREFIX = "CONSENSUS: ";
    public static final String COUNCIL_PRESIDENT_SUFFIX = " has been elected Council President!";
    
    // Test configuration
    public static final long TEST_TIMEOUT_MS = 60000;
    public static final int MAX_HISTORY_SIZE = 100;
    public static final long CONFLICT_DETECTION_WINDOW_MS = 10000;
    
    // Private constructor to prevent instantiation
    private PaxosConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}