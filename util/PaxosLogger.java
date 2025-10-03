package util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PaxosLogger {
    private static final boolean DEBUG_ENABLED = Boolean.parseBoolean(
        System.getProperty("paxos.debug", "false"));
    private static final DateTimeFormatter TIME_FORMAT = 
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
    
    /**
     * Log an info message
     */
    public static void info(String memberId, String message) {
        log(LogLevel.INFO, memberId, message);
    }
    
    /**
     * Log a warning message
     */
    public static void warn(String memberId, String message) {
        log(LogLevel.WARN, memberId, message);
    }
    
    /**
     * Log an error message with exception
     */
    public static void error(String memberId, String message, Throwable t) {
        log(LogLevel.ERROR, memberId, message);
        if (t != null && DEBUG_ENABLED) {
            t.printStackTrace();
        }
    }
    
    /**
     * Log a debug message
     */
    public static void debug(String memberId, String message) {
        if (DEBUG_ENABLED) {
            log(LogLevel.DEBUG, memberId, message);
        }
    }
    
    /**
     * Log a consensus achievement
     */
    public static void consensus(String memberId, String value) {
        System.out.println(String.format("CONSENSUS: %s has been elected Council President!", value));
        info(memberId, "Learned consensus value: " + value);
    }
    
    private static void log(LogLevel level, String memberId, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String output = String.format("[%s] [%s] %s: %s", 
            timestamp, level, memberId != null ? memberId : "SYSTEM", message);
        
        if (level == LogLevel.ERROR) {
            System.err.println(output);
        } else {
            System.out.println(output);
        }
    }
}