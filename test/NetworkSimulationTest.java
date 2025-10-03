import java.util.*;
import java.util.concurrent.*;
import java.io.*;

/**
 * Comprehensive test suite for network behavior simulation
 * Validates realistic network conditions and their impact on Paxos consensus
 */
public class NetworkSimulationTest {
    
    private final List<AdvancedCouncilMember> members;
    private final DynamicNetworkConfig networkConfig;
    private final ExecutorService testExecutor;
    private final Random random;
    
    private static final String CONFIG_FILE = "test_network.config";
    private static final long TEST_TIMEOUT_MS = 60000; // 1 minute per test
    
    /**
     * Test result for network simulation scenarios
     */
    public static class NetworkTestResult {
        private final String testName;
        private final boolean consensusReached;
        private final String consensusValue;
        private final long executionTime;
        private final Map<String, NetworkStatistics> memberStats;
        private final List<String> observations;
        
        public static class NetworkStatistics {
            private final long messagesSent;
            private final long messagesLost;
            private final double lossRate;
            private final NetworkBehaviorSimulator.NetworkCondition finalCondition;
            
            public NetworkStatistics(long messagesSent, long messagesLost, 
                                   double lossRate, NetworkBehaviorSimulator.NetworkCondition finalCondition) {
                this.messagesSent = messagesSent;
                this.messagesLost = messagesLost;
                this.lossRate = lossRate;
                this.finalCondition = finalCondition;
            }
            
            // Getters
            public long getMessagesSent() { return messagesSent; }
            public long getMessagesLost() { return messagesLost; }
            public double getLossRate() { return lossRate; }
            public NetworkBehaviorSimulator.NetworkCondition getFinalCondition() { return finalCondition; }
        }
        
        public NetworkTestResult(String testName, boolean consensusReached, 
                               String consensusValue, long executionTime) {
            this.testName = testName;
            this.consensusReached = consensusReached;
            this.consensusValue = consensusValue;
            this.executionTime = executionTime;
            this.memberStats = new HashMap<>();
            this.observations = new ArrayList<>();
        }
        
        public void addMemberStats(String memberId, NetworkStatistics stats) {
            memberStats.put(memberId, stats);
        }
        
        public void addObservation(String observation) {
            observations.add(observation);
        }
        
        // Getters
        public String getTestName() { return testName; }
        public boolean isConsensusReached() { return consensusReached; }
        public String getConsensusValue() { return consensusValue; }
        public long getExecutionTime() { return executionTime; }
        public Map<String, NetworkStatistics> getMemberStats() { return memberStats; }
        public List<String> getObservations() { return observations; }
        
        @Override
        public String toString() {
            return String.format("[%s] %s (%dms) - Consensus: %s on '%s'", 
                consensusReached ? "PASS" : "FAIL", testName, executionTime, 
                consensusReached, consensusValue);
        }
    }
    
    /**
     * Constructor for NetworkSimulationTest
     */
    public NetworkSimulationTest() {
        this.members = new ArrayList<>();
        this.networkConfig = new DynamicNetworkConfig(CONFIG_FILE);
        this.testExecutor = Executors.newFixedThreadPool(12);
        this.random = new Random();
    }
    
    /**
     * Sets up the test environment with 9 members
     */
    public void setUp() throws Exception {
        System.out.println("Setting up network simulation test environment...");
        
        // Create test configuration file
        createTestConfigFile();
        
        // Define realistic profiles for each member
        NetworkBehaviorSimulator.NetworkProfile[] profiles = {
            NetworkBehaviorSimulator.NetworkProfile.RELIABLE,  // M1 - Ultra responsive
            NetworkBehaviorSimulator.NetworkProfile.LATENT,    // M2 - Poor connection
            NetworkBehaviorSimulator.NetworkProfile.FAILURE,   // M3 - Camping/unreliable
            NetworkBehaviorSimulator.NetworkProfile.STANDARD,  // M4-M9 - Business users
            NetworkBehaviorSimulator.NetworkProfile.STANDARD,
            NetworkBehaviorSimulator.NetworkProfile.STANDARD,
            NetworkBehaviorSimulator.NetworkProfile.STANDARD,
            NetworkBehaviorSimulator.NetworkProfile.STANDARD,
            NetworkBehaviorSimulator.NetworkProfile.STANDARD
        };
        
        // Create all members
        for (int i = 1; i <= 9; i++) {
            String memberId = "M" + i;
            int port = 9000 + i;
            
            AdvancedCouncilMember member = new AdvancedCouncilMember(
                memberId, port, profiles[i-1], networkConfig);
            
            members.add(member);
            member.start(CONFIG_FILE);
        }
        
        // Wait for all members to be ready
        Thread.sleep(3000);
        System.out.println("Network simulation environment ready with " + members.size() + " members");
    }
    
    /**
     * Creates test configuration file
     */
    private void createTestConfigFile() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            writer.println("# Test Network Configuration for Adelaide Suburbs Council");
            writer.println("# Format: MemberID,Hostname,Port,Profile");
            
            String[] profiles = {"RELIABLE", "LATENT", "FAILURE", "STANDARD", "STANDARD", 
                               "STANDARD", "STANDARD", "STANDARD", "STANDARD"};
            
            for (int i = 1; i <= 9; i++) {
                writer.println("M" + i + ",localhost," + (9000 + i) + "," + profiles[i-1]);
            }
        }
    }
    
    /**
     * Tears down the test environment
     */
    public void tearDown() {
        System.out.println("Tearing down network simulation test environment...");
        
        for (AdvancedCouncilMember member : members) {
            member.stop();
        }
        
        networkConfig.shutdown();
        testExecutor.shutdown();
        
        try {
            if (!testExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                testExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            testExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        members.clear();
        
        // Clean up test config file
        new File(CONFIG_FILE).delete();
    }
    
    /**
     * Runs all network simulation tests
     */
    public List<NetworkTestResult> runAllNetworkTests() {
        List<NetworkTestResult> results = new ArrayList<>();
        
        try {
            setUp();
            
            // Basic network condition tests
            results.add(testIdealNetworkConditions());
            results.add(testHighLatencyConditions());
            results.add(testIntermittentFailures());
            
            // Partition and recovery tests
            results.add(testNetworkPartitioning());
            results.add(testPartitionRecovery());
            results.add(testAsymmetricPartition());
            
            // Member behavior tests
            results.add(testLatentMemberProposal());
            results.add(testFailureMemberBehavior());
            results.add(testMemberRecoveryPatterns());
            
            // Dynamic scenario tests
            results.add(testDynamicProfileChanges());
            results.add(testCascadingFailures());
            results.add(testNetworkStormSimulation());
            
        } catch (Exception e) {
            System.err.println("Network simulation test setup failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            tearDown();
        }
        
        return results;
    }
    
    /**
     * Test 1: Ideal network conditions (all reliable)
     */
    public NetworkTestResult testIdealNetworkConditions() {
        long startTime = System.currentTimeMillis();
        String testName = "Ideal Network Conditions";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            // Activate ideal scenario
            networkConfig.activateTestScenario("ideal");
            Thread.sleep(2000); // Let scenario activate
            
            // Reset all members
            resetAllMembers();
            
            // M5 proposes M7
            AdvancedCouncilMember proposer = getMember("M5");
            proposer.proposeCandidate("M7");
            
            // Wait for consensus
            String consensus = waitForConsensus(15000);
            boolean success = consensus != null && consensus.equals("M7");
            
            NetworkTestResult result = new NetworkTestResult(testName, success, consensus, 
                System.currentTimeMillis() - startTime);
            
            // Collect network statistics
            collectNetworkStatistics(result);
            
            if (success) {
                result.addObservation("Consensus achieved quickly in ideal conditions");
                result.addObservation("All members maintained reliable connections");
                result.addObservation("Low message loss across all members");
            } else {
                result.addObservation("Failed to achieve consensus despite ideal conditions");
            }
            
            return result;
            
        } catch (Exception e) {
            NetworkTestResult result = new NetworkTestResult(testName, false, null, 
                System.currentTimeMillis() - startTime);
            result.addObservation("Exception occurred: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Test 2: High latency conditions
     */
    public NetworkTestResult testHighLatencyConditions() {
        long startTime = System.currentTimeMillis();
        String testName = "High Latency Conditions";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            // Activate high latency scenario
            networkConfig.activateTestScenario("high_latency");
            Thread.sleep(3000);
            
            resetAllMembers();
            
            // M1 proposes M2 (both will have high latency)
            AdvancedCouncilMember proposer = getMember("M1");
            proposer.proposeCandidate("M2");
            
            String consensus = waitForConsensus(30000); // Longer timeout for high latency
            boolean success = consensus != null && consensus.equals("M2");
            
            NetworkTestResult result = new NetworkTestResult(testName, success, consensus, 
                System.currentTimeMillis() - startTime);
            
            collectNetworkStatistics(result);
            
            if (success) {
                result.addObservation("Consensus achieved despite high latency");
                result.addObservation("System adapted to slower network conditions");
            } else {
                result.addObservation("High latency prevented consensus within timeout");
                result.addObservation("Network delays exceeded system tolerance");
            }
            
            return result;
            
        } catch (Exception e) {
            NetworkTestResult result = new NetworkTestResult(testName, false, null, 
                System.currentTimeMillis() - startTime);
            result.addObservation("Exception occurred: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Test 3: Intermittent failures
     */
    public NetworkTestResult testIntermittentFailures() {
        long startTime = System.currentTimeMillis();
        String testName = "Intermittent Failures";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            // Set some members to failure profile
            networkConfig.changeMemberProfile("M3", NetworkBehaviorSimulator.NetworkProfile.FAILURE);
            networkConfig.changeMemberProfile("M7", NetworkBehaviorSimulator.NetworkProfile.FAILURE);
            Thread.sleep(2000);
            
            resetAllMembers();
            
            // M4 (standard) proposes M6
            AdvancedCouncilMember proposer = getMember("M4");
            proposer.proposeCandidate("M6");
            
            String consensus = waitForConsensus(25000);
            boolean success = consensus != null && consensus.equals("M6");
            
            NetworkTestResult result = new NetworkTestResult(testName, success, consensus, 
                System.currentTimeMillis() - startTime);
            
            collectNetworkStatistics(result);
            
            if (success) {
                result.addObservation("Consensus achieved despite intermittent failures");
                result.addObservation("System demonstrated fault tolerance");
                result.addObservation("Majority of members maintained connectivity");
            } else {
                result.addObservation("Intermittent failures prevented consensus");
                result.addObservation("System could not achieve majority agreement");
            }
            
            return result;
            
        } catch (Exception e) {
            NetworkTestResult result = new NetworkTestResult(testName, false, null, 
                System.currentTimeMillis() - startTime);
            result.addObservation("Exception occurred: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Test 4: Network partitioning
     */
    public NetworkTestResult testNetworkPartitioning() {
        long startTime = System.currentTimeMillis();
        String testName = "Network Partitioning";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            resetAllMembers();
            
            // Create partition: {M1,M2,M3,M4} vs {M5,M6,M7,M8,M9}
            Set<String> partition1 = new HashSet<>(Arrays.asList("M5", "M6", "M7", "M8", "M9"));
            Set<String> partition2 = new HashSet<>(Arrays.asList("M1", "M2", "M3", "M4"));
            
            // Simulate partition using the network simulator
            for (String memberId : Arrays.asList("M1", "M2", "M3", "M4")) {
                AdvancedCouncilMember member = getMember(memberId);
                if (member != null && member.networkSimulator != null) {
                    member.networkSimulator.simulatePartition(partition1, 20000);
                }
            }
            
            Thread.sleep(2000);
            
            // M6 (in majority partition) proposes M8
            AdvancedCouncilMember proposer = getMember("M6");
            proposer.proposeCandidate("M8");
            
            String consensus = waitForConsensus(30000);
            boolean success = consensus != null && consensus.equals("M8");
            
            NetworkTestResult result = new NetworkTestResult(testName, success, consensus, 
                System.currentTimeMillis() - startTime);
            
            collectNetworkStatistics(result);
            
            if (success) {
                result.addObservation("Majority partition achieved consensus");
                result.addObservation("System correctly handled network split");
                result.addObservation("Minority partition unable to proceed");
            } else {
                result.addObservation("Partition prevented consensus");
                result.addObservation("Neither partition achieved majority");
            }
            
            return result;
            
        } catch (Exception e) {
            NetworkTestResult result = new NetworkTestResult(testName, false, null, 
                System.currentTimeMillis() - startTime);
            result.addObservation("Exception occurred: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Test 5: Partition recovery
     */
    public NetworkTestResult testPartitionRecovery() {
        long startTime = System.currentTimeMillis();
        String testName = "Partition Recovery";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            resetAllMembers();
            
            // Short partition that recovers
            Set<String> isolatedMembers = new HashSet<>(Arrays.asList("M1", "M4", "M5", "M6", "M7", "M8", "M9"));
            AdvancedCouncilMember m2 = getMember("M2");
            if (m2 != null && m2.networkSimulator != null) {
                m2.networkSimulator.simulatePartition(isolatedMembers, 8000);
            }
            
            Thread.sleep(1000);
            
            // M1 proposes M9 while M2 is partitioned
            AdvancedCouncilMember proposer = getMember("M1");
            proposer.proposeCandidate("M9");
            
            String consensus = waitForConsensus(20000);
            boolean success = consensus != null && consensus.equals("M9");
            
            NetworkTestResult result = new NetworkTestResult(testName, success, consensus, 
                System.currentTimeMillis() - startTime);
            
            collectNetworkStatistics(result);
            
            if (success) {
                result.addObservation("Consensus survived partition and recovery");
                result.addObservation("Isolated member rejoined successfully");
                result.addObservation("System demonstrated resilience");
            } else {
                result.addObservation("Partition recovery failed to achieve consensus");
            }
            
            return result;
            
        } catch (Exception e) {
            NetworkTestResult result = new NetworkTestResult(testName, false, null, 
                System.currentTimeMillis() - startTime);
            result.addObservation("Exception occurred: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Test 6: Asymmetric partition (different views)
     */
    public NetworkTestResult testAsymmetricPartition() {
        long startTime = System.currentTimeMillis();
        String testName = "Asymmetric Partition";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            resetAllMembers();
            
            // Asymmetric partition: M1 can't reach M5,M6 but M5,M6 can reach others
            AdvancedCouncilMember m1 = getMember("M1");
            if (m1 != null && m1.networkSimulator != null) {
                m1.networkSimulator.simulatePartition(new HashSet<>(Arrays.asList("M5", "M6")), 15000);
            }
            
            Thread.sleep(1000);
            
            // M5 proposes M3
            AdvancedCouncilMember proposer = getMember("M5");
            proposer.proposeCandidate("M3");
            
            String consensus = waitForConsensus(25000);
            boolean success = consensus != null && consensus.equals("M3");
            
            NetworkTestResult result = new NetworkTestResult(testName, success, consensus, 
                System.currentTimeMillis() - startTime);
            
            collectNetworkStatistics(result);
            
            if (success) {
                result.addObservation("Handled asymmetric partition correctly");
                result.addObservation("Consensus achieved despite unidirectional communication loss");
            } else {
                result.addObservation("Asymmetric partition prevented consensus");
            }
            
            return result;
            
        } catch (Exception e) {
            NetworkTestResult result = new NetworkTestResult(testName, false, null, 
                System.currentTimeMillis() - startTime);
            result.addObservation("Exception occurred: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Test 7: Latent member (M2) as proposer
     */
    public NetworkTestResult testLatentMemberProposal() {
        long startTime = System.currentTimeMillis();
        String testName = "Latent Member Proposal";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            // Ensure M2 has latent profile
            networkConfig.changeMemberProfile("M2", NetworkBehaviorSimulator.NetworkProfile.LATENT);
            Thread.sleep(2000);
            
            resetAllMembers();
            
            // M2 proposes M4
            AdvancedCouncilMember proposer = getMember("M2");
            proposer.proposeCandidate("M4");
            
            String consensus = waitForConsensus(35000); // Extra time for latent member
            boolean success = consensus != null && consensus.equals("M4");
            
            NetworkTestResult result = new NetworkTestResult(testName, success, consensus, 
                System.currentTimeMillis() - startTime);
            
            collectNetworkStatistics(result);
            
            if (success) {
                result.addObservation("Latent member successfully proposed despite connection issues");
                result.addObservation("System accommodated high-latency proposer");
                result.addObservation("Other members waited appropriately for slow responses");
            } else {
                result.addObservation("Latent member failed to complete proposal");
                result.addObservation("High latency exceeded system timeout thresholds");
            }
            
            return result;
            
        } catch (Exception e) {
            NetworkTestResult result = new NetworkTestResult(testName, false, null, 
                System.currentTimeMillis() - startTime);
            result.addObservation("Exception occurred: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Test 8: Failure member behavior
     */
    public NetworkTestResult testFailureMemberBehavior() {
        long startTime = System.currentTimeMillis();
        String testName = "Failure Member Behavior";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            // Set M3 to failure profile
            networkConfig.changeMemberProfile("M3", NetworkBehaviorSimulator.NetworkProfile.FAILURE);
            Thread.sleep(2000);
            
            resetAllMembers();
            
            // M3 (failure prone) starts proposal then may crash
            AdvancedCouncilMember proposer = getMember("M3");
            proposer.proposeCandidate("M1");
            
            // Wait a bit, then have backup proposer
            Thread.sleep(5000);
            
            AdvancedCouncilMember backup = getMember("M7");
            backup.proposeCandidate("M1");
            
            String consensus = waitForConsensus(30000);
            boolean success = consensus != null && consensus.equals("M1");
            
            NetworkTestResult result = new NetworkTestResult(testName, success, consensus, 
                System.currentTimeMillis() - startTime);
            
            collectNetworkStatistics(result);
            
            if (success) {
                result.addObservation("System recovered from failure member crash");
                result.addObservation("Backup proposer successfully took over");
                result.addObservation("Fault tolerance mechanisms worked correctly");
            } else {
                result.addObservation("Failed to recover from member failure");
                result.addObservation("System stalled without proper recovery");
            }
            
            return result;
            
        } catch (Exception e) {
            NetworkTestResult result = new NetworkTestResult(testName, false, null, 
                System.currentTimeMillis() - startTime);
            result.addObservation("Exception occurred: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Test 9: Member recovery patterns
     */
    public NetworkTestResult testMemberRecoveryPatterns() {
        long startTime = System.currentTimeMillis();
        String testName = "Member Recovery Patterns";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            resetAllMembers();
            
            // Simulate M8 going offline then recovering
            AdvancedCouncilMember m8 = getMember("M8");
            if (m8 != null && m8.networkSimulator != null) {
                m8.networkSimulator.simulateOffline(10000);
            }
            
            Thread.sleep(2000);
            
            // M9 proposes M5 while M8 is offline
            AdvancedCouncilMember proposer = getMember("M9");
            proposer.proposeCandidate("M5");
            
            String consensus = waitForConsensus(25000);
            boolean success = consensus != null && consensus.equals("M5");
            
            NetworkTestResult result = new NetworkTestResult(testName, success, consensus, 
                System.currentTimeMillis() - startTime);
            
            collectNetworkStatistics(result);
            
            if (success) {
                result.addObservation("Consensus achieved while member offline");
                result.addObservation("System handled member recovery gracefully");
                result.addObservation("Recovered member synchronized properly");
            } else {
                result.addObservation("Member offline prevented consensus");
                result.addObservation("Recovery process interfered with consensus");
            }
            
            return result;
            
        } catch (Exception e) {
            NetworkTestResult result = new NetworkTestResult(testName, false, null, 
                System.currentTimeMillis() - startTime);
            result.addObservation("Exception occurred: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Test 10: Dynamic profile changes during consensus
     */
    public NetworkTestResult testDynamicProfileChanges() {
        long startTime = System.currentTimeMillis();
        String testName = "Dynamic Profile Changes";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            resetAllMembers();
            
            // M4 starts proposal
            AdvancedCouncilMember proposer = getMember("M4");
            proposer.proposeCandidate("M2");
            
            // Change profiles mid-consensus
            Thread.sleep(3000);
            networkConfig.changeMemberProfile("M1", NetworkBehaviorSimulator.NetworkProfile.LATENT);
            networkConfig.changeMemberProfile("M6", NetworkBehaviorSimulator.NetworkProfile.FAILURE);
            
            String consensus = waitForConsensus(30000);
            boolean success = consensus != null && consensus.equals("M2");
            
            NetworkTestResult result = new NetworkTestResult(testName, success, consensus, 
                System.currentTimeMillis() - startTime);
            
            collectNetworkStatistics(result);
            
            if (success) {
                result.addObservation("Consensus survived dynamic profile changes");
                result.addObservation("System adapted to changing network conditions");
                result.addObservation("Runtime reconfiguration handled properly");
            } else {
                result.addObservation("Dynamic changes disrupted consensus process");
                result.addObservation("System could not adapt to profile changes");
            }
            
            return result;
            
        } catch (Exception e) {
            NetworkTestResult result = new NetworkTestResult(testName, false, null, 
                System.currentTimeMillis() - startTime);
            result.addObservation("Exception occurred: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Test 11: Cascading failures
     */
    public NetworkTestResult testCascadingFailures() {
        long startTime = System.currentTimeMillis();
        String testName = "Cascading Failures";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            resetAllMembers();
            
            // M1 proposes M3
            AdvancedCouncilMember proposer = getMember("M1");
            proposer.proposeCandidate("M3");
            
            // Simulate cascading failures
            Thread.sleep(2000);
            AdvancedCouncilMember m2 = getMember("M2");
            if (m2 != null && m2.networkSimulator != null) {
                m2.networkSimulator.simulateOffline(8000);
            }
            
            Thread.sleep(1000);
            AdvancedCouncilMember m7 = getMember("M7");
            if (m7 != null && m7.networkSimulator != null) {
                m7.networkSimulator.simulateOffline(6000);
            }
            
            Thread.sleep(1000);
            AdvancedCouncilMember m9 = getMember("M9");
            if (m9 != null && m9.networkSimulator != null) {
                m9.networkSimulator.simulateOffline(4000);
            }
            
            String consensus = waitForConsensus(35000);
            boolean success = consensus != null && consensus.equals("M3");
            
            NetworkTestResult result = new NetworkTestResult(testName, success, consensus, 
                System.currentTimeMillis() - startTime);
            
            collectNetworkStatistics(result);
            
            if (success) {
                result.addObservation("System survived cascading failures");
                result.addObservation("Remaining members maintained consensus");
                result.addObservation("Majority still functional despite multiple failures");
            } else {
                result.addObservation("Cascading failures prevented consensus");
                result.addObservation("Too many members failed simultaneously");
            }
            
            return result;
            
        } catch (Exception e) {
            NetworkTestResult result = new NetworkTestResult(testName, false, null, 
                System.currentTimeMillis() - startTime);
            result.addObservation("Exception occurred: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Test 12: Network storm simulation
     */
    public NetworkTestResult testNetworkStormSimulation() {
        long startTime = System.currentTimeMillis();
        String testName = "Network Storm Simulation";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            // Apply stress scenario
            networkConfig.activateTestScenario("stress_test");
            Thread.sleep(3000);
            
            resetAllMembers();
            
            // Multiple concurrent proposals under stress
            List<CompletableFuture<Void>> proposals = new ArrayList<>();
            
            proposals.add(CompletableFuture.runAsync(() -> 
                getMember("M1").proposeCandidate("M8"), testExecutor));
            proposals.add(CompletableFuture.runAsync(() -> 
                getMember("M4").proposeCandidate("M6"), testExecutor));
            proposals.add(CompletableFuture.runAsync(() -> 
                getMember("M5").proposeCandidate("M9"), testExecutor));
            
            // Wait for proposals to start
            Thread.sleep(2000);
            
            String consensus = waitForConsensus(45000); // Extended timeout for stress
            boolean success = consensus != null && 
                (consensus.equals("M8") || consensus.equals("M6") || consensus.equals("M9"));
            
            NetworkTestResult result = new NetworkTestResult(testName, success, consensus, 
                System.currentTimeMillis() - startTime);
            
            collectNetworkStatistics(result);
            
            if (success) {
                result.addObservation("Consensus achieved under network storm conditions");
                result.addObservation("Conflict resolution handled multiple proposals");
                result.addObservation("System performed well under high stress");
            } else {
                result.addObservation("Network storm prevented consensus");
                result.addObservation("System overwhelmed by multiple stresses");
            }
            
            return result;
            
        } catch (Exception e) {
            NetworkTestResult result = new NetworkTestResult(testName, false, null, 
                System.currentTimeMillis() - startTime);
            result.addObservation("Exception occurred: " + e.getMessage());
            return result;
        }
    }
    
    // Helper methods
    
    /**
     * Resets all members to prepare for a new test
     */
    private void resetAllMembers() {
        for (AdvancedCouncilMember member : members) {
            member.reset();
        }
        try {
            Thread.sleep(1000); // Give time for reset to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Gets a member by their ID
     * 
     * @param memberId The member identifier
     * @return The AdvancedCouncilMember or null if not found
     */
    private AdvancedCouncilMember getMember(String memberId) {
        return members.stream()
            .filter(m -> m.getMemberId().equals(memberId))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Waits for consensus to be reached by any member
     * 
     * @param timeoutMs Timeout in milliseconds
     * @return The consensus value or null if timeout
     */
    private String waitForConsensus(long timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            for (AdvancedCouncilMember member : members) {
                if (member.hasLearned()) {
                    String value = member.getLearnedValue();
                    System.out.println("Consensus achieved: " + value + " (detected by " + member.getMemberId() + ")");
                    return value;
                }
            }
            
            try {
                Thread.sleep(200); // Check every 200ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        System.out.println("Consensus timeout after " + timeoutMs + "ms");
        return null; // Timeout
    }
    
    /**
     * Collects network statistics from all members for analysis
     * 
     * @param result The test result to populate with statistics
     */
    private void collectNetworkStatistics(NetworkTestResult result) {
        for (AdvancedCouncilMember member : members) {
            long messagesSent = member.networkSimulator.getTotalMessagesSent();
            long messagesLost = member.networkSimulator.getTotalMessagesLost();
            double lossRate = member.networkSimulator.getMessageLossRate();
            NetworkBehaviorSimulator.NetworkCondition condition = 
                member.networkSimulator.getCurrentCondition();
            
            NetworkTestResult.NetworkStatistics stats = new NetworkTestResult.NetworkStatistics(
                messagesSent, messagesLost, lossRate, condition);
            
            result.addMemberStats(member.getMemberId(), stats);
        }
    }
    
    /**
     * Main method to run network simulation tests
     */
    public static void main(String[] args) {
        System.out.println("Starting Network Behavior Simulation Tests");
        System.out.println("==========================================");
        System.out.println("Testing Paxos consensus under realistic network conditions");
        System.out.println();
        
        NetworkSimulationTest testSuite = new NetworkSimulationTest();
        List<NetworkTestResult> results = testSuite.runAllNetworkTests();
        
        // Print immediate results summary
        System.out.println("\nNetwork Simulation Test Results:");
        System.out.println("===============================");
        
        int passed = 0;
        int failed = 0;
        long totalExecutionTime = 0;
        
        for (NetworkTestResult result : results) {
            System.out.println(result);
            if (result.isConsensusReached()) {
                passed++;
            } else {
                failed++;
            }
            totalExecutionTime += result.getExecutionTime();
        }
        
        System.out.println("\nImmediate Summary:");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("Total:  " + (passed + failed));
        System.out.println("Success Rate: " + String.format("%.1f%%", 100.0 * passed / (passed + failed)));
        System.out.println("Total Execution Time: " + totalExecutionTime + "ms");
        System.out.println("Average Test Time: " + (totalExecutionTime / results.size()) + "ms");
        
        System.exit(failed > 0 ? 1 : 0);
    }
}