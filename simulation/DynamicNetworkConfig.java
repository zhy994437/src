import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Dynamic network configuration manager that supports runtime configuration changes,
 * scenario-based testing, and adaptive network behavior simulation
 */
public class DynamicNetworkConfig {
    
    private final Map<String, MemberConfig> memberConfigurations;
    private final Map<String, NetworkBehaviorSimulator> simulators;
    private final ScheduledExecutorService configExecutor;
    private final List<ConfigChangeListener> changeListeners;
    private final Queue<ConfigurationScenario> scheduledScenarios;
    
    // File watching for dynamic reloading
    private final String configFilePath;
    private volatile long lastConfigModified;
    
    /**
     * Configuration for a single member
     */
    public static class MemberConfig {
        private final String memberId;
        private final String hostname;
        private final int port;
        private NetworkBehaviorSimulator.NetworkProfile profile;
        private final Map<String, Object> customProperties;
        
        public MemberConfig(String memberId, String hostname, int port, 
                          NetworkBehaviorSimulator.NetworkProfile profile) {
            this.memberId = memberId;
            this.hostname = hostname;
            this.port = port;
            this.profile = profile;
            this.customProperties = new HashMap<>();
        }
        
        // Getters and setters
        public String getMemberId() { return memberId; }
        public String getHostname() { return hostname; }
        public int getPort() { return port; }
        public NetworkBehaviorSimulator.NetworkProfile getProfile() { return profile; }
        public void setProfile(NetworkBehaviorSimulator.NetworkProfile profile) { this.profile = profile; }
        
        public void setCustomProperty(String key, Object value) {
            customProperties.put(key, value);
        }
        
        public Object getCustomProperty(String key) {
            return customProperties.get(key);
        }
        
        @Override
        public String toString() {
            return String.format("MemberConfig{id=%s, host=%s, port=%d, profile=%s}", 
                memberId, hostname, port, profile);
        }
    }
    
    /**
     * Interface for listening to configuration changes
     */
    public interface ConfigChangeListener {
        /**
         * Called when a member's configuration changes
         * 
         * @param memberId The member whose configuration changed
         * @param oldConfig Previous configuration
         * @param newConfig New configuration
         */
        void onConfigChanged(String memberId, MemberConfig oldConfig, MemberConfig newConfig);
        
        /**
         * Called when a network scenario is activated
         * 
         * @param scenario The scenario being activated
         */
        void onScenarioActivated(ConfigurationScenario scenario);
    }
    
    /**
     * Predefined network scenarios for testing
     */
    public static class ConfigurationScenario {
        private final String name;
        private final String description;
        private final long durationMs;
        private final Map<String, NetworkBehaviorSimulator.NetworkProfile> profileChanges;
        private final List<NetworkAction> actions;
        private final long activationTime;
        
        public static class NetworkAction {
            private final long delayMs;
            private final String targetMember;
            private final ActionType type;
            private final Map<String, Object> parameters;
            
            public enum ActionType {
                CHANGE_PROFILE, SIMULATE_PARTITION, SIMULATE_OFFLINE,
                TEMPORARY_IMPROVEMENT, SIMULATE_CRASH
            }
            
            public NetworkAction(long delayMs, String targetMember, ActionType type) {
                this.delayMs = delayMs;
                this.targetMember = targetMember;
                this.type = type;
                this.parameters = new HashMap<>();
            }
            
            public NetworkAction withParameter(String key, Object value) {
                parameters.put(key, value);
                return this;
            }
            
            // Getters
            public long getDelayMs() { return delayMs; }
            public String getTargetMember() { return targetMember; }
            public ActionType getType() { return type; }
            public Map<String, Object> getParameters() { return parameters; }
        }
        
        public ConfigurationScenario(String name, String description, long durationMs) {
            this.name = name;
            this.description = description;
            this.durationMs = durationMs;
            this.profileChanges = new HashMap<>();
            this.actions = new ArrayList<>();
            this.activationTime = System.currentTimeMillis();
        }
        
        public ConfigurationScenario withProfileChange(String memberId, 
                                                     NetworkBehaviorSimulator.NetworkProfile profile) {
            profileChanges.put(memberId, profile);
            return this;
        }
        
        public ConfigurationScenario withAction(NetworkAction action) {
            actions.add(action);
            return this;
        }
        
        // Getters
        public String getName() { return name; }
        public String getDescription() { return description; }
        public long getDurationMs() { return durationMs; }
        public Map<String, NetworkBehaviorSimulator.NetworkProfile> getProfileChanges() { return profileChanges; }
        public List<NetworkAction> getActions() { return actions; }
        public long getActivationTime() { return activationTime; }
        
        @Override
        public String toString() {
            return String.format("Scenario{name='%s', duration=%dms, changes=%d, actions=%d}", 
                name, durationMs, profileChanges.size(), actions.size());
        }
    }
    
    /**
     * Constructor for DynamicNetworkConfig
     * 
     * @param configFilePath Path to the configuration file
     */
    public DynamicNetworkConfig(String configFilePath) {
        this.configFilePath = configFilePath;
        this.memberConfigurations = new ConcurrentHashMap<>();
        this.simulators = new ConcurrentHashMap<>();
        this.configExecutor = Executors.newScheduledThreadPool(3);
        this.changeListeners = new ArrayList<>();
        this.scheduledScenarios = new ConcurrentLinkedQueue<>();
        this.lastConfigModified = 0;
        
        // Start file monitoring
        startFileMonitoring();
        
        System.out.println("Dynamic network configuration manager initialized");
    }
    
    /**
     * Loads configuration from file
     * 
     * @throws IOException If file reading fails
     */
    public void loadConfiguration() throws IOException {
        File configFile = new File(configFilePath);
        if (!configFile.exists()) {
            createDefaultConfiguration();
            return;
        }
        
        Map<String, MemberConfig> newConfigurations = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                MemberConfig config = parseConfigLine(line);
                if (config != null) {
                    newConfigurations.put(config.getMemberId(), config);
                }
            }
        }
        
        // Update configurations and notify listeners
        updateConfigurations(newConfigurations);
        lastConfigModified = configFile.lastModified();
        
        System.out.println("Loaded " + newConfigurations.size() + " member configurations");
    }
    
    /**
     * Registers a network behavior simulator for a member
     * 
     * @param memberId Member ID
     * @param simulator The simulator instance
     */
    public void registerSimulator(String memberId, NetworkBehaviorSimulator simulator) {
        simulators.put(memberId, simulator);
        System.out.println("Registered network simulator for " + memberId);
    }
    
    /**
     * Changes a member's network profile dynamically
     * 
     * @param memberId Member ID
     * @param newProfile New network profile
     */
    public void changeMemberProfile(String memberId, NetworkBehaviorSimulator.NetworkProfile newProfile) {
        MemberConfig config = memberConfigurations.get(memberId);
        if (config != null) {
            NetworkBehaviorSimulator.NetworkProfile oldProfile = config.getProfile();
            config.setProfile(newProfile);
            
            // Update simulator if registered
            NetworkBehaviorSimulator simulator = simulators.get(memberId);
            if (simulator != null) {
                simulator.changeNetworkProfile(newProfile);
            }
            
            // Notify listeners
            notifyConfigChanged(memberId, config, config);
            
            System.out.println(memberId + " profile changed from " + oldProfile + " to " + newProfile);
        }
    }
    
    /**
     * Activates a predefined configuration scenario
     * 
     * @param scenario The scenario to activate
     */
    public void activateScenario(ConfigurationScenario scenario) {
        System.out.println("Activating scenario: " + scenario.getName());
        System.out.println("Description: " + scenario.getDescription());
        
        // Apply profile changes
        for (Map.Entry<String, NetworkBehaviorSimulator.NetworkProfile> entry : scenario.getProfileChanges().entrySet()) {
            changeMemberProfile(entry.getKey(), entry.getValue());
        }
        
        // Schedule actions
        for (ConfigurationScenario.NetworkAction action : scenario.getActions()) {
            scheduleAction(action);
        }
        
        // Schedule scenario end
        if (scenario.getDurationMs() > 0) {
            configExecutor.schedule(() -> {
                System.out.println("Scenario '" + scenario.getName() + "' ended");
                resetToNormalOperation();
            }, scenario.getDurationMs(), TimeUnit.MILLISECONDS);
        }
        
        // Notify listeners
        for (ConfigChangeListener listener : changeListeners) {
            try {
                listener.onScenarioActivated(scenario);
            } catch (Exception e) {
                System.err.println("Error notifying listener: " + e.getMessage());
            }
        }
    }
    
    /**
     * Creates and activates predefined test scenarios
     */
    public void activateTestScenario(String scenarioName) {
        ConfigurationScenario scenario;
        
        switch (scenarioName.toLowerCase()) {
            case "ideal":
                scenario = createIdealNetworkScenario();
                break;
            case "high_latency":
                scenario = createHighLatencyScenario();
                break;
            case "network_partition":
                scenario = createNetworkPartitionScenario();
                break;
            case "member_failures":
                scenario = createMemberFailuresScenario();
                break;
            case "recovery_test":
                scenario = createRecoveryTestScenario();
                break;
            case "stress_test":
                scenario = createStressTestScenario();
                break;
            default:
                System.err.println("Unknown scenario: " + scenarioName);
                return;
        }
        
        activateScenario(scenario);
    }
    
    /**
     * Adds a configuration change listener
     * 
     * @param listener The listener to add
     */
    public void addChangeListener(ConfigChangeListener listener) {
        changeListeners.add(listener);
    }
    
    /**
     * Removes a configuration change listener
     * 
     * @param listener The listener to remove
     */
    public void removeChangeListener(ConfigChangeListener listener) {
        changeListeners.remove(listener);
    }
    
    /**
     * Gets current configuration for a member
     * 
     * @param memberId Member ID
     * @return Member configuration or null if not found
     */
    public MemberConfig getMemberConfig(String memberId) {
        return memberConfigurations.get(memberId);
    }
    
    /**
     * Gets all member configurations
     * 
     * @return Map of all configurations
     */
    public Map<String, MemberConfig> getAllConfigurations() {
        return new HashMap<>(memberConfigurations);
    }
    
    /**
     * Gets network statistics for all members
     * 
     * @return Formatted statistics string
     */
    public String getNetworkStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("Network Statistics Summary:\n");
        stats.append("=========================\n");
        
        for (Map.Entry<String, NetworkBehaviorSimulator> entry : simulators.entrySet()) {
            String memberId = entry.getKey();
            NetworkBehaviorSimulator simulator = entry.getValue();
            MemberConfig config = memberConfigurations.get(memberId);
            
            stats.append("\n").append(memberId).append(" (").append(config.getProfile()).append("):\n");
            stats.append("  Messages Sent: ").append(simulator.getTotalMessagesSent()).append("\n");
            stats.append("  Messages Lost: ").append(simulator.getTotalMessagesLost()).append("\n");
            stats.append("  Loss Rate: ").append(String.format("%.2f%%", simulator.getMessageLossRate() * 100)).append("\n");
            stats.append("  Current Condition: ").append(simulator.getCurrentCondition()).append("\n");
        }
        
        return stats.toString();
    }
    
    /**
     * Shuts down the configuration manager
     */
    public void shutdown() {
        configExecutor.shutdown();
        try {
            if (!configExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                configExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            configExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Dynamic network configuration manager shut down");
    }
    
    // Private helper methods
    
    private void createDefaultConfiguration() throws IOException {
        System.out.println("Creating default network configuration");
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(configFilePath))) {
            writer.println("# Adelaide Suburbs Council Network Configuration");
            writer.println("# Format: MemberID,Hostname,Port,Profile");
            writer.println("# Profiles: RELIABLE, LATENT, FAILURE, STANDARD");
            writer.println();
            
            String[] profiles = {"RELIABLE", "LATENT", "FAILURE", "STANDARD", "STANDARD", 
                               "STANDARD", "STANDARD", "STANDARD", "STANDARD"};
            
            for (int i = 1; i <= 9; i++) {
                writer.println("M" + i + ",localhost," + (9000 + i) + "," + profiles[i-1]);
            }
        }
        
        loadConfiguration();
    }
    
    private MemberConfig parseConfigLine(String line) {
        String[] parts = line.split(",");
        if (parts.length < 3) {
            System.err.println("Invalid config line: " + line);
            return null;
        }
        
        try {
            String memberId = parts[0].trim();
            String hostname = parts[1].trim();
            int port = Integer.parseInt(parts[2].trim());
            
            NetworkBehaviorSimulator.NetworkProfile profile = NetworkBehaviorSimulator.NetworkProfile.STANDARD;
            if (parts.length > 3) {
                try {
                    profile = NetworkBehaviorSimulator.NetworkProfile.valueOf(parts[3].trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    System.err.println("Invalid profile in config: " + parts[3] + ", using STANDARD");
                }
            }
            
            return new MemberConfig(memberId, hostname, port, profile);
            
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number in config line: " + line);
            return null;
        }
    }
    
    private void updateConfigurations(Map<String, MemberConfig> newConfigurations) {
        // Check for changes and notify listeners
        for (Map.Entry<String, MemberConfig> entry : newConfigurations.entrySet()) {
            String memberId = entry.getKey();
            MemberConfig newConfig = entry.getValue();
            MemberConfig oldConfig = memberConfigurations.get(memberId);
            
            if (oldConfig == null || !oldConfig.getProfile().equals(newConfig.getProfile())) {
                notifyConfigChanged(memberId, oldConfig, newConfig);
                
                // Update simulator if registered
                NetworkBehaviorSimulator simulator = simulators.get(memberId);
                if (simulator != null) {
                    simulator.changeNetworkProfile(newConfig.getProfile());
                }
            }
        }
        
        memberConfigurations.clear();
        memberConfigurations.putAll(newConfigurations);
    }
    
    private void notifyConfigChanged(String memberId, MemberConfig oldConfig, MemberConfig newConfig) {
        for (ConfigChangeListener listener : changeListeners) {
            try {
                listener.onConfigChanged(memberId, oldConfig, newConfig);
            } catch (Exception e) {
                System.err.println("Error notifying config change listener: " + e.getMessage());
            }
        }
    }
    
    private void scheduleAction(ConfigurationScenario.NetworkAction action) {
        configExecutor.schedule(() -> {
            executeNetworkAction(action);
        }, action.getDelayMs(), TimeUnit.MILLISECONDS);
    }
    
    private void executeNetworkAction(ConfigurationScenario.NetworkAction action) {
        NetworkBehaviorSimulator simulator = simulators.get(action.getTargetMember());
        if (simulator == null) {
            System.err.println("No simulator found for member: " + action.getTargetMember());
            return;
        }
        
        switch (action.getType()) {
            case CHANGE_PROFILE:
                NetworkBehaviorSimulator.NetworkProfile profile = 
                    (NetworkBehaviorSimulator.NetworkProfile) action.getParameters().get("profile");
                if (profile != null) {
                    changeMemberProfile(action.getTargetMember(), profile);
                }
                break;
                
            case SIMULATE_PARTITION:
                @SuppressWarnings("unchecked")
                Set<String> peers = (Set<String>) action.getParameters().get("peers");
                Long duration = (Long) action.getParameters().get("duration");
                if (peers != null && duration != null) {
                    simulator.simulatePartition(peers, duration);
                }
                break;
                
            case SIMULATE_OFFLINE:
                Long offlineDuration = (Long) action.getParameters().get("duration");
                if (offlineDuration != null) {
                    simulator.simulateOffline(offlineDuration);
                }
                break;
                
            case SIMULATE_CRASH:
                // Simulate member crash by going offline for extended period
                simulator.simulateOffline(5000 + new Random().nextInt(10000));
                System.out.println(action.getTargetMember() + " simulating crash!");
                break;
        }
    }
    
    private void resetToNormalOperation() {
        System.out.println("Resetting all members to normal operation");
        
        for (Map.Entry<String, MemberConfig> entry : memberConfigurations.entrySet()) {
            String memberId = entry.getKey();
            NetworkBehaviorSimulator simulator = simulators.get(memberId);
            
            if (simulator != null) {
                // Reset to configured profile
                simulator.changeNetworkProfile(entry.getValue().getProfile());
            }
        }
    }
    
    private void startFileMonitoring() {
        // Monitor configuration file for changes
        configExecutor.scheduleAtFixedRate(() -> {
            try {
                File configFile = new File(configFilePath);
                if (configFile.exists() && configFile.lastModified() > lastConfigModified) {
                    System.out.println("Configuration file changed, reloading...");
                    loadConfiguration();
                }
            } catch (IOException e) {
                System.err.println("Error reloading configuration: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS);
    }
    
    // Predefined scenario creators
    
    private ConfigurationScenario createIdealNetworkScenario() {
        return new ConfigurationScenario("Ideal Network", 
            "All members have reliable connections", 30000)
            .withProfileChange("M1", NetworkBehaviorSimulator.NetworkProfile.RELIABLE)
            .withProfileChange("M2", NetworkBehaviorSimulator.NetworkProfile.RELIABLE)
            .withProfileChange("M3", NetworkBehaviorSimulator.NetworkProfile.RELIABLE)
            .withProfileChange("M4", NetworkBehaviorSimulator.NetworkProfile.RELIABLE)
            .withProfileChange("M5", NetworkBehaviorSimulator.NetworkProfile.RELIABLE)
            .withProfileChange("M6", NetworkBehaviorSimulator.NetworkProfile.RELIABLE)
            .withProfileChange("M7", NetworkBehaviorSimulator.NetworkProfile.RELIABLE)
            .withProfileChange("M8", NetworkBehaviorSimulator.NetworkProfile.RELIABLE)
            .withProfileChange("M9", NetworkBehaviorSimulator.NetworkProfile.RELIABLE);
    }
    
    private ConfigurationScenario createHighLatencyScenario() {
        return new ConfigurationScenario("High Latency", 
            "All members experience high latency", 45000)
            .withProfileChange("M1", NetworkBehaviorSimulator.NetworkProfile.LATENT)
            .withProfileChange("M2", NetworkBehaviorSimulator.NetworkProfile.LATENT)
            .withProfileChange("M3", NetworkBehaviorSimulator.NetworkProfile.LATENT)
            .withProfileChange("M4", NetworkBehaviorSimulator.NetworkProfile.LATENT)
            .withProfileChange("M5", NetworkBehaviorSimulator.NetworkProfile.LATENT)
            .withProfileChange("M6", NetworkBehaviorSimulator.NetworkProfile.LATENT)
            .withProfileChange("M7", NetworkBehaviorSimulator.NetworkProfile.LATENT)
            .withProfileChange("M8", NetworkBehaviorSimulator.NetworkProfile.LATENT)
            .withProfileChange("M9", NetworkBehaviorSimulator.NetworkProfile.LATENT);
    }
    
    private ConfigurationScenario createNetworkPartitionScenario() {
        ConfigurationScenario scenario = new ConfigurationScenario("Network Partition", 
            "Members split into two partitions", 60000);
        
        // Partition M1, M2, M3, M4 from M5, M6, M7, M8, M9
        Set<String> partition1 = new HashSet<>(Arrays.asList("M5", "M6", "M7", "M8", "M9"));
        Set<String> partition2 = new HashSet<>(Arrays.asList("M1", "M2", "M3", "M4"));
        
        scenario.withAction(new ConfigurationScenario.NetworkAction(1000, "M1", 
            ConfigurationScenario.NetworkAction.ActionType.SIMULATE_PARTITION)
            .withParameter("peers", partition1)
            .withParameter("duration", 30000L));
            
        scenario.withAction(new ConfigurationScenario.NetworkAction(1000, "M5", 
            ConfigurationScenario.NetworkAction.ActionType.SIMULATE_PARTITION)
            .withParameter("peers", partition2)
            .withParameter("duration", 30000L));
        
        return scenario;
    }
    
    private ConfigurationScenario createMemberFailuresScenario() {
        return new ConfigurationScenario("Member Failures", 
            "Random member failures and recoveries", 90000)
            .withProfileChange("M3", NetworkBehaviorSimulator.NetworkProfile.FAILURE)
            .withProfileChange("M7", NetworkBehaviorSimulator.NetworkProfile.FAILURE)
            .withAction(new ConfigurationScenario.NetworkAction(5000, "M3", 
                ConfigurationScenario.NetworkAction.ActionType.SIMULATE_CRASH))
            .withAction(new ConfigurationScenario.NetworkAction(15000, "M7", 
                ConfigurationScenario.NetworkAction.ActionType.SIMULATE_OFFLINE)
                .withParameter("duration", 10000L))
            .withAction(new ConfigurationScenario.NetworkAction(30000, "M2", 
                ConfigurationScenario.NetworkAction.ActionType.SIMULATE_OFFLINE)
                .withParameter("duration", 8000L));
    }
    
    private ConfigurationScenario createRecoveryTestScenario() {
        return new ConfigurationScenario("Recovery Test", 
            "Test recovery from various failure conditions", 120000)
            .withProfileChange("M1", NetworkBehaviorSimulator.NetworkProfile.FAILURE)
            .withProfileChange("M3", NetworkBehaviorSimulator.NetworkProfile.FAILURE)
            .withProfileChange("M5", NetworkBehaviorSimulator.NetworkProfile.LATENT)
            .withAction(new ConfigurationScenario.NetworkAction(10000, "M1", 
                ConfigurationScenario.NetworkAction.ActionType.CHANGE_PROFILE)
                .withParameter("profile", NetworkBehaviorSimulator.NetworkProfile.STANDARD))
            .withAction(new ConfigurationScenario.NetworkAction(20000, "M3", 
                ConfigurationScenario.NetworkAction.ActionType.CHANGE_PROFILE)
                .withParameter("profile", NetworkBehaviorSimulator.NetworkProfile.RELIABLE))
            .withAction(new ConfigurationScenario.NetworkAction(30000, "M5", 
                ConfigurationScenario.NetworkAction.ActionType.CHANGE_PROFILE)
                .withParameter("profile", NetworkBehaviorSimulator.NetworkProfile.STANDARD));
    }
    
    private ConfigurationScenario createStressTestScenario() {
        ConfigurationScenario scenario = new ConfigurationScenario("Stress Test", 
            "High-stress conditions with multiple failures", 180000);
        
        // Gradual degradation
        scenario.withAction(new ConfigurationScenario.NetworkAction(5000, "M2", 
            ConfigurationScenario.NetworkAction.ActionType.CHANGE_PROFILE)
            .withParameter("profile", NetworkBehaviorSimulator.NetworkProfile.LATENT));
            
        scenario.withAction(new ConfigurationScenario.NetworkAction(10000, "M3", 
            ConfigurationScenario.NetworkAction.ActionType.CHANGE_PROFILE)
            .withParameter("profile", NetworkBehaviorSimulator.NetworkProfile.FAILURE));
            
        scenario.withAction(new ConfigurationScenario.NetworkAction(20000, "M7", 
            ConfigurationScenario.NetworkAction.ActionType.SIMULATE_CRASH));
            
        scenario.withAction(new ConfigurationScenario.NetworkAction(30000, "M9", 
            ConfigurationScenario.NetworkAction.ActionType.SIMULATE_OFFLINE)
            .withParameter("duration", 15000L));
        
        return scenario;
    }
}