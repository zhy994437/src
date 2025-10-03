/**
 * Represents a message in the Paxos protocol
 * Message format: TYPE:SENDER_ID:PROPOSAL_NUM:PROPOSAL_VAL
 */
public class PaxosMessage {
    
    /**
     * Enum representing different types of Paxos messages
     */
    public enum MessageType {
        PREPARE,        // Phase 1a: Proposer sends prepare request
        PROMISE,        // Phase 1b: Acceptor responds with promise
        ACCEPT_REQUEST, // Phase 2a: Proposer sends accept request
        ACCEPTED,       // Phase 2b: Acceptor confirms acceptance
        LEARN          // Learning phase: Notifying learners
    }
    
    private final MessageType type;
    private final String senderId;
    private final String proposalNumber;
    private final String proposalValue;
    private final String acceptedProposalNumber; // For PROMISE messages
    private final String acceptedValue;          // For PROMISE messages
    
    /**
     * Constructor for basic Paxos messages
     * 
     * @param type The message type
     * @param senderId The ID of the sender (e.g., "M1")
     * @param proposalNumber The proposal number (e.g., "1.1")
     * @param proposalValue The proposed value (e.g., candidate name)
     */
    public PaxosMessage(MessageType type, String senderId, String proposalNumber, String proposalValue) {
        this.type = type;
        this.senderId = senderId;
        this.proposalNumber = proposalNumber;
        this.proposalValue = proposalValue;
        this.acceptedProposalNumber = null;
        this.acceptedValue = null;
    }
    
    /**
     * Constructor for PROMISE messages that may contain previously accepted values
     * 
     * @param type The message type (should be PROMISE)
     * @param senderId The ID of the sender
     * @param proposalNumber The proposal number being responded to
     * @param proposalValue The current proposal value
     * @param acceptedProposalNumber The highest proposal number previously accepted
     * @param acceptedValue The value of the previously accepted proposal
     */
    public PaxosMessage(MessageType type, String senderId, String proposalNumber, 
                       String proposalValue, String acceptedProposalNumber, String acceptedValue) {
        this.type = type;
        this.senderId = senderId;
        this.proposalNumber = proposalNumber;
        this.proposalValue = proposalValue;
        this.acceptedProposalNumber = acceptedProposalNumber;
        this.acceptedValue = acceptedValue;
    }
    
    /**
     * Serializes the message to a string format for network transmission
     * Format: TYPE:SENDER_ID:PROPOSAL_NUM:PROPOSAL_VAL[:ACCEPTED_PROPOSAL_NUM:ACCEPTED_VAL]
     * 
     * @return The serialized message string
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.name()).append(":")
          .append(senderId).append(":")
          .append(proposalNumber != null ? proposalNumber : "").append(":")
          .append(proposalValue != null ? proposalValue : "");
        
        // Add accepted proposal info for PROMISE messages
        if (acceptedProposalNumber != null && acceptedValue != null) {
            sb.append(":").append(acceptedProposalNumber)
              .append(":").append(acceptedValue);
        }
        
        return sb.toString();
    }
    
    /**
     * Deserializes a message from string format
     * 
     * @param messageStr The serialized message string
     * @return A PaxosMessage object
     * @throws IllegalArgumentException If the message format is invalid
     */
    public static PaxosMessage deserialize(String messageStr) {
        if (messageStr == null || messageStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Message string cannot be null or empty");
        }
        
        String[] parts = messageStr.split(":", -1); // -1 to include empty strings
        
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid message format: " + messageStr);
        }
        
        try {
            MessageType type = MessageType.valueOf(parts[0]);
            String senderId = parts[1];
            String proposalNumber = parts[2].isEmpty() ? null : parts[2];
            String proposalValue = parts[3].isEmpty() ? null : parts[3];
            
            // Check if this is a PROMISE message with accepted values
            if (parts.length >= 6 && !parts[4].isEmpty() && !parts[5].isEmpty()) {
                String acceptedProposalNumber = parts[4];
                String acceptedValue = parts[5];
                return new PaxosMessage(type, senderId, proposalNumber, proposalValue, 
                                      acceptedProposalNumber, acceptedValue);
            }
            
            return new PaxosMessage(type, senderId, proposalNumber, proposalValue);
            
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid message format: " + messageStr, e);
        }
    }
    
    // Getter methods
    
    /**
     * @return The message type
     */
    public MessageType getType() {
        return type;
    }
    
    /**
     * @return The sender ID
     */
    public String getSenderId() {
        return senderId;
    }
    
    /**
     * @return The proposal number
     */
    public String getProposalNumber() {
        return proposalNumber;
    }
    
    /**
     * @return The proposal value
     */
    public String getProposalValue() {
        return proposalValue;
    }
    
    /**
     * @return The accepted proposal number (for PROMISE messages)
     */
    public String getAcceptedProposalNumber() {
        return acceptedProposalNumber;
    }
    
    /**
     * @return The accepted value (for PROMISE messages)
     */
    public String getAcceptedValue() {
        return acceptedValue;
    }
    
    /**
     * Checks if this PROMISE message contains a previously accepted value
     * 
     * @return true if the message contains accepted proposal info
     */
    public boolean hasAcceptedValue() {
        return acceptedProposalNumber != null && acceptedValue != null;
    }
    
    /**
     * Compares two proposal numbers
     * Proposal numbers are in format: "counter.memberId" (e.g., "3.1")
     * 
     * @param proposal1 First proposal number
     * @param proposal2 Second proposal number
     * @return negative if proposal1 < proposal2, 0 if equal, positive if proposal1 > proposal2
     */
    public static int compareProposalNumbers(String proposal1, String proposal2) {
        if (proposal1 == null && proposal2 == null) return 0;
        if (proposal1 == null) return -1;
        if (proposal2 == null) return 1;
        
        try {
            String[] parts1 = proposal1.split("\\.");
            String[] parts2 = proposal2.split("\\.");
            
            if (parts1.length != 2 || parts2.length != 2) {
                // Fallback to string comparison
                return proposal1.compareTo(proposal2);
            }
            
            int counter1 = Integer.parseInt(parts1[0]);
            int counter2 = Integer.parseInt(parts2[0]);
            
            if (counter1 != counter2) {
                return Integer.compare(counter1, counter2);
            }
            
            // If counters are equal, compare member IDs
            int memberId1 = Integer.parseInt(parts1[1]);
            int memberId2 = Integer.parseInt(parts2[1]);
            
            return Integer.compare(memberId1, memberId2);
            
        } catch (NumberFormatException e) {
            // Fallback to string comparison
            return proposal1.compareTo(proposal2);
        }
    }
    
    @Override
    public String toString() {
        return String.format("PaxosMessage{type=%s, sender=%s, proposalNum=%s, proposalVal=%s%s}",
                type, senderId, proposalNumber, proposalValue,
                hasAcceptedValue() ? ", acceptedNum=" + acceptedProposalNumber + 
                                   ", acceptedVal=" + acceptedValue : "");
    }
}