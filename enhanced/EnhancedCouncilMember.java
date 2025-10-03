import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Enhanced council member with additional features and testing capabilities
 * Extends CouncilMember with network simulation support and advanced state management
 */
public class EnhancedCouncilMember extends CouncilMember {
    
    // State management
    private final PaxosStateManager stateManager;
    private final ExecutorService enhancedExecutor;
    
    // Statistics tracking
    private volatile long totalProposals;
    private volatile long successfulProposals;
    private volatile long failedProposals;
    
    // Round management for multi-Paxos
    private volatile int currentRound;
    private final Map<Integer, String> roundDecisions;
    
    /**
     * Constructor for EnhancedCouncilMember
     * 
     * @param memberId Member identifier
     * @param port Network port
     * @param profile Initial network profile
     */
    public EnhancedCouncilMember(String memberId, int port, NetworkProfile profile) {
        super(memberId, port, profile);
        
        this.stateManager = new PaxosStateManager(memberId);
        this.enhancedExecutor = Executors.newFixedThreadPool(3);
        this.currentRound = 0;
        this.roundDecisions = new ConcurrentHashMap<>();
        
        System.out.println(memberId + " enhanced council member initialized");
    }
    
    /**
     * Proposes a candidate for the current round
     * 
     * @param candidate The candidate to propose
     */
    public void proposeCandidate(String candidate) {
        if (candidate == null || candidate.trim().isEmpty()) {
            System.err.println("Invalid candidate name");
            return;
        }
        
        totalProposals++;
        boolean success = propose(candidate.trim());
        
        if (success) {
            System.out.println(getMemberId() + " initiated proposal for " + candidate);
        } else {
            failedProposals++;
            System.out.println(getMemberId() + " failed to initiate proposal for " + candidate);
        }
    }
    
    /**
     * Starts a new consensus round
     */
    public void startNewRound() {
        if (hasLearned()) {
            // Save current round decision
            roundDecisions.put(currentRound, getLearnedValue());
            currentRound++;
            
            // Reset for new round
            reset();
            stateManager.resetForNewRound();
            
            System.out.println(getMemberId() + " starting new round " + currentRound);
        }
    }
    
    /**
     * Simulates message sending with network behavior
     * 
     * @param sendOperation The send operation to execute
     * @return true if message was sent successfully
     */
    protected boolean sendMessageWithSimulation(Supplier<Boolean> sendOperation) {
        // This method is for compatibility with AdvancedCouncilMember
        // Delegates to the actual send operation
        return sendOperation.get();
    }
    
    /**
     * Gets the network manager (for AdvancedCouncilMember compatibility)
     * 
     * @return The network manager instance
     */
    protected NetworkManager getNetworkManager() {
        // This would need to expose the networkManager from CouncilMember
        // For now, return null as it's package-private in CouncilMember
        try {
            java.lang.reflect.Field field = CouncilMember.class.getDeclaredField("networkManager");
            field.setAccessible(true);
            return (NetworkManager) field.get(this);
        } catch (Exception e) {
            System.err.println("Could not access network manager: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Broadcasts a message with network simulation
     * 
     * @param message The message to broadcast
     * @return Number of successful sends
     */
    protected int broadcastMessageWithSimulation(PaxosMessage message) {
        NetworkManager networkManager = getNetworkManager();
        if (networkManager != null) {
            return networkManager.broadcastMessage(message);
        }
        return 0;
    }
    
    @Override
    public boolean propose(String candidate) {
        if (!stateManager.startProposal(getCurrentProposalNumber(), candidate)) {
            return false;
        }
        
        boolean result = super.propose(candidate);
        
        if (!result) {
            stateManager.getCurrentInstance().markFailed();
            failedProposals++;
        }
        
        return result;
    }
    
    @Override
    public void handlePromise(PaxosMessage message) {
        super.handlePromise(message);
        
        // Update state manager
        int majoritySize = (getNetworkManager().getKnownMembers().size() / 2) + 1;
        boolean readyForPhase2 = stateManager.handlePromiseResponse(
            message.getSenderId(), 
            message.getAcceptedProposalNumber(),
            message.getAcceptedValue(), 
            majoritySize
        );
        
        if (readyForPhase2) {
            System.out.println(getMemberId() + " transitioning to Phase 2");
        }
    }
    
    @Override
    public void handleAccepted(PaxosMessage message) {
        super.handleAccepted(message);
        
        // Update state manager
        int majoritySize = (getNetworkManager().getKnownMembers().size() / 2) + 1;
        boolean consensusReached = stateManager.handleAcceptedResponse(
            message.getSenderId(), 
            majoritySize
        );
        
        if (consensusReached) {
            successfulProposals++;
            System.out.println(getMemberId() + " achieved consensus through state manager");
        }
    }
    
    @Override
    public void handlePrepare(PaxosMessage message) {
        // Update state manager first
        boolean shouldPromise = stateManager.handlePrepareMessage(message.getProposalNumber());
        
        if (shouldPromise) {
            super.handlePrepare(message);
        } else {
            System.out.println(getMemberId() + " state manager rejected PREPARE from " + 
                             message.getSenderId());
        }
    }
    
    @Override
    public void handleAcceptRequest(PaxosMessage message) {
        // Update state manager first
        boolean shouldAccept = stateManager.handleAcceptRequest(
            message.getProposalNumber(), 
            message.getProposalValue()
        );
        
        if (shouldAccept) {
            super.handleAcceptRequest(message);
        } else {
            System.out.println(getMemberId() + " state manager rejected ACCEPT_REQUEST from " + 
                             message.getSenderId());
        }
    }
    
    @Override
    public void handleLearn(PaxosMessage message) {
        super.handleLearn(message);
        
        // Force learn in state manager
        stateManager.forceLearnConsensus(
            message.getProposalNumber(), 
            message.getProposalValue()
        );
    }
    
    @Override
    public String getState() {
        StringBuilder state = new StringBuilder();
        state.append(super.getState()).append("\n");
        state.append("Enhanced State:\n");
        state.append("  Current Round: ").append(currentRound).append("\n");
        state.append("  Total Proposals: ").append(totalProposals).append("\n");
        state.append("  Successful: ").append(successfulProposals).append("\n");
        state.append("  Failed: ").append(failedProposals).append("\n");
        state.append("  Round Decisions: ").append(roundDecisions).append("\n");
        
        return state.toString();
    }
    
    /**
     * Gets detailed state from the state manager
     * 
     * @return Detailed state information
     */
    public String getDetailedState() {
        return stateManager.getDetailedState();
    }
    
    /**
     * Gets statistics about the member's performance
     * 
     * @return Performance statistics
     */
    public String getStatistics() {
        double successRate = totalProposals > 0 ? 
            (double) successfulProposals / totalProposals * 100 : 0;
        
        return String.format(
            "Statistics for %s:\n" +
            "  Total Proposals: %d\n" +
            "  Successful: %d\n" +
            "  Failed: %d\n" +
            "  Success Rate: %.2f%%\n" +
            "  Rounds Completed: %d",
            getMemberId(), totalProposals, successfulProposals, 
            failedProposals, successRate, roundDecisions.size()
        );
    }
    
    @Override
    public void stop() {
        // Shutdown enhanced components
        enhancedExecutor.shutdown();
        try {
            if (!enhancedExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                enhancedExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            enhancedExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        stateManager.shutdown();
        
        // Stop base member
        super.stop();
        
        System.out.println(getMemberId() + " enhanced member stopped");
    }
    
    /**
     * Main method for running enhanced council member
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java EnhancedCouncilMember <memberId> [--profile <profile>] [--config <configFile>]");
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
                    i++;
                } catch (IllegalArgumentException e) {
                    System.err.println("Unknown profile: " + args[i + 1]);
                    System.exit(1);
                }
            } else if ("--config".equals(args[i]) && i + 1 < args.length) {
                configFile = args[i + 1];
                i++;
            }
        }
        
        // Extract port from member ID
        int memberNumber = Integer.parseInt(memberId.substring(1));
        int port = 9000 + memberNumber;
        
        EnhancedCouncilMember member = new EnhancedCouncilMember(memberId, port, profile);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(member::stop));
        
        try {
            member.start(configFile);
            
            System.out.println(memberId + " enhanced member ready!");
            System.out.println("Commands: <candidate>, status, stats, new_round, quit");
            
            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    String input = scanner.nextLine().trim();
                    
                    if ("quit".equalsIgnoreCase(input)) {
                        break;
                    } else if ("status".equalsIgnoreCase(input)) {
                        System.out.println(member.getState());
                    } else if ("stats".equalsIgnoreCase(input)) {
                        System.out.println(member.getStatistics());
                    } else if ("detailed".equalsIgnoreCase(input)) {
                        System.out.println(member.getDetailedState());
                    } else if ("new_round".equalsIgnoreCase(input)) {
                        member.startNewRound();
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