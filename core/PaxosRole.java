package core;
/**
 * Interface defining the three roles in the Paxos consensus algorithm
 * Each council member can act in all three roles simultaneously
 */
public interface PaxosRole {
    
    /**
     * Proposer role interface - initiates proposals and drives consensus
     */
    interface Proposer {
        
        /**
         * Initiates a new proposal for electing a candidate
         * This starts Phase 1 of the Paxos algorithm
         * 
         * @param candidate The candidate being proposed for president
         * @return true if the proposal process was started successfully
         */
        boolean propose(String candidate);
        
        /**
         * Handles a PROMISE response from an Acceptor
         * Part of Phase 1 of Paxos
         * 
         * @param message The PROMISE message received
         */
        void handlePromise(PaxosMessage message);
        
        /**
         * Handles an ACCEPTED response from an Acceptor  
         * Part of Phase 2 of Paxos
         * 
         * @param message The ACCEPTED message received
         */
        void handleAccepted(PaxosMessage message);
        
        /**
         * Gets the current proposal number being used
         * 
         * @return The current proposal number, or null if no active proposal
         */
        String getCurrentProposalNumber();
        
        /**
         * Checks if this proposer has an active proposal
         * 
         * @return true if actively proposing
         */
        boolean isActivelyProposing();
    }
    
    /**
     * Acceptor role interface - receives and responds to proposals
     */
    interface Acceptor {
        
        /**
         * Handles a PREPARE request from a Proposer
         * Phase 1 of Paxos - decides whether to promise not to accept
         * any proposal with a number less than the received proposal number
         * 
         * @param message The PREPARE message received
         */
        void handlePrepare(PaxosMessage message);
        
        /**
         * Handles an ACCEPT_REQUEST from a Proposer
         * Phase 2 of Paxos - decides whether to accept the proposed value
         * 
         * @param message The ACCEPT_REQUEST message received
         */
        void handleAcceptRequest(PaxosMessage message);
        
        /**
         * Gets the highest proposal number this acceptor has promised to
         * 
         * @return The highest promised proposal number, or null if none
         */
        String getHighestPromisedProposal();
        
        /**
         * Gets the highest proposal number this acceptor has accepted
         * 
         * @return The highest accepted proposal number, or null if none
         */
        String getHighestAcceptedProposal();
        
        /**
         * Gets the value of the highest accepted proposal
         * 
         * @return The accepted proposal value, or null if none
         */
        String getAcceptedValue();
    }
    
    /**
     * Learner role interface - learns the final decided value
     */
    interface Learner {
        
        /**
         * Handles an ACCEPTED message from an Acceptor
         * Learns about accepted proposals and determines when consensus is reached
         * 
         * @param message The ACCEPTED message received
         */
        void handleAccepted(PaxosMessage message);
        
        /**
         * Handles a LEARN message (direct notification of consensus)
         * 
         * @param message The LEARN message received
         */
        void handleLearn(PaxosMessage message);
        
        /**
         * Gets the learned value if consensus has been reached
         * 
         * @return The consensus value, or null if no consensus yet
         */
        String getLearnedValue();
        
        /**
         * Checks if consensus has been reached
         * 
         * @return true if consensus has been reached
         */
        boolean hasLearned();
        
        /**
         * Gets the proposal number of the learned consensus
         * 
         * @return The proposal number of the consensus, or null if no consensus
         */
        String getLearnedProposalNumber();
    }
    
    /**
     * Common interface for all Paxos participants
     */
    interface Participant extends Proposer, Acceptor, Learner {
        
        /**
         * Processes any incoming Paxos message
         * Routes the message to the appropriate role handler
         * 
         * @param message The incoming message to process
         */
        void processMessage(PaxosMessage message);
        
        /**
         * Gets the member ID of this participant
         * 
         * @return The member ID (e.g., "M1")
         */
        String getMemberId();
        
        /**
         * Resets the participant state for a new consensus round
         * Clears any state from previous proposals while preserving learned values
         */
        void reset();
        
        /**
         * Gets the current state of this participant as a string
         * Useful for debugging and logging
         * 
         * @return A string describing the current state
         */
        String getState();
    }
}