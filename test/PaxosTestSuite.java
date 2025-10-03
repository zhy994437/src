import java.util.*;
import java.util.concurrent.*;
import java.io.*;

/**
 * Comprehensive test suite for validating Paxos algorithm implementation
 * Tests various scenarios including concurrent proposals, failures, and edge cases
 */
public class PaxosTestSuite {
    
    private final List<EnhancedCouncilMember> members;
    private final ExecutorService testExecutor;
    private final Random random;
    
    // Test configuration
    private static final int TOTAL_MEMBERS = 9;
    private static final long TEST_TIMEOUT_MS = 30000; // 30 seconds per test
    private static final String CONFIG_FILE = "network.config";
    
    /**
     * Test result container
     */
    public static class TestResult {
        private final String testName;
        private final boolean success;
        private final String details;
        private final long executionTime;
        private final Map<String, String> memberStates;
        
        public TestResult(String testName, boolean success, String details, long executionTime) {
            this.testName = testName;
            this.success = success;
            this.details = details;
            this.executionTime = executionTime;
            this.memberStates = new HashMap<>();
        }
        
        public void addMemberState(String memberId, String state) {
            memberStates.put(memberId, state);
        }
        
        // Getters
        public String getTestName() { return testName; }
        public boolean isSuccess() { return success; }
        public String getDetails() { return details; }
        public long getExecutionTime() { return executionTime; }
        public Map<String, String> getMemberStates() { return memberStates; }
        
        @Override
        public String toString() {
            return String.format("[%s] %s (%dms) - %s", 
                success ? "PASS" : "FAIL", testName, executionTime, details);
        }
    }
    
    /**
     * Constructor for PaxosTestSuite
     */
    public PaxosTestSuite() {
        this.members = new ArrayList<>();
        this.testExecutor = Executors.newFixedThreadPool(TOTAL_MEMBERS + 2);
        this.random = new Random();
    }
    
    /**
     * Sets up test environment by creating and starting all members
     * 
     * @throws IOException If setup fails
     */
    public void setUp() throws IOException {
        System.out.println("Setting up Paxos test environment...");
        
        // Create network configuration
        createTestNetworkConfig();
        
        // Create all members with different profiles
        EnhancedCouncilMember.NetworkProfile[] profiles = {
            EnhancedCouncilMember.NetworkProfile.RELIABLE,  // M1
            EnhancedCouncilMember.NetworkProfile.LATENT,    // M2
            EnhancedCouncilMember.NetworkProfile.FAILURE,   // M3
            EnhancedCouncilMember.NetworkProfile.STANDARD,  // M4
            EnhancedCouncilMember.NetworkProfile.STANDARD,  // M5
            EnhancedCouncilMember.NetworkProfile.STANDARD,  // M6
            EnhancedCouncilMember.NetworkProfile.STANDARD,  // M7
            EnhancedCouncilMember.NetworkProfile.STANDARD,  // M8
            EnhancedCouncilMember.NetworkProfile.STANDARD   // M9
        };
        
        for (int i = 1; i <= TOTAL_MEMBERS; i++) {
            String memberId = "M" + i;
            int port = 9000 + i;
            EnhancedCouncilMember member = new EnhancedCouncilMember(
                memberId, port, profiles[i-1]);
            
            members.add(member);
            member.start(CONFIG_FILE);
        }
        
        // Wait for all members to be ready
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Setup interrupted while waiting for members: " + e.getMessage());
        }
        
        System.out.println("Test environment ready with " + members.size() + " members");
    }
    
    /**
     * Tears down test environment
     */
    public void tearDown() {
        System.out.println("Tearing down test environment...");
        
        for (EnhancedCouncilMember member : members) {
            member.stop();
        }
        
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
        
        // Clean up config file
        new File(CONFIG_FILE).delete();
    }
    
    /**
     * Runs all test scenarios
     * 
     * @return List of test results
     */
    public List<TestResult> runAllTests() {
        List<TestResult> results = new ArrayList<>();
        
        try {
            setUp();
            
            // Basic functionality tests
            results.add(testSingleProposal());
            results.add(testSequentialProposals());
            
            // Concurrency tests
            results.add(testConcurrentProposals());
            results.add(testMultipleConcurrentProposals());
            
            // Failure tolerance tests
            results.add(testProposalWithFailures());
            results.add(testLatentMemberProposal());
            results.add(testFailureMemberRecovery());
            
            // Edge case tests
            results.add(testConflictResolution());
            results.add(testConsensusConvergence());
            
        } catch (Exception e) {
            System.err.println("Test setup failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            tearDown();
        }
        
        return results;
    }
    
    /**
     * Test 1: Single proposal in ideal conditions
     */
    public TestResult testSingleProposal() {
        long startTime = System.currentTimeMillis();
        String testName = "Single Proposal Test";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            // Reset all members
            resetAllMembers();
            
            // M4 proposes M5 for president
            EnhancedCouncilMember proposer = getMember("M4");
            proposer.proposeCandidate("M5");
            
            // Wait for consensus
            String consensusValue = waitForConsensus(10000);
            
            if (consensusValue != null && consensusValue.equals("M5")) {
                // Verify all members learned the same value
                boolean allAgree = verifyConsensus("M5");
                
                long executionTime = System.currentTimeMillis() - startTime;
                TestResult result = new TestResult(testName, allAgree, 
                    allAgree ? "Consensus achieved on M5" : "Members disagree on result", 
                    executionTime);
                
                // Capture member states
                for (EnhancedCouncilMember member : members) {
                    result.addMemberState(member.getMemberId(), member.getState());
                }
                
                return result;
            } else {
                return new TestResult(testName, false, "No consensus achieved", 
                    System.currentTimeMillis() - startTime);
            }
            
        } catch (Exception e) {
            return new TestResult(testName, false, "Exception: " + e.getMessage(), 
                System.currentTimeMillis() - startTime);
        }
    }
    
    /**
     * Test 2: Sequential proposals (one after another)
     */
    public TestResult testSequentialProposals() {
        long startTime = System.currentTimeMillis();
        String testName = "Sequential Proposals Test";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            resetAllMembers();
            
            // First proposal: M1 proposes M3
            EnhancedCouncilMember proposer1 = getMember("M1");
            proposer1.proposeCandidate("M3");
            
            String firstConsensus = waitForConsensus(10000);
            if (firstConsensus == null || !firstConsensus.equals("M3")) {
                return new TestResult(testName, false, "First consensus failed", 
                    System.currentTimeMillis() - startTime);
            }
            
            // Start new round
            for (EnhancedCouncilMember member : members) {
                member.startNewRound();
            }
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Second proposal: M6 proposes M8
            EnhancedCouncilMember proposer2 = getMember("M6");
            proposer2.proposeCandidate("M8");
            
            String secondConsensus = waitForConsensus(10000);
            boolean success = secondConsensus != null && secondConsensus.equals("M8");
            
            return new TestResult(testName, success, 
                success ? "Both sequential consensuses achieved" : "Second consensus failed: " + secondConsensus, 
                System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            return new TestResult(testName, false, "Exception: " + e.getMessage(), 
                System.currentTimeMillis() - startTime);
        }
    }
    
    /**
     * Test 3: Concurrent proposals from two members
     */
    public TestResult testConcurrentProposals() {
        long startTime = System.currentTimeMillis();
        String testName = "Concurrent Proposals Test";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            resetAllMembers();
            
            // Concurrent proposals: M1 proposes M1, M8 proposes M8
            CompletableFuture<Void> proposal1 = CompletableFuture.runAsync(() -> 
                getMember("M1").proposeCandidate("M1"), testExecutor);
            
            CompletableFuture<Void> proposal2 = CompletableFuture.runAsync(() -> 
                getMember("M8").proposeCandidate("M8"), testExecutor);
            
            // Wait for both proposals to start
            CompletableFuture.allOf(proposal1, proposal2).get(2, TimeUnit.SECONDS);
            
            // Wait for consensus
            String consensusValue = waitForConsensus(15000);
            
            if (consensusValue != null) {
                boolean validResult = consensusValue.equals("M1") || consensusValue.equals("M8");
                boolean allAgree = verifyConsensus(consensusValue);
                
                boolean success = validResult && allAgree;
                return new TestResult(testName, success, 
                    success ? "Consensus achieved on " + consensusValue : "Invalid consensus: " + consensusValue, 
                    System.currentTimeMillis() - startTime);
            } else {
                return new TestResult(testName, false, "No consensus achieved", 
                    System.currentTimeMillis() - startTime);
            }
            
        } catch (Exception e) {
            return new TestResult(testName, false, "Exception: " + e.getMessage(), 
                System.currentTimeMillis() - startTime);
        }
    }
    
    /**
     * Test 4: Multiple concurrent proposals (stress test)
     */
    public TestResult testMultipleConcurrentProposals() {
        long startTime = System.currentTimeMillis();
        String testName = "Multiple Concurrent Proposals Test";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            resetAllMembers();
            
            // Multiple members propose simultaneously
            List<CompletableFuture<Void>> proposals = new ArrayList<>();
            String[] candidates = {"M1", "M3", "M5", "M7", "M9"};
            
            for (int i = 0; i < candidates.length; i++) {
                final String candidate = candidates[i];
                final String proposerId = "M" + (i * 2 + 1); // M1, M3, M5, M7, M9
                
                proposals.add(CompletableFuture.runAsync(() -> 
                    getMember(proposerId).proposeCandidate(candidate), testExecutor));
            }
            
            // Wait for all proposals to start
            CompletableFuture.allOf(proposals.toArray(new CompletableFuture[0]))
                .get(5, TimeUnit.SECONDS);
            
            // Wait for consensus
            String consensusValue = waitForConsensus(20000);
            
            if (consensusValue != null) {
                boolean validCandidate = Arrays.asList(candidates).contains(consensusValue);
                boolean allAgree = verifyConsensus(consensusValue);
                
                boolean success = validCandidate && allAgree;
                return new TestResult(testName, success, 
                    success ? "Consensus achieved on " + consensusValue + " from multiple proposals" : 
                    "Invalid consensus from multiple proposals", 
                    System.currentTimeMillis() - startTime);
            } else {
                return new TestResult(testName, false, "No consensus from multiple concurrent proposals", 
                    System.currentTimeMillis() - startTime);
            }
            
        } catch (Exception e) {
            return new TestResult(testName, false, "Exception: " + e.getMessage(), 
                System.currentTimeMillis() - startTime);
        }
    }
    
    /**
     * Test 5: Proposal with simulated failures
     */
    public TestResult testProposalWithFailures() {
        long startTime = System.currentTimeMillis();
        String testName = "Proposal With Failures Test";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            resetAllMembers();
            
            // M4 (standard) proposes M6 while M3 (failure profile) may fail
            EnhancedCouncilMember proposer = getMember("M4");
            proposer.proposeCandidate("M6");
            
            // Wait longer due to potential failures
            String consensusValue = waitForConsensus(20000);
            
            if (consensusValue != null && consensusValue.equals("M6")) {
                // Check that majority still agrees despite failures
                int agreeingMembers = countAgreeingMembers("M6");
                boolean success = agreeingMembers >= 5; // Majority of 9
                
                return new TestResult(testName, success, 
                    success ? "Consensus achieved despite failures (" + agreeingMembers + "/9 agree)" : 
                    "Insufficient agreement despite failures", 
                    System.currentTimeMillis() - startTime);
            } else {
                return new TestResult(testName, false, "No consensus achieved with failures", 
                    System.currentTimeMillis() - startTime);
            }
            
        } catch (Exception e) {
            return new TestResult(testName, false, "Exception: " + e.getMessage(), 
                System.currentTimeMillis() - startTime);
        }
    }
    
    /**
     * Test 6: Latent member (M2) initiates proposal
     */
    public TestResult testLatentMemberProposal() {
        long startTime = System.currentTimeMillis();
        String testName = "Latent Member Proposal Test";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            resetAllMembers();
            
            // M2 (latent profile) proposes M7
            EnhancedCouncilMember latentProposer = getMember("M2");
            latentProposer.proposeCandidate("M7");
            
            // Wait longer for latent member
            String consensusValue = waitForConsensus(25000);
            
            if (consensusValue != null && consensusValue.equals("M7")) {
                boolean allAgree = verifyConsensus("M7");
                
                return new TestResult(testName, allAgree, 
                    allAgree ? "Consensus achieved despite latent proposer" : 
                    "Agreement failed with latent proposer", 
                    System.currentTimeMillis() - startTime);
            } else {
                return new TestResult(testName, false, "No consensus from latent proposer", 
                    System.currentTimeMillis() - startTime);
            }
            
        } catch (Exception e) {
            return new TestResult(testName, false, "Exception: " + e.getMessage(), 
                System.currentTimeMillis() - startTime);
        }
    }
    
    /**
     * Test 7: Failure member recovery
     */
    public TestResult testFailureMemberRecovery() {
        long startTime = System.currentTimeMillis();
        String testName = "Failure Member Recovery Test";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            resetAllMembers();
            
            // M3 (failure profile) starts proposal but may crash
            EnhancedCouncilMember failureMember = getMember("M3");
            failureMember.proposeCandidate("M4");
            
            // Wait a bit, then have another member take over
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            EnhancedCouncilMember backupProposer = getMember("M5");
            backupProposer.proposeCandidate("M4");
            
            String consensusValue = waitForConsensus(20000);
            
            if (consensusValue != null && consensusValue.equals("M4")) {
                boolean success = verifyConsensus("M4");
                return new TestResult(testName, success, 
                    success ? "System recovered from failure member crash" : 
                    "Recovery failed after member crash", 
                    System.currentTimeMillis() - startTime);
            } else {
                return new TestResult(testName, false, "No recovery after member failure", 
                    System.currentTimeMillis() - startTime);
            }
            
        } catch (Exception e) {
            return new TestResult(testName, false, "Exception: " + e.getMessage(), 
                System.currentTimeMillis() - startTime);
        }
    }
    
    /**
     * Test 8: Conflict resolution mechanisms
     */
    public TestResult testConflictResolution() {
        long startTime = System.currentTimeMillis();
        String testName = "Conflict Resolution Test";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            resetAllMembers();
            
            // Rapid-fire proposals to test conflict resolution
            List<CompletableFuture<Void>> rapidProposals = new ArrayList<>();
            
            for (int i = 1; i <= 4; i++) {
                final String memberId = "M" + i;
                final String candidate = "M" + (i + 4);
                
                rapidProposals.add(CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(random.nextInt(100)); // Small random delay
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    getMember(memberId).proposeCandidate(candidate);
                }, testExecutor));
            }
            
            // Wait for all proposals
            CompletableFuture.allOf(rapidProposals.toArray(new CompletableFuture[0]))
                .get(5, TimeUnit.SECONDS);
            
            String consensusValue = waitForConsensus(25000);
            
            if (consensusValue != null) {
                boolean validCandidate = Arrays.asList("M5", "M6", "M7", "M8").contains(consensusValue);
                boolean allAgree = verifyConsensus(consensusValue);
                
                boolean success = validCandidate && allAgree;
                return new TestResult(testName, success, 
                    success ? "Conflict resolution successful, consensus on " + consensusValue : 
                    "Conflict resolution failed", 
                    System.currentTimeMillis() - startTime);
            } else {
                return new TestResult(testName, false, "No consensus despite conflict resolution", 
                    System.currentTimeMillis() - startTime);
            }
            
        } catch (Exception e) {
            return new TestResult(testName, false, "Exception: " + e.getMessage(), 
                System.currentTimeMillis() - startTime);
        }
    }
    
    /**
     * Test 9: Consensus convergence under stress
     */
    public TestResult testConsensusConvergence() {
        long startTime = System.currentTimeMillis();
        String testName = "Consensus Convergence Test";
        
        try {
            System.out.println("\n=== " + testName + " ===");
            
            resetAllMembers();
            
            // Multiple rounds of proposals with different candidates
            String[] rounds = {"M1", "M2", "M3"};
            List<String> results = new ArrayList<>();
            
            for (String candidate : rounds) {
                EnhancedCouncilMember proposer = getMember("M" + (random.nextInt(9) + 1));
                proposer.proposeCandidate(candidate);
                
                String result = waitForConsensus(10000);
                if (result != null) {
                    results.add(result);
                    
                    // Start new round
                    for (EnhancedCouncilMember member : members) {
                        member.startNewRound();
                    }
                    
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            
            boolean success = results.size() == rounds.length;
            return new TestResult(testName, success, 
                success ? "All consensus rounds successful: " + results : 
                "Some consensus rounds failed: " + results, 
                System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            return new TestResult(testName, false, "Exception: " + e.getMessage(), 
                System.currentTimeMillis() - startTime);
        }
    }
    
    // Utility methods
    
    private void createTestNetworkConfig() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            for (int i = 1; i <= TOTAL_MEMBERS; i++) {
                writer.println("M" + i + ",localhost," + (9000 + i));
            }
        }
    }
    
    private void resetAllMembers() {
        for (EnhancedCouncilMember member : members) {
            member.reset();
        }
        try {
            Thread.sleep(1000); // Give time for reset
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private EnhancedCouncilMember getMember(String memberId) {
        return members.stream()
            .filter(m -> m.getMemberId().equals(memberId))
            .findFirst()
            .orElse(null);
    }
    
    private String waitForConsensus(long timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            for (EnhancedCouncilMember member : members) {
                if (member.hasLearned()) {
                    return member.getLearnedValue();
                }
            }
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return null; // Timeout
    }
    
    private boolean verifyConsensus(String expectedValue) {
        for (EnhancedCouncilMember member : members) {
            if (!member.hasLearned() || !expectedValue.equals(member.getLearnedValue())) {
                return false;
            }
        }
        return true;
    }
    
    private int countAgreeingMembers(String value) {
        int count = 0;
        for (EnhancedCouncilMember member : members) {
            if (member.hasLearned() && value.equals(member.getLearnedValue())) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Main method to run the test suite
     */
    public static void main(String[] args) {
        System.out.println("Starting Paxos Algorithm Test Suite");
        System.out.println("===================================");
        
        PaxosTestSuite testSuite = new PaxosTestSuite();
        List<TestResult> results = testSuite.runAllTests();
        
        // Print results
        System.out.println("\nTest Results Summary:");
        System.out.println("=====================");
        
        int passed = 0;
        int failed = 0;
        
        for (TestResult result : results) {
            System.out.println(result);
            if (result.isSuccess()) {
                passed++;
            } else {
                failed++;
            }
        }
        
        System.out.println("\nOverall Results:");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("Total:  " + (passed + failed));
        System.out.println("Success Rate: " + (100.0 * passed / (passed + failed)) + "%");
        
        // Detailed failure analysis
        if (failed > 0) {
            System.out.println("\nFailed Test Details:");
            System.out.println("====================");
            for (TestResult result : results) {
                if (!result.isSuccess()) {
                    System.out.println("\n" + result.getTestName() + ":");
                    System.out.println("  Reason: " + result.getDetails());
                    System.out.println("  Execution Time: " + result.getExecutionTime() + "ms");
                    
                    if (!result.getMemberStates().isEmpty()) {
                        System.out.println("  Member States:");
                        for (Map.Entry<String, String> entry : result.getMemberStates().entrySet()) {
                            System.out.println("    " + entry.getKey() + ": " + entry.getValue());
                        }
                    }
                }
            }
        }
        
        System.exit(failed > 0 ? 1 : 0);
    }
}