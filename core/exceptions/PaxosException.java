package core.exceptions;

/**
 * Base exception class for Paxos-related errors
 */
public class PaxosException extends Exception {
    public PaxosException(String message) {
        super(message);
    }
    
    public PaxosException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Exception for consensus-related errors
 */
class ConsensusException extends PaxosException {
    public ConsensusException(String message) {
        super(message);
    }
    
    public ConsensusException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Exception for network-related errors
 */
class NetworkException extends PaxosException {
    private final String targetMember;
    
    public NetworkException(String message, String targetMember) {
        super(message);
        this.targetMember = targetMember;
    }
    
    public String getTargetMember() {
        return targetMember;
    }
}

/**
 * Exception for configuration errors
 */
class ConfigurationException extends PaxosException {
    public ConfigurationException(String message) {
        super(message);
    }
}

/**
 * Exception for proposal failures
 */
class ProposalException extends PaxosException {
    private final String proposalNumber;
    
    public ProposalException(String message, String proposalNumber) {
        super(message);
        this.proposalNumber = proposalNumber;
    }
    
    public String getProposalNumber() {
        return proposalNumber;
    }
}