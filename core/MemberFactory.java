package core;

import enhanced.EnhancedCouncilMember;
import enhanced.AdvancedCouncilMember;
import simulation.DynamicNetworkConfig;

public class MemberFactory {
    
    /**
     * Creates a basic council member
     */
    public static CouncilMember createBasicMember(String memberId, int port, 
                                                  CouncilMember.NetworkProfile profile) {
        return new CouncilMember(memberId, port, profile);
    }
    
    /**
     * Creates an enhanced council member with state management
     */
    public static EnhancedCouncilMember createEnhancedMember(String memberId, int port,
                                                            EnhancedCouncilMember.NetworkProfile profile) {
        return new EnhancedCouncilMember(memberId, port, profile);
    }
    
    /**
     * Creates an advanced member with full simulation capabilities
     */
    public static AdvancedCouncilMember createAdvancedMember(String memberId, int port,
                                                            NetworkBehaviorSimulator.NetworkProfile profile,
                                                            DynamicNetworkConfig config) {
        return new AdvancedCouncilMember(memberId, port, profile, config);
    }
    
    /**
     * Creates a member based on the specified type
     */
    public static CouncilMember createMember(MemberType type, String memberId, int port, 
                                            CouncilMember.NetworkProfile profile) {
        switch(type) {
            case BASIC:
                return createBasicMember(memberId, port, profile);
            case ENHANCED:
                return createEnhancedMember(memberId, port, 
                    EnhancedCouncilMember.NetworkProfile.valueOf(profile.name()));
            default:
                throw new IllegalArgumentException("Unknown member type: " + type);
        }
    }
    
    public enum MemberType {
        BASIC, ENHANCED, ADVANCED
    }
}