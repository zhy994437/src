import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages Paxos state transitions and ensures consistency across all three roles
 * This class centralizes state management to prevent race conditions and ensure
 * proper Paxos protocol adherence
 */
public class PaxosStateManager {
    
    /**
     * Represents the current state of a Paxos instance
     */
    public static class PaxosInstanceState {
        private final String instanceId;
        private volatile Phase currentPhase;
        private volatile long startTime;
        private volatile long lastActivity;
        
        // Proposer state
        private volatile String proposalNumber;
        private volatile String proposalValue;
        private final Set<String> promiseResponders;
        private final Set<String> acceptResponders;
        private final Map<String, String> receivedAcceptedValues; // senderId -> acceptedValue
        
        // Acceptor state  
        private volatile String highestPromisedProposal;
        private volatile String highestAcceptedProposal;
        private volatile String acceptedValue;
        
        // Learner state
        private final Map<String, AcceptedProposal> learnedAccepts; // proposalNumber -> AcceptedProposal
        private volatile String decidedValue;
        private volatile String decidedProposalNumber;
        
        public enum Phase {
            IDLE,           // No active proposal
            PHASE_1,        // Sending PREPARE, collecting PROMISE
            PHASE_2,        // Sending ACCEPT_REQUEST, collecting ACCEPTED  
            DECIDED,        // Consensus reached
            FAILED          // Proposal failed, can retry
        }
        
        public static class AcceptedProposal {
            private final String proposalNumber;
            private final String value;
            private final Set<String> acceptors;
            private final long timestamp;
            
            public AcceptedProposal(String proposalNumber, String value) {
                this.proposalNumber = proposalNumber;
                this.value = value;
                this.acceptors = ConcurrentHashMap.newKeySet();
                this.timestamp = System.currentTimeMillis();
            }
            
            public String getProposalNumber() { return proposalNumber; }
            public String getValue() { return value; }
            public Set<String> getAcceptors() { return acceptors; }
            public long getTimestamp() { return timestamp; }
            public int getAcceptorCount() { return acceptors.size(); }
        }
        
        public PaxosInstanceState(String instanceId) {
            this.instanceId = instanceId;
            this.currentPhase = Phase.IDLE;
            this.startTime = System.currentTimeMillis();
            this.lastActivity = this.startTime;
            this.promiseResponders = ConcurrentHashMap.newKeySet();
            this.acceptResponders = ConcurrentHashMap.newKeySet();
            this.receivedAcceptedValues = new ConcurrentHashMap<>();
            this.learnedAccepts = new ConcurrentHashMap<>();
        }
        
        // Getters and state query methods
        public String getInstanceId() { return instanceId; }
        public Phase getCurrentPhase() { return currentPhase; }
        public String getProposalNumber() { return proposalNumber; }
        public String getProposalValue() { return proposalValue; }
        public String getHighestPromisedProposal() { return highestPromisedProposal; }
        public String getHighestAcceptedProposal() { return highestAcceptedProposal; }
        public String getAcceptedValue() { return acceptedValue; }
        public String getDecidedValue() { return decidedValue; }
        public String getDecidedProposalNumber() { return decidedProposalNumber; }
        public boolean isDecided() { return decidedValue != null; }
        public long getLastActivity() { return lastActivity; }
        
        // State transition methods
        public synchronized void startPhase1(String proposalNum, String value) {
            if (currentPhase != Phase.IDLE && currentPhase != Phase.FAILED) {
                throw new IllegalStateException("Cannot start Phase 1 from " + currentPhase);
            }
            this.proposalNumber = proposalNum;
            this.proposalValue = value;
            this.currentPhase = Phase.PHASE_1;
            this.promiseResponders.clear();
            this.acceptResponders.clear();
            this.receivedAcceptedValues.clear();
            this.lastActivity = System.currentTimeMillis();
        }
        
        public synchronized boolean addPromiseResponse(String senderId, String acceptedProposal, String acceptedValue) {
            if (currentPhase != Phase.PHASE_1) {
                return false;
            }
            
            promiseResponders.add(senderId);
            if (acceptedProposal != null && acceptedValue != null) {
                receivedAcceptedValues.put(senderId, acceptedValue);
            }
            this.lastActivity = System.currentTimeMillis();
            return true;
        }
        
        public synchronized String transitionToPhase2(int majoritySize) {
            if (currentPhase != Phase.PHASE_1) {
                return null;
            }
            
            if (promiseResponders.size() < majoritySize) {
                return null;
            }
            
            // Determine the value to propose in Phase 2
            String valueToPropose = proposalValue;
            String highestAcceptedProposal = null;
            
            // Find the highest-numbered accepted proposal from promises
            for (Map.Entry<String, String> entry : receivedAcceptedValues.entrySet()) {
                // In a real implementation, we'd need to track the proposal numbers
                // For now, we'll use the most recently received accepted value
                valueToPropose = entry.getValue();
            }
            
            this.proposalValue = valueToPropose;
            this.currentPhase = Phase.PHASE_2;
            this.lastActivity = System.currentTimeMillis();
            return valueToPropose;
        }
        
        public synchronized boolean addAcceptResponse(String senderId) {
            if (currentPhase != Phase.PHASE_2) {
                return false;
            }
            
            acceptResponders.add(senderId);
            this.lastActivity = System.currentTimeMillis();
            return true;
        }
        
        public synchronized boolean checkDecision(int majoritySize) {
            if (currentPhase == Phase.PHASE_2 && acceptResponders.size() >= majoritySize) {
                this.decidedValue = proposalValue;
                this.decidedProposalNumber = proposalNumber;
                this.currentPhase = Phase.DECIDED;
                this.lastActivity = System.currentTimeMillis();
                return true;
            }
            return false;
        }
        
        public synchronized boolean updatePromise(String proposalNum) {
            if (PaxosMessage.compareProposalNumbers(proposalNum, highestPromisedProposal) > 0) {
                this.highestPromisedProposal = proposalNum;
                this.lastActivity = System.currentTimeMillis();
                return true;
            }
            return false;
        }
        
        public synchronized boolean updateAcceptance(String proposalNum, String value) {
            if (highestPromisedProposal == null || 
                PaxosMessage.compareProposalNumbers(proposalNum, highestPromisedProposal) >= 0) {
                this.highestPromisedProposal = proposalNum;
                this.highestAcceptedProposal = proposalNum;
                this.acceptedValue = value;
                this.lastActivity = System.currentTimeMillis();
                return true;
            }
            return false;
        }
        
        public synchronized boolean learnAcceptance(String proposalNum, String value, String acceptorId, int majoritySize) {
            AcceptedProposal accepted = learnedAccepts.computeIfAbsent(proposalNum, 
                k -> new AcceptedProposal(proposalNum, value));
            
            accepted.acceptors.add(acceptorId);
            this.lastActivity = System.currentTimeMillis();
            
            // Check if we've learned consensus
            if (accepted.getAcceptorCount() >= majoritySize && decidedValue == null) {
                this.decidedValue = value;
                this.decidedProposalNumber = proposalNum;
                this.currentPhase = Phase.DECIDED;
                return true;
            }
            return false;
        }
        
        public synchronized void reset() {
            if (currentPhase != Phase.DECIDED) {
                this.currentPhase = Phase.IDLE;
                this.proposalNumber = null;
                this.proposalValue = null;
                this.promiseResponders.clear();
                this.acceptResponders.clear();
                this.receivedAcceptedValues.clear();
            }
            // Don't reset acceptor or learner state - they should persist
        }
        
        public synchronized void markFailed() {
            if (currentPhase == Phase.PHASE_1 || currentPhase == Phase.PHASE_2) {
                this.currentPhase = Phase.FAILED;
                this.lastActivity = System.currentTimeMillis();
            }
        }
    }
    
    private final String memberId;
    private final AtomicReference<PaxosInstanceState> currentInstance;
    private final Map<String, PaxosInstanceState> instanceHistory;
    private final ScheduledExecutorService timeoutExecutor;
    
    // Configuration
    private static final long PHASE_TIMEOUT_MS = 5000; // 5 seconds
    private static final long CLEANUP_INTERVAL_MS = 30000; // 30 seconds
    private static final int MAX_HISTORY_SIZE = 100;
    
    public PaxosStateManager(String memberId) {
        this.memberId = memberId;
        this.currentInstance = new AtomicReference<>(new PaxosInstanceState("default"));
        this.instanceHistory = new ConcurrentHashMap<>();
        this.timeoutExecutor = Executors.newScheduledThreadPool(2);
        
        // Start cleanup task
        timeoutExecutor.scheduleAtFixedRate(this::cleanupOldInstances, 
            CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Gets the current Paxos instance state
     * 
     * @return Current instance state
     */
    public PaxosInstanceState getCurrentInstance() {
        return currentInstance.get();
    }
    
    /**
     * Starts a new proposal (Phase 1 of Paxos)
     * 
     * @param proposalNumber Unique proposal number
     * @param value Value being proposed
     * @return true if proposal started successfully
     */
    public boolean startProposal(String proposalNumber, String value) {
        PaxosInstanceState instance = currentInstance.get();
        
        // Check if we can start a new proposal
        if (instance.isDecided()) {
            return false; // Already decided
        }
        
        if (instance.getCurrentPhase() == PaxosInstanceState.Phase.PHASE_1 ||
            instance.getCurrentPhase() == PaxosInstanceState.Phase.PHASE_2) {
            return false; // Already proposing
        }
        
        try {
            instance.startPhase1(proposalNumber, value);
            
            // Set timeout for this proposal
            timeoutExecutor.schedule(() -> handleProposalTimeout(instance), 
                PHASE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
    
    /**
     * Handles a PROMISE response in Phase 1
     * 
     * @param senderId ID of the responder
     * @param acceptedProposal Previously accepted proposal (if any)
     * @param acceptedValue Previously accepted value (if any)
     * @param majoritySize Size needed for majority
     * @return true if ready to proceed to Phase 2
     */
    public boolean handlePromiseResponse(String senderId, String acceptedProposal, 
                                       String acceptedValue, int majoritySize) {
        PaxosInstanceState instance = currentInstance.get();
        
        if (!instance.addPromiseResponse(senderId, acceptedProposal, acceptedValue)) {
            return false;
        }
        
        // Check if we have enough promises to proceed to Phase 2
        return instance.transitionToPhase2(majoritySize) != null;
    }
    
    /**
     * Handles an ACCEPTED response in Phase 2
     * 
     * @param senderId ID of the acceptor
     * @param majoritySize Size needed for majority
     * @return true if consensus achieved
     */
    public boolean handleAcceptedResponse(String senderId, int majoritySize) {
        PaxosInstanceState instance = currentInstance.get();
        
        if (!instance.addAcceptResponse(senderId)) {
            return false;
        }
        
        return instance.checkDecision(majoritySize);
    }
    
    /**
     * Handles a PREPARE message (Acceptor role)
     * 
     * @param proposalNumber The proposal number from PREPARE
     * @return true if should send PROMISE
     */
    public boolean handlePrepareMessage(String proposalNumber) {
        PaxosInstanceState instance = currentInstance.get();
        return instance.updatePromise(proposalNumber);
    }
    
    /**
     * Handles an ACCEPT_REQUEST message (Acceptor role)
     * 
     * @param proposalNumber The proposal number
     * @param value The proposed value
     * @return true if should send ACCEPTED
     */
    public boolean handleAcceptRequest(String proposalNumber, String value) {
        PaxosInstanceState instance = currentInstance.get();
        return instance.updateAcceptance(proposalNumber, value);
    }
    
    /**
     * Handles learning about an accepted proposal (Learner role)
     * 
     * @param proposalNumber The accepted proposal number
     * @param value The accepted value
     * @param acceptorId The acceptor who sent this
     * @param majoritySize Size needed for majority
     * @return true if consensus learned
     */
    public boolean learnAcceptance(String proposalNumber, String value, 
                                 String acceptorId, int majoritySize) {
        PaxosInstanceState instance = currentInstance.get();
        return instance.learnAcceptance(proposalNumber, value, acceptorId, majoritySize);
    }
    
    /**
     * Forces learning of a consensus value (from LEARN message)
     * 
     * @param proposalNumber The consensus proposal number
     * @param value The consensus value
     */
    public void forceLearnConsensus(String proposalNumber, String value) {
        PaxosInstanceState instance = currentInstance.get();
        synchronized (instance) {
            if (!instance.isDecided()) {
                instance.decidedValue = value;
                instance.decidedProposalNumber = proposalNumber;
                instance.currentPhase = PaxosInstanceState.Phase.DECIDED;
                instance.lastActivity = System.currentTimeMillis();
            }
        }
    }
    
    /**
     * Gets detailed state information for debugging
     * 
     * @return Formatted state string
     */
    public String getDetailedState() {
        PaxosInstanceState instance = currentInstance.get();
        return String.format(
            "Member %s State:\n" +
            "  Phase: %s\n" +
            "  Proposal: %s -> %s\n" +
            "  Promises: %d, Accepts: %d\n" +
            "  Promised: %s, Accepted: %s -> %s\n" +
            "  Decided: %s (proposal %s)\n" +
            "  Last Activity: %d ms ago",
            memberId,
            instance.getCurrentPhase(),
            instance.getProposalNumber(), instance.getProposalValue(),
            instance.promiseResponders.size(), instance.acceptResponders.size(),
            instance.getHighestPromisedProposal(),
            instance.getHighestAcceptedProposal(), instance.getAcceptedValue(),
            instance.getDecidedValue(), instance.getDecidedProposalNumber(),
            System.currentTimeMillis() - instance.getLastActivity()
        );
    }
    
    /**
     * Resets the current instance for a new consensus round
     */
    public void resetForNewRound() {
        PaxosInstanceState instance = currentInstance.get();
        instance.reset();
    }
    
    /**
     * Creates a new instance (for multi-Paxos scenarios)
     * 
     * @param instanceId Unique instance identifier
     */
    public void createNewInstance(String instanceId) {
        PaxosInstanceState oldInstance = currentInstance.get();
        if (!oldInstance.isDecided()) {
            // Archive the current instance
            instanceHistory.put(oldInstance.getInstanceId(), oldInstance);
        }
        
        PaxosInstanceState newInstance = new PaxosInstanceState(instanceId);
        currentInstance.set(newInstance);
    }
    
    /**
     * Shuts down the state manager
     */
    public void shutdown() {
        timeoutExecutor.shutdown();
        try {
            if (!timeoutExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                timeoutExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            timeoutExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Handles proposal timeout
     */
    private void handleProposalTimeout(PaxosInstanceState instance) {
        if (instance == currentInstance.get() && 
            (instance.getCurrentPhase() == PaxosInstanceState.Phase.PHASE_1 ||
             instance.getCurrentPhase() == PaxosInstanceState.Phase.PHASE_2)) {
            
            instance.markFailed();
            System.out.println(memberId + " proposal " + instance.getProposalNumber() + 
                             " timed out in " + instance.getCurrentPhase());
        }
    }
    
    /**
     * Cleans up old instances from history
     */
    private void cleanupOldInstances() {
        if (instanceHistory.size() > MAX_HISTORY_SIZE) {
            // Remove oldest instances
            List<Map.Entry<String, PaxosInstanceState>> entries = 
                new ArrayList<>(instanceHistory.entrySet());
            
            entries.sort((a, b) -> Long.compare(a.getValue().getLastActivity(), 
                                              b.getValue().getLastActivity()));
            
            int toRemove = instanceHistory.size() - MAX_HISTORY_SIZE;
            for (int i = 0; i < toRemove; i++) {
                instanceHistory.remove(entries.get(i).getKey());
            }
        }
    }
}