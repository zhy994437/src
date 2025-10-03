package config;

import java.io.*;
import java.util.*;

public class ConfigurationLoader {
    private static final String DEFAULT_CONFIG = "network.config";
    
    /**
     * Loads network configuration from file
     */
    public static NetworkConfiguration load(String configFile) throws IOException {
        File file = new File(configFile != null ? configFile : DEFAULT_CONFIG);
        NetworkConfiguration config = new NetworkConfiguration();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    config.addMember(parts[0].trim(), parts[1].trim(), 
                                   Integer.parseInt(parts[2].trim()));
                }
            }
        }
        
        validate(config);
        return config;
    }
    
    /**
     * Validates configuration integrity
     */
    public static void validate(NetworkConfiguration config) throws ConfigurationException {
        if (config.getMemberCount() < 3) {
            throw new ConfigurationException("At least 3 members required for consensus");
        }
        
        // Check for duplicate ports
        Set<Integer> ports = new HashSet<>();
        for (MemberInfo info : config.getMembers()) {
            if (!ports.add(info.getPort())) {
                throw new ConfigurationException("Duplicate port: " + info.getPort());
            }
        }
    }
    
    // Configuration data structure
    public static class NetworkConfiguration {
        private final Map<String, MemberInfo> members = new HashMap<>();
        
        public void addMember(String id, String host, int port) {
            members.put(id, new MemberInfo(id, host, port));
        }
        
        public int getMemberCount() { return members.size(); }
        public Collection<MemberInfo> getMembers() { return members.values(); }
        public MemberInfo getMember(String id) { return members.get(id); }
    }
    
    public static class MemberInfo {
        private final String id;
        private final String host;
        private final int port;
        
        public MemberInfo(String id, String host, int port) {
            this.id = id;
            this.host = host;
            this.port = port;
        }
        
        public String getId() { return id; }
        public String getHost() { return host; }
        public int getPort() { return port; }
    }
    
    public static class ConfigurationException extends Exception {
        public ConfigurationException(String message) {
            super(message);
        }
    }
}