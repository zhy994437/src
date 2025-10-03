import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Advanced Council Member with integrated network behavior simulation,
 * dynamic configuration support, and comprehensive monitoring capabilities
 */
public class AdvancedCouncilMember extends EnhancedCouncilMember 
    implements DynamicNetworkConfig.ConfigChangeListener {
    
    // Network simulation components
    public final NetworkBehaviorSimulator networkSimulator;
    private final DynamicNetworkConfig networkConfig;
    private final ConflictResolver conflictResolver;
    
    // Monitoring and statistics
    private final Map<String, Long> messageTimestamps;
    private final Map<String, Integer> messageSizes;
    private final ExecutorService monitoringExecutor;
    
    // Command processing
    private final Queue<String> commandQueue;
    private final ScheduledExecutorService commandProcessor;
    
    /**
     * Constructor for AdvancedCouncilMember
     * 
     * @param memberId Member identifier
     * @param port Network port
     * @param profile Initial network profile
     * @param configManager Dynamic configuration manager
     */
    public AdvancedCouncilMember(String memberId, int port, 
                               NetworkBehaviorSimulator.NetworkProfile profile,
                               DynamicNetworkConfig configManager) {
        super(memberId, port, convertProfile(profile));
        
        this.networkSimulator = new NetworkBehaviorSimulator(memberId, profile);
        this.networkConfig = configManager;
        this.conflictResolver = new ConflictResolver(memberId);
        
        this.messageTimestamps = new ConcurrentHashMap<>();
        this.messageSizes = new ConcurrentHashMap<>();
        this.monitoringExecutor = Executors.newSingleThreadExecutor();
        
        this.commandQueue = new ConcurrentLinkedQueue<>();
        this.commandProcessor = Executors.newSingleThreadScheduledExecutor();
        
        // Register with configuration manager
        networkConfig.addChangeListener(this);
        networkConfig.registerSimulator(memberId, networkSimulator);
        
        // Start command processing
        startCommandProcessing();
        startMonitoring();
        
        System.out.println(memberId + " advanced council member initialized");
    }
    
    @Override
    public void start(String configFile) throws IOException {
        // Load dynamic configuration
        networkConfig.loadConfiguration();
        
        // Start the base member
        super.start(configFile);
        
        System.out.println(getMemberId() + " advanced member started with dynamic configuration");
    }
    
    @Override
    public void stop() {
        // Stop all components
        networkSimulator.shutdown();
        monitoringExecutor.shutdown();
        commandProcessor.shutdown();
        
        try {
            if (!monitoringExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                monitoringExecutor.shutdownNow();
            }
            if (!commandProcessor.awaitTermination(3, TimeUnit.SECONDS)) {
                commandProcessor.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitoringExecutor.shutdownNow();
            commandProcessor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Stop base member
        super.stop();
        
        System.out.println(getMemberId() + " advanced member fully stopped");
    }
    
    /**
     * Enhanced proposal method with conflict resolution and network simulation
     */
    @Override
    public boolean propose(String candidate) {
        if (hasLearned()) {
            System.out.println(getMemberId() + " cannot propose - consensus already reached");
            return false;
        }
        
        // Register proposal with conflict resolver
        String proposalNumber = conflictResolver.generateConflictAwareProposalNumber(
            getCurrentProposalCounter());
        ConflictResolver.ProposalAttempt attempt = conflictResolver.registerProposal(
            proposalNumber, candidate);
        
        // Check for conflicts
        List<ConflictResolver.ProposalAttempt> conflicts = conflictResolver.detectConflicts(attempt);
        if (!conflicts.isEmpty()) {
            ConflictResolver.ConflictAction action = conflictResolver.resolveConflicts(attempt, conflicts);
            
            switch (action) {
                case BACKOFF:
                    long delay = conflictResolver.calculateBackoffDelay();
                    System.out.println(getMemberId() + " backing off due to conflicts, retry in " + delay + "ms");
                    commandProcessor.schedule(() -> propose(candidate), delay, TimeUnit.MILLISECONDS);
                    return false;
                    
                case YIELD:
                    System.out.println(getMemberId() + " yielding to higher priority proposals");
                    return false;
                    
                case ABORT:
                    System.out.println(getMemberId() + " aborting proposal due to conflicts");
                    return false;
                    
                default:
                    // Continue with proposal
                    break;
            }
        }
        
        return super.propose(candidate);
    }
    
    /**
     * Enhanced message sending with network behavior simulation
     */
    protected boolean sendMessageWithSimulation(String targetMember, PaxosMessage message) {
        // Calculate message size (rough estimate)
        int messageSize = estimateMessageSize(message);
        messageSizes.put(message.serialize(), messageSize);
        messageTimestamps.put(message.serialize(), System.currentTimeMillis());
        
        // Simulate network behavior
        NetworkBehaviorSimulator.MessageDeliveryResult result = 
            networkSimulator.simulateMessageSend(targetMember, messageSize);
        
        if (!result.isDelivered()) {
            System.out.println(getMemberId() + " message to " + targetMember + 
                             " failed: " + result.getReason());
            return false;
        }
        
        // Apply simulated delay
        if (result.getLatencyMs() > 0) {
            try {
                Thread.sleep(result.getLatencyMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        // Actually send the message using parent implementation
        return super.sendMessageWithSimulation(() -> 
            getNetworkManager().sendMessage(targetMember, message));
    }
    
    /**
     * Enhanced broadcast with network simulation
     */
    protected int broadcastMessageWithSimulation(PaxosMessage message) {
        int successCount = 0;
        Set<String> knownMembers = getNetworkManager().getKnownMembers();
        
        for (String member : knownMembers) {
            if (!member.equals(getMemberId())) {
                if (sendMessageWithSimulation(member, message)) {
                    successCount++;
                }
            }
        }
        
        return successCount;
    }
    
    // Configuration change listener implementation
    
    @Override
    public void onConfigChanged(String memberId, DynamicNetworkConfig.MemberConfig oldConfig, 
                              DynamicNetworkConfig.MemberConfig newConfig) {
        if (memberId.equals(getMemberId())) {
            System.out.println(getMemberId() + " configuration changed: " + 
                             (oldConfig != null ? oldConfig.getProfile() : "null") + 
                             " -> " + newConfig.getProfile());
            
            // Update network profile
            setNetworkProfile(convertProfile(newConfig.getProfile()));
        }
    }
    
    @Override
    public void onScenarioActivated(DynamicNetworkConfig.ConfigurationScenario scenario) {
        System.out.println(getMemberId() + " participating in scenario: " + scenario.getName());
    }
    
    /**
     * Processes interactive commands
     * 
     * @param command The command to process
     */
    public void processCommand(String command) {
        commandQueue.offer(command);
    }
    
    /**
     * Gets comprehensive member statistics
     * 
     * @return Detailed statistics string
     */
    @Override
    public String getState() {
        StringBuilder state = new StringBuilder();
        
        // Base Paxos state
        state.append("=== ").append(getMemberId()).append(" Advanced State ===\n");
        state.append(super.getState()).append("\n\n");
        
        // Network simulation statistics
        state.append("Network Simulation:\n");
        state.append(networkSimulator.getNetworkStatistics()).append("\n\n");
        
        // Conflict resolution statistics  
        state.append("Conflict Resolution:\n");
        state.append(conflictResolver.getConflictStatistics()).append("\n\n");
        
        // Recent network events
        state.append("Recent Network Events:\n");
        List<NetworkBehaviorSimulator.NetworkEvent> recentEvents = 
            networkSimulator.getRecentEvents(5);
        for (NetworkBehaviorSimulator.NetworkEvent event : recentEvents) {
            state.append("  ").append(event).append("\n");
        }
        
        return state.toString();
    }
    
    /**
     * Gets network performance metrics
     * 
     * @return Performance metrics
     */
    public String getPerformanceMetrics() {
        long totalMessages = networkSimulator.getTotalMessagesSent();
        long lostMessages = networkSimulator.getTotalMessagesLost();
        double lossRate = totalMessages > 0 ? (double) lostMessages / totalMessages * 100 : 0;
        
        return String.format(
            "Performance Metrics for %s:\n" +
            "  Total Messages: %d\n" +
            "  Lost Messages: %d (%.2f%%)\n" +
            "  Current Profile: %s\n" +
            "  Current Condition: %s\n" +
            "  Paxos Phase: %s\n" +
            "  Has Learned: %s",
            getMemberId(), totalMessages, lostMessages, lossRate,
            networkSimulator.getCurrentProfile(),
            networkSimulator.getCurrentCondition(),
            getCurrentPaxosPhase(),
            hasLearned()
        );
    }
    
    /**
     * Triggers network scenario testing
     * 
     * @param scenarioName Name of the scenario to activate
     */
    public void activateNetworkScenario(String scenarioName) {
        networkConfig.activateTestScenario(scenarioName);
    }
    
    // Private helper methods
    
    private void startCommandProcessing() {
        commandProcessor.scheduleAtFixedRate(() -> {
            String command = commandQueue.poll();
            if (command != null) {
                executeCommand(command);
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }
    
    private void startMonitoring() {
        monitoringExecutor.execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Periodic monitoring tasks
                    Thread.sleep(5000);
                    
                    // Log periodic statistics
                    if (networkSimulator.getTotalMessagesSent() > 0) {
                        System.out.println(getMemberId() + " - " + getPerformanceMetrics());
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
    
    private void executeCommand(String command) {
        String[] parts = command.split("\\s+");
        String action = parts[0].toLowerCase();
        
        try {
            switch (action) {
                case "propose":
                    if (parts.length > 1) {
                        proposeCandidate(parts[1]);
                    }
                    break;
                    
                case "profile":
                    if (parts.length > 1) {
                        try {
                            NetworkBehaviorSimulator.NetworkProfile profile = 
                                NetworkBehaviorSimulator.NetworkProfile.valueOf(parts[1].toUpperCase());
                            networkConfig.changeMemberProfile(getMemberId(), profile);
                        } catch (IllegalArgumentException e) {
                            System.err.println("Invalid profile: " + parts[1]);
                        }
                    }
                    break;
                    
                case "scenario":
                    if (parts.length > 1) {
                        activateNetworkScenario(parts[1]);
                    }
                    break;
                    
                case "partition":
                    if (parts.length > 2) {
                        Set<String> peers = new HashSet<>(Arrays.asList(parts).subList(2, parts.length));
                        long duration = Long.parseLong(parts[1]) * 1000; // Convert to milliseconds
                        networkSimulator.simulatePartition(peers, duration);
                    }
                    break;
                    
                case "offline":
                    if (parts.length > 1) {
                        long duration = Long.parseLong(parts[1]) * 1000;
                        networkSimulator.simulateOffline(duration);
                    }
                    break;
                    
                case "stats":
                    System.out.println(getState());
                    break;
                    
                case "metrics":
                    System.out.println(getPerformanceMetrics());
                    break;
                    
                case "reset":
                    reset();
                    conflictResolver.resetStrategies();
                    break;
                    
                case "events":
                    int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
                    List<NetworkBehaviorSimulator.NetworkEvent> events = 
                        networkSimulator.getRecentEvents(count);
                    System.out.println("Recent Network Events:");
                    events.forEach(System.out::println);
                    break;
                    
                default:
                    System.err.println("Unknown command: " + action);
            }
        } catch (Exception e) {
            System.err.println("Error executing command '" + command + "': " + e.getMessage());
        }
    }
    
    private int estimateMessageSize(PaxosMessage message) {
        // Rough estimate based on message content
        String serialized = message.serialize();
        return serialized.length() + 50; // Base overhead
    }
    
    private String getCurrentPaxosPhase() {
        if (isActivelyProposing()) {
            return getCurrentProposalNumber() != null ? "PROPOSING" : "PREPARING";
        } else if (hasLearned()) {
            return "DECIDED";
        } else {
            return "IDLE";
        }
    }
    
    private int getCurrentProposalCounter() {
        // Extract counter from current proposal number if exists
        String currentProposal = getCurrentProposalNumber();
        if (currentProposal != null) {
            try {
                return Integer.parseInt(currentProposal.split("\\.")[0]);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
    
    private static EnhancedCouncilMember.NetworkProfile convertProfile(
            NetworkBehaviorSimulator.NetworkProfile simProfile) {
        switch (simProfile) {
            case RELIABLE: return EnhancedCouncilMember.NetworkProfile.RELIABLE;
            case LATENT: return EnhancedCouncilMember.NetworkProfile.LATENT;
            case FAILURE: return EnhancedCouncilMember.NetworkProfile.FAILURE;
            case STANDARD: return EnhancedCouncilMember.NetworkProfile.STANDARD;
            default: return EnhancedCouncilMember.NetworkProfile.STANDARD;
        }
    }
    
    // Method to access NetworkManager (assuming it exists in parent)
    @Override
    protected NetworkManager getNetworkManager() {
       // This would need to be implemented based on the parent class structure
       // For now, delegate to parent implementation
       return super.getNetworkManager();
     }
    
    /**
     * Enhanced main method with advanced command-line options
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        String memberId = args[0];
        NetworkBehaviorSimulator.NetworkProfile profile = NetworkBehaviorSimulator.NetworkProfile.STANDARD;
        String configFile = "network.config";
        Integer portOverride = null;
        String scenario = null;
        boolean interactiveMode = false;
        
        // Enhanced argument parsing
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--profile":
                    if (i + 1 < args.length) {
                        try {
                            profile = NetworkBehaviorSimulator.NetworkProfile.valueOf(
                                args[++i].toUpperCase());
                        } catch (IllegalArgumentException e) {
                            System.err.println("Unknown profile: " + args[i]);
                            System.exit(1);
                        }
                    }
                    break;
                case "--config":
                    if (i + 1 < args.length) {
                        configFile = args[++i];
                    }
                    break;
                case "--port":
                    if (i + 1 < args.length) {
                        try {
                            portOverride = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid port number: " + args[i]);
                            System.exit(1);
                        }
                    }
                    break;
                case "--scenario":
                    if (i + 1 < args.length) {
                        scenario = args[++i];
                    }
                    break;
                case "--interactive":
                    interactiveMode = true;
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }
        
        // Determine port
        int port;
        if (portOverride != null) {
            port = portOverride;
        } else {
            try {
                int memberNumber = Integer.parseInt(memberId.substring(1));
                port = 9000 + memberNumber;
            } catch (Exception e) {
                System.err.println("Invalid member ID format: " + memberId);
                System.exit(1);
                return;
            }
        }
        
        // Create dynamic configuration manager
        DynamicNetworkConfig configManager = new DynamicNetworkConfig(configFile);
        
        // Create advanced member
        AdvancedCouncilMember member = new AdvancedCouncilMember(memberId, port, profile, configManager);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            member.stop();
            configManager.shutdown();
        }));
        
        try {
            member.start(configFile);
            
            // Activate initial scenario if specified
            if (scenario != null) {
                System.out.println("Activating initial scenario: " + scenario);
                member.activateNetworkScenario(scenario);
            }
            
            if (interactiveMode) {
                runInteractiveMode(member);
            } else {
                runCommandLineMode(member);
            }
            
        } catch (IOException e) {
            System.err.println("Failed to start " + memberId + ": " + e.getMessage());
            System.exit(1);
        } finally {
            member.stop();
            configManager.shutdown();
        }
    }
    
    private static void printUsage() {
        System.out.println("Usage: java AdvancedCouncilMember <memberId> [options]");
        System.out.println("Options:");
        System.out.println("  --profile <profile>    Network profile (reliable, latent, failure, standard)");
        System.out.println("  --config <file>        Configuration file (default: network.config)");
        System.out.println("  --port <port>          Override port number");
        System.out.println("  --scenario <scenario>  Initial scenario (ideal, high_latency, network_partition, etc.)");
        System.out.println("  --interactive          Enable interactive command mode");
        System.out.println();
        System.out.println("Available scenarios:");
        System.out.println("  ideal, high_latency, network_partition, member_failures,");
        System.out.println("  recovery_test, stress_test");
    }
    
    private static void runInteractiveMode(AdvancedCouncilMember member) {
        System.out.println("\n" + member.getMemberId() + " Advanced Interactive Mode");
        System.out.println("=======================================");
        printCommands();
        
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print(member.getMemberId() + "> ");
                String input = scanner.nextLine().trim();
                
                if ("quit".equalsIgnoreCase(input) || "exit".equalsIgnoreCase(input)) {
                    break;
                } else if ("help".equalsIgnoreCase(input)) {
                    printCommands();
                } else if (!input.isEmpty()) {
                    member.processCommand(input);
                }
            }
        }
    }
    
    private static void runCommandLineMode(AdvancedCouncilMember member) {
        System.out.println("\n" + member.getMemberId() + " ready for advanced election!");
        System.out.println("Commands: <candidate>, status, stats, metrics, scenario <name>, quit");
        
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                String input = scanner.nextLine().trim();
                
                if ("quit".equalsIgnoreCase(input)) {
                    break;
                } else if ("status".equalsIgnoreCase(input)) {
                    System.out.println(member.getState());
                } else if ("stats".equalsIgnoreCase(input)) {
                    System.out.println(member.networkConfig.getNetworkStatistics());
                } else if ("metrics".equalsIgnoreCase(input)) {
                    System.out.println(member.getPerformanceMetrics());
                } else if (input.startsWith("scenario ")) {
                    String scenarioName = input.substring(9);
                    member.activateNetworkScenario(scenarioName);
                } else if (!input.isEmpty()) {
                    member.proposeCandidate(input);
                }
            }
        }
    }
    
    private static void printCommands() {
        System.out.println("Available Commands:");
        System.out.println("  propose <candidate>     Propose a candidate for president");
        System.out.println("  profile <profile>       Change network profile (reliable, latent, failure, standard)");
        System.out.println("  scenario <name>         Activate network scenario");
        System.out.println("  partition <duration> <peers>  Simulate network partition");
        System.out.println("  offline <duration>      Go offline for specified seconds");
        System.out.println("  stats                   Show detailed state information");
        System.out.println("  metrics                 Show performance metrics");
        System.out.println("  events [count]          Show recent network events");
        System.out.println("  reset                   Reset member state");
        System.out.println("  help                    Show this help message");
        System.out.println("  quit/exit               Exit the program");
        System.out.println();
        System.out.println("Available Scenarios:");
        System.out.println("  ideal, high_latency, network_partition, member_failures,");
        System.out.println("  recovery_test, stress_test");
    }
}