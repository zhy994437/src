import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main class representing a council member in the Adelaide Suburbs Council election
 * Implements all three Paxos roles: Proposer, Acceptor, and Learner
 */
public class CouncilMember implements PaxosRole.Participant, NetworkManager.MessageHandler {
    
    // Network and identification
    private final String memberId;
    protected final NetworkManager networkManager;
    private final Random random;
    
    // Paxos state variables
    private final AtomicInteger proposalCounter;
    private volatile String currentProposalNumber;
    private volatile String currentProposalValue;
    private volatile boolean activelyProposing;
    
    // Acceptor state
    private volatile String highestPromisedProposal;
    private volatile String highestAcceptedProposal;
    private volatile String acceptedValue;
    
    // Learner state
    private volatile String learnedValue;
    private volatile String learnedProposalNumber;
    private final Map<String, Set<String>> acceptedProposals; // proposal -> set of acceptors
    private final Object learnerLock = new Object();
    
    // Promise and acceptance tracking for proposer
    private final Set<String> promiseResponses;
    private final Set<String> acceptedResponses;
    private final Object proposerLock = new Object();
    
    // Network behavior simulation
    private NetworkProfile profile;
    
    /**
     * Enum defining different network behavior profiles
     */
    public enum NetworkProfile {
        RELIABLE(0, 50, 0.0),      // No delay, no failures
        LATENT(1000, 3000, 0.1),   // High latency, occasional failures
        FAILURE(500, 1500, 0.3),   // Moderate latency, frequent failures
        STANDARD(100, 500, 0.05);  // Standard latency, rare failures
        
        private final int minDelayMs;
        private final int maxDelayMs;
        private final double failureRate;
        
        NetworkProfile(int minDelayMs, int maxDelayMs, double failureRate) {
            this.minDelayMs = minDelayMs;
            this.maxDelayMs = maxDelayMs;
            this.failureRate = failureRate;
        }
        
        public int getMinDelay() { return minDelayMs; }
        public int getMaxDelay() { return maxDelayMs; }
        public double getFailureRate() { return failureRate; }
    }
    
    /**
     * Constructor for CouncilMember
     * 
     * @param memberId The unique identifier for this member (e.g., "M1")
     * @param port The port to listen on for incoming connections
     * @param profile The network behavior profile to simulate
     */
    public CouncilMember(String memberId, int port, NetworkProfile profile) {
        this.memberId = memberId;
        this.networkManager = new NetworkManager(memberId, port);
        this.random = new Random();
        this.profile = profile;
        
        // Initialize Paxos state
        this.proposalCounter = new AtomicInteger(0);
        this.promiseResponses = new HashSet<>();
        this.acceptedResponses = new HashSet<>();
        this.acceptedProposals = new HashMap<>();
        
        // Set this as the message handler
        this.networkManager.setMessageHandler(this);
        
        System.out.println(memberId + " initialized with " + profile + " profile");
    }
    
    /**
     * Starts the council member (begins network operations)
     * 
     * @param configFile Path to the network configuration file
     * @throws IOException If network initialization fails
     */
    public void start(String configFile) throws IOException {
        networkManager.loadConfiguration(configFile);
        networkManager.start();
        
        System.out.println(memberId + " started and ready for election");
    }
    
    /**
     * Stops the council member and cleans up resources
     */
    public void stop() {
        networkManager.stop();
        System.out.println(memberId + " stopped");
    }
    
    // Implementation of PaxosRole.Proposer interface
    
    @Override
    public boolean propose(String candidate) {
        if (hasLearned()) {
            System.out.println(memberId + " cannot propose - consensus already reached: " + learnedValue);
            return false;
        }
        
        synchronized (proposerLock) {
            if (activelyProposing) {
                System.out.println(memberId + " already has an active proposal");
                return false;
            }
            
            // Generate unique proposal number: counter.memberId
            int counter = proposalCounter.incrementAndGet();
            int memberNumber = Integer.parseInt(memberId.substring(1)); // Extract number from "M1", "M2", etc.
            currentProposalNumber = counter + "." + memberNumber;
            currentProposalValue = candidate;
            activelyProposing = true;
            
            promiseResponses.clear();
            acceptedResponses.clear();
            
            // Call hook method for subclass extension
            onProposalStart(candidate);
            
            System.out.println(memberId + " proposing candidate " + candidate + 
                             " with proposal number " + currentProposalNumber);
        }
        
        // Phase 1: Send PREPARE messages
        PaxosMessage prepareMessage = new PaxosMessage(
            PaxosMessage.MessageType.PREPARE, 
            memberId, 
            currentProposalNumber, 
            null
        );
        
        simulateNetworkDelay();
        networkManager.broadcastMessage(prepareMessage);
        
        return true;
    }
    
    @Override
    public void handlePromise(PaxosMessage message) {
        synchronized (proposerLock) {
            if (!activelyProposing || 
                !currentProposalNumber.equals(message.getProposalNumber())) {
                return; // Not our proposal or we're not proposing
            }
            
            promiseResponses.add(message.getSenderId());
            
            // If the promise contains a previously accepted value, we must use it
            if (message.hasAcceptedValue()) {
                // Use the value from the highest-numbered accepted proposal
                String acceptedProposalNum = message.getAcceptedProposalNumber();
                if (PaxosMessage.compareProposalNumbers(acceptedProposalNum, currentProposalNumber) < 0) {
                    currentProposalValue = message.getAcceptedValue();
                    System.out.println(memberId + " adopting previously accepted value: " + currentProposalValue);
                }
            }
            
            // Check if we have a majority of promises
            int totalMembers = networkManager.getKnownMembers().size();
            int majoritySize = (totalMembers / 2) + 1;
            
            if (promiseResponses.size() >= majoritySize) {
                System.out.println(memberId + " received majority promises (" + 
                                 promiseResponses.size() + "/" + totalMembers + ")");
                sendAcceptRequest();
            }
        }
    }
    
    @Override
    public void handleAccepted(PaxosMessage message) {
        synchronized (proposerLock) {
            if (!activelyProposing || 
                !currentProposalNumber.equals(message.getProposalNumber())) {
                return; // Not our proposal or we're not proposing
            }
            
            acceptedResponses.add(message.getSenderId());
            
            // Check if we have a majority of accepts
            int totalMembers = networkManager.getKnownMembers().size();
            int majoritySize = (totalMembers / 2) + 1;
            
            if (acceptedResponses.size() >= majoritySize) {
                System.out.println(memberId + " achieved consensus! " + 
                                 currentProposalValue + " elected as Council President");
                
                // Call hook method for subclass extension
                onConsensusReached(currentProposalValue);
                
                // Notify all learners
                PaxosMessage learnMessage = new PaxosMessage(
                    PaxosMessage.MessageType.LEARN,
                    memberId,
                    currentProposalNumber,
                    currentProposalValue
                );
                
                simulateNetworkDelay();
                networkManager.broadcastMessage(learnMessage);
                
                activelyProposing = false;
            }
        }
    }
    
    @Override
    public String getCurrentProposalNumber() {
        return currentProposalNumber;
    }
    
    @Override
    public boolean isActivelyProposing() {
        return activelyProposing;
    }
    
    // Implementation of PaxosRole.Acceptor interface
    
    @Override
    public void handlePrepare(PaxosMessage message) {
        String proposalNumber = message.getProposalNumber();
        
        // Check if we should promise
        if (highestPromisedProposal == null || 
            PaxosMessage.compareProposalNumbers(proposalNumber, highestPromisedProposal) > 0) {
            
            highestPromisedProposal = proposalNumber;
            
            // Create promise message
            PaxosMessage promiseMessage;
            if (highestAcceptedProposal != null && acceptedValue != null) {
                // Include previously accepted value
                promiseMessage = new PaxosMessage(
                    PaxosMessage.MessageType.PROMISE,
                    memberId,
                    proposalNumber,
                    null,
                    highestAcceptedProposal,
                    acceptedValue
                );
            } else {
                // No previously accepted value
                promiseMessage = new PaxosMessage(
                    PaxosMessage.MessageType.PROMISE,
                    memberId,
                    proposalNumber,
                    null
                );
            }
            
            System.out.println(memberId + " promising to " + message.getSenderId() + 
                             " for proposal " + proposalNumber);
            
            simulateNetworkDelay();
            if (!shouldSimulateFailure()) {
                networkManager.sendMessage(message.getSenderId(), promiseMessage);
            }
        } else {
            System.out.println(memberId + " rejecting PREPARE from " + message.getSenderId() + 
                             " (proposal " + proposalNumber + " <= " + highestPromisedProposal + ")");
        }
    }
    
    @Override
    public void handleAcceptRequest(PaxosMessage message) {
        String proposalNumber = message.getProposalNumber();
        String proposalValue = message.getProposalValue();
        
        // Check if we should accept
        if (highestPromisedProposal == null || 
            PaxosMessage.compareProposalNumbers(proposalNumber, highestPromisedProposal) >= 0) {
            
            highestPromisedProposal = proposalNumber;
            highestAcceptedProposal = proposalNumber;
            acceptedValue = proposalValue;
            
            // Send ACCEPTED message
            PaxosMessage acceptedMessage = new PaxosMessage(
                PaxosMessage.MessageType.ACCEPTED,
                memberId,
                proposalNumber,
                proposalValue
            );
            
            System.out.println(memberId + " accepting proposal " + proposalNumber + 
                             " with value " + proposalValue);
            
            simulateNetworkDelay();
            if (!shouldSimulateFailure()) {
                networkManager.sendMessage(message.getSenderId(), acceptedMessage);
                // Also broadcast to all learners
                networkManager.broadcastMessage(acceptedMessage);
            }
        } else {
            System.out.println(memberId + " rejecting ACCEPT_REQUEST from " + message.getSenderId() + 
                             " (proposal " + proposalNumber + " < " + highestPromisedProposal + ")");
        }
    }
    
    @Override
    public String getHighestPromisedProposal() {
        return highestPromisedProposal;
    }
    
    @Override
    public String getHighestAcceptedProposal() {
        return highestAcceptedProposal;
    }
    
    @Override
    public String getAcceptedValue() {
        return acceptedValue;
    }
    
    // Implementation of PaxosRole.Learner interface
    @Override
    public void handleLearn(PaxosMessage message) {
        synchronized (learnerLock) {
            if (!hasLearned()) {
                learnedValue = message.getProposalValue();
                learnedProposalNumber = message.getProposalNumber();
                
                System.out.println("CONSENSUS: " + learnedValue + " has been elected Council President!");
                System.out.println(memberId + " learned consensus directly from " + message.getSenderId());
            }
        }
    }
    
    @Override
    public String getLearnedValue() {
        return learnedValue;
    }
    
    @Override
    public boolean hasLearned() {
        return learnedValue != null;
    }
    
    @Override
    public String getLearnedProposalNumber() {
        return learnedProposalNumber;
    }
    
    // Implementation of PaxosRole.Participant interface
    
    @Override
    public void processMessage(PaxosMessage message) {
        // Route message to appropriate handler based on type
        switch (message.getType()) {
            case PREPARE:
                handlePrepare(message);
                break;
            case PROMISE:
                handlePromise(message);
                break;
            case ACCEPT_REQUEST:
                handleAcceptRequest(message);
                break;
            case ACCEPTED:
                handleAccepted(message);
                break;
            case LEARN:
                handleLearn(message);
                break;
            default:
                System.err.println(memberId + " received unknown message type: " + message.getType());
        }
    }
    
    @Override
    public String getMemberId() {
        return memberId;
    }
    
    @Override
    public void reset() {
        synchronized (proposerLock) {
            activelyProposing = false;
            currentProposalNumber = null;
            currentProposalValue = null;
            promiseResponses.clear();
            acceptedResponses.clear();
        }
        
        // Don't reset learned values - they should persist
        // Don't reset acceptor state - promises should persist across proposals
    }
    
    @Override
    public String getState() {
        return String.format(
            "Member %s [Proposing: %s, Promised: %s, Accepted: %s->%s, Learned: %s]",
            memberId,
            activelyProposing ? currentProposalNumber : "No",
            highestPromisedProposal != null ? highestPromisedProposal : "None",
            highestAcceptedProposal != null ? highestAcceptedProposal : "None",
            acceptedValue != null ? acceptedValue : "None",
            learnedValue != null ? learnedValue : "None"
        );
    }
    
    // Implementation of NetworkManager.MessageHandler interface
    
    @Override
    public void handleMessage(PaxosMessage message) {
        processMessage(message);
    }
    
    // Hook methods for subclass extension
    
    /**
     * Hook method called when a proposal is initiated.
     * Subclasses can override this method to add custom behavior
     * at the start of a proposal.
     * 
     * @param candidate The candidate being proposed
     */
    protected void onProposalStart(String candidate) {
        // Default implementation does nothing
        // Subclasses can override to add custom behavior
    }
    
    /**
     * Hook method called when consensus is reached.
     * Subclasses can override this method to add custom behavior
     * when consensus is achieved.
     * 
     * @param value The value that achieved consensus
     */
    protected void onConsensusReached(String value) {
        // Default implementation does nothing
        // Subclasses can override to add custom behavior
    }
    
    // Private helper methods
    
    /**
     * Sends ACCEPT_REQUEST messages to all acceptors (Phase 2 of Paxos)
     */
    private void sendAcceptRequest() {
        PaxosMessage acceptRequest = new PaxosMessage(
            PaxosMessage.MessageType.ACCEPT_REQUEST,
            memberId,
            currentProposalNumber,
            currentProposalValue
        );
        
        System.out.println(memberId + " sending ACCEPT_REQUEST for value: " + currentProposalValue);
        
        simulateNetworkDelay();
        networkManager.broadcastMessage(acceptRequest);
    }
    
    /**
     * Simulates network delay based on the member's profile
     */
    private void simulateNetworkDelay() {
        if (profile == NetworkProfile.RELIABLE) {
            return; // No delay for reliable members
        }
        
        int minDelay = profile.getMinDelay();
        int maxDelay = profile.getMaxDelay();
        int delay = minDelay + random.nextInt(maxDelay - minDelay + 1);
        
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Determines if a network failure should be simulated
     * 
     * @return true if the message should be "lost"
     */
    private boolean shouldSimulateFailure() {
        return random.nextDouble() < profile.getFailureRate();
    }
    
    /**
     * Changes the network profile at runtime
     * 
     * @param newProfile The new network profile to use
     */
    public void setNetworkProfile(NetworkProfile newProfile) {
        this.profile = newProfile;
        System.out.println(memberId + " switched to " + newProfile + " profile");
    }
    
    /**
     * Triggers a proposal for the specified candidate
     * This method can be called from user input or automated testing
     * 
     * @param candidate The candidate to propose
     */
    public void proposeCandidate(String candidate) {
        if (candidate == null || candidate.trim().isEmpty()) {
            System.err.println("Invalid candidate name");
            return;
        }
        
        propose(candidate.trim());
    }
    
    /**
     * Main method to run a council member
     * 
     * @param args Command line arguments: memberId [--profile profileName] [--config configFile]
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java CouncilMember <memberId> [--profile <profile>] [--config <configFile>]");
            System.err.println("Profiles: reliable, latent, failure, standard");
            System.exit(1);
        }
        
        String memberId = args[0];
        NetworkProfile profile = NetworkProfile.STANDARD;
        String configFile = "network.config";
        
        // Parse command line arguments
        for (int i = 1; i < args.length; i++) {
            if ("--profile".equals(args[i]) && i + 1 < args.length) {
                try {
                    profile = NetworkProfile.valueOf(args[i + 1].toUpperCase());
                    i++; // Skip the profile name
                } catch (IllegalArgumentException e) {
                    System.err.println("Unknown profile: " + args[i + 1]);
                    System.exit(1);
                }
            } else if ("--config".equals(args[i]) && i + 1 < args.length) {
                configFile = args[i + 1];
                i++; // Skip the config file name
            }
        }
        
        // Extract port from member ID (simple mapping: M1->9001, M2->9002, etc.)
        int memberNumber = Integer.parseInt(memberId.substring(1));
        int port = 9000 + memberNumber;
        
        CouncilMember member = new CouncilMember(memberId, port, profile);
        
        try {
            member.start(configFile);
            
            // Keep the member running and listen for input
            System.out.println(memberId + " ready. Type candidate name to propose, 'status' for state, 'quit' to exit:");
            
            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    String input = scanner.nextLine().trim();
                    
                    if ("quit".equalsIgnoreCase(input)) {
                        break;
                    } else if ("status".equalsIgnoreCase(input)) {
                        System.out.println(member.getState());
                    } else if (!input.isEmpty()) {
                        member.proposeCandidate(input);
                    }
                }
            }
            
        } catch (IOException e) {
            System.err.println("Failed to start " + memberId + ": " + e.getMessage());
        } finally {
            member.stop();
        }
    }
}