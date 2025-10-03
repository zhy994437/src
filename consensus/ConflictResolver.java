import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles conflict resolution when multiple members propose simultaneously
 * Implements advanced Paxos conflict resolution strategies including:
 * - Proposal number collision detection
 * - Backoff strategies for failed proposals
 * - Priority-based proposal scheduling
 * - Dueling proposers detection and resolution
 */
public class ConflictResolver {
    
    private final String memberId;
    private final Random random;
    private final AtomicLong lastProposalTime;
    private final Map<String, Long> memberLastProposal;
    private final Queue<ConflictResolutionStrategy> strategyQueue;
    
    // Backoff configuration
    private static final long MIN_BACKOFF_MS = 100;
    private static final long MAX_BACKOFF_MS = 5000;
    private static final double BACKOFF_MULTIPLIER = 1.5;
    private volatile long currentBackoff = MIN_BACKOFF_MS;
    
    // Conflict detection
    private final Map<String, ProposalAttempt> recentProposals;
    private static final long CONFLICT_DETECTION_WINDOW_MS = 10000; // 10 seconds
    
    /**
     * Represents a proposal attempt for conflict tracking
     */
    public static class ProposalAttempt {
        private final String proposalNumber;
        private final String value;
        private final long timestamp;
        private final String memberId;
        private volatile ProposalStatus status;
        
        public enum ProposalStatus {
            PREPARING,      // Sending PREPARE messages
            ACCEPTING,      // Sending ACCEPT_REQUEST messages
            SUCCEEDED,      // Proposal succeeded
            FAILED,         // Proposal failed
            CONFLICTED      // Detected conflict with another proposal
        }
        
        public ProposalAttempt(String proposalNumber, String value, String memberId) {
            this.proposalNumber = proposalNumber;
            this.value = value;
            this.memberId = memberId;
            this.timestamp = System.currentTimeMillis();
            this.status = ProposalStatus.PREPARING;
        }
        
        // Getters
        public String getProposalNumber() { return proposalNumber; }
        public String getValue() { return value; }
        public long getTimestamp() { return timestamp; }
        public String getMemberId() { return memberId; }
        public ProposalStatus getStatus() { return status; }
        
        public void setStatus(ProposalStatus status) {
            this.status = status;
        }
        
        public boolean isActive() {
            return status == ProposalStatus.PREPARING || status == ProposalStatus.ACCEPTING;
        }
        
        public long getAge() {
            return System.currentTimeMillis() - timestamp;
        }
    }
    
    /**
     * Strategy interface for conflict resolution
     */
    public interface ConflictResolutionStrategy {
        /**
         * Determines the appropriate action when a conflict is detected
         * 
         * @param myProposal My current proposal attempt
         * @param conflictingProposals List of conflicting proposals
         * @return Recommended action
         */
        ConflictAction resolveConflict(ProposalAttempt myProposal, 
                                     List<ProposalAttempt> conflictingProposals);
    }
    
    /**
     * Possible actions when conflict is detected
     */
    public enum ConflictAction {
        CONTINUE,       // Continue with current proposal
        BACKOFF,        // Back off and retry later
        ABORT,          // Abort current proposal
        YIELD,          // Yield to higher priority proposal
        ESCALATE        // Increase proposal priority
    }
    
    /**
     * Constructor for ConflictResolver
     * 
     * @param memberId The member ID this resolver belongs to
     */
    public ConflictResolver(String memberId) {
        this.memberId = memberId;
        this.random = new Random();
        this.lastProposalTime = new AtomicLong(0);
        this.memberLastProposal = new ConcurrentHashMap<>();
        this.recentProposals = new ConcurrentHashMap<>();
        this.strategyQueue = new ConcurrentLinkedQueue<>();
        
        // Initialize default strategies
        initializeDefaultStrategies();
        
        // Start cleanup task
        ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor();
        cleanup.scheduleAtFixedRate(this::cleanupOldProposals, 
            CONFLICT_DETECTION_WINDOW_MS, CONFLICT_DETECTION_WINDOW_MS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Registers a new proposal attempt
     * 
     * @param proposalNumber The proposal number
     * @param value The proposed value
     * @return ProposalAttempt object for tracking
     */
    public ProposalAttempt registerProposal(String proposalNumber, String value) {
        ProposalAttempt attempt = new ProposalAttempt(proposalNumber, value, memberId);
        recentProposals.put(proposalNumber, attempt);
        lastProposalTime.set(System.currentTimeMillis());
        
        System.out.println(memberId + " registered proposal " + proposalNumber + " for " + value);
        return attempt;
    }
    
    /**
     * Detects conflicts with other concurrent proposals
     * 
     * @param myProposal My current proposal
     * @return List of conflicting proposals
     */
    public List<ProposalAttempt> detectConflicts(ProposalAttempt myProposal) {
        List<ProposalAttempt> conflicts = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (ProposalAttempt proposal : recentProposals.values()) {
            // Skip own proposals and old proposals
            if (proposal.getMemberId().equals(memberId) || 
                currentTime - proposal.getTimestamp() > CONFLICT_DETECTION_WINDOW_MS) {
                continue;
            }
            
            // Check if proposals overlap in time and are both active
            if (proposal.isActive() && myProposal.isActive()) {
                long timeDiff = Math.abs(proposal.getTimestamp() - myProposal.getTimestamp());
                if (timeDiff < 2000) { // 2 second window for considering concurrent
                    conflicts.add(proposal);
                }
            }
        }
        
        if (!conflicts.isEmpty()) {
            System.out.println(memberId + " detected " + conflicts.size() + " conflicting proposals");
        }
        
        return conflicts;
    }
    
    /**
     * Resolves conflicts using registered strategies
     * 
     * @param myProposal My current proposal
     * @param conflicts List of conflicting proposals
     * @return Recommended action
     */
    public ConflictAction resolveConflicts(ProposalAttempt myProposal, List<ProposalAttempt> conflicts) {
        if (conflicts.isEmpty()) {
            return ConflictAction.CONTINUE;
        }
        
        // Try each strategy until one provides a definitive answer
        for (ConflictResolutionStrategy strategy : strategyQueue) {
            ConflictAction action = strategy.resolveConflict(myProposal, conflicts);
            if (action != ConflictAction.CONTINUE) {
                System.out.println(memberId + " conflict resolution: " + action + 
                                 " (strategy: " + strategy.getClass().getSimpleName() + ")");
                return action;
            }
        }
        
        return ConflictAction.CONTINUE;
    }
    
    /**
     * Calculates backoff delay after a failed proposal
     * 
     * @return Backoff delay in milliseconds
     */
    public long calculateBackoffDelay() {
        // Exponential backoff with jitter
        long delay = currentBackoff + random.nextInt((int)(currentBackoff * 0.5));
        currentBackoff = Math.min((long)(currentBackoff * BACKOFF_MULTIPLIER), MAX_BACKOFF_MS);
        
        System.out.println(memberId + " backing off for " + delay + "ms");
        return delay;
    }
    
    /**
     * Resets backoff delay after successful proposal
     */
    public void resetBackoff() {
        currentBackoff = MIN_BACKOFF_MS;
    }
    
    /**
     * Updates proposal status
     * 
     * @param proposalNumber The proposal number
     * @param status New status
     */
    public void updateProposalStatus(String proposalNumber, ProposalAttempt.ProposalStatus status) {
        ProposalAttempt proposal = recentProposals.get(proposalNumber);
        if (proposal != null) {
            proposal.setStatus(status);
            System.out.println(memberId + " updated proposal " + proposalNumber + " status to " + status);
        }
    }
    
    /**
     * Checks if a proposal number is higher priority than another
     * 
     * @param proposal1 First proposal number
     * @param proposal2 Second proposal number
     * @return true if proposal1 has higher priority
     */
    public boolean hasHigherPriority(String proposal1, String proposal2) {
        return PaxosMessage.compareProposalNumbers(proposal1, proposal2) > 0;
    }
    
    /**
     * Gets current conflict statistics
     * 
     * @return Statistics as formatted string
     */
    public String getConflictStatistics() {
        long activeProposals = recentProposals.values().stream()
            .filter(ProposalAttempt::isActive)
            .count();
        
        long recentConflicts = recentProposals.values().stream()
            .filter(p -> p.getStatus() == ProposalAttempt.ProposalStatus.CONFLICTED)
            .count();
        
        return String.format(
            "Conflict Statistics for %s:\n" +
            "  Active Proposals: %d\n" +
            "  Recent Conflicts: %d\n" +
            "  Current Backoff: %dms\n" +
            "  Last Proposal: %dms ago",
            memberId, activeProposals, recentConflicts, currentBackoff,
            System.currentTimeMillis() - lastProposalTime.get()
        );
    }
    
    /**
     * Initializes default conflict resolution strategies
     */
    private void initializeDefaultStrategies() {
        // Strategy 1: Proposal Number Priority - Higher numbered proposals have priority
        strategyQueue.offer((myProposal, conflicts) -> {
            for (ProposalAttempt conflict : conflicts) {
                if (hasHigherPriority(conflict.getProposalNumber(), myProposal.getProposalNumber())) {
                    return ConflictAction.YIELD;
                }
            }
            return ConflictAction.CONTINUE;
        });
        
        // Strategy 2: Member ID Tiebreaker - Lower member IDs have priority in ties
        strategyQueue.offer((myProposal, conflicts) -> {
            for (ProposalAttempt conflict : conflicts) {
                if (PaxosMessage.compareProposalNumbers(
                        conflict.getProposalNumber(), myProposal.getProposalNumber()) == 0) {
                    // Same proposal number, use member ID as tiebreaker
                    int myMemberNum = extractMemberNumber(memberId);
                    int conflictMemberNum = extractMemberNumber(conflict.getMemberId());
                    
                    if (conflictMemberNum < myMemberNum) {
                        return ConflictAction.BACKOFF;
                    }
                }
            }
            return ConflictAction.CONTINUE;
        });
        
        // Strategy 3: Dueling Proposers Detection - Back off if too many conflicts
        strategyQueue.offer((myProposal, conflicts) -> {
            if (conflicts.size() >= 3) {
                // Too many concurrent proposals - back off with random delay
                return ConflictAction.BACKOFF;
            }
            return ConflictAction.CONTINUE;
        });
        
        // Strategy 4: Age-based Priority - Older proposals have some priority
        strategyQueue.offer((myProposal, conflicts) -> {
            for (ProposalAttempt conflict : conflicts) {
                if (conflict.getTimestamp() < myProposal.getTimestamp() - 1000) {
                    // Conflict is significantly older, yield briefly
                    return ConflictAction.BACKOFF;
                }
            }
            return ConflictAction.CONTINUE;
        });
    }
    
    /**
     * Extracts member number from member ID
     * 
     * @param memberId Member ID (e.g., "M1")
     * @return Member number (e.g., 1)
     */
    private int extractMemberNumber(String memberId) {
        try {
            return Integer.parseInt(memberId.substring(1));
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            return 999; // Default high number for invalid IDs
        }
    }
    
    /**
     * Cleans up old proposal attempts
     */
    private void cleanupOldProposals() {
        long cutoff = System.currentTimeMillis() - CONFLICT_DETECTION_WINDOW_MS;
        recentProposals.entrySet().removeIf(entry -> entry.getValue().getTimestamp() < cutoff);
    }
    
    /**
     * Adds a custom conflict resolution strategy
     * 
     * @param strategy The strategy to add
     */
    public void addStrategy(ConflictResolutionStrategy strategy) {
        strategyQueue.offer(strategy);
    }
    
    /**
     * Clears all strategies and resets to defaults
     */
    public void resetStrategies() {
        strategyQueue.clear();
        initializeDefaultStrategies();
    }
    
    /**
     * Simulates a more intelligent proposal number generation that considers conflicts
     * 
     * @param baseCounter Base counter value
     * @return Enhanced proposal number
     */
    public String generateConflictAwareProposalNumber(int baseCounter) {
        int memberNumber = extractMemberNumber(memberId);
        
        // Check for recent conflicts and adjust accordingly
        long recentConflicts = recentProposals.values().stream()
            .filter(p -> p.getAge() < 5000 && p.getStatus() == ProposalAttempt.ProposalStatus.CONFLICTED)
            .count();
        
        if (recentConflicts > 0) {
            // Add small random factor to avoid repeated conflicts
            baseCounter += random.nextInt(10) + 1;
        }
        
        return baseCounter + "." + memberNumber;
    }
}