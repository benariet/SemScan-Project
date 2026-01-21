package edu.bgu.semscanapi.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Filter that suppresses SQL statements targeting the app_logs table.
 * This filter prevents these logs from being written to both console and file appenders.
 * Handles cases where the SQL statement may be split across multiple lines.
 */
public class AppLogInsertFilter extends Filter<ILoggingEvent> {

    private static final String SUPPRESSED_PATTERN = "insert into  app_logs";
    private static final String SUPPRESSED_PATTERN_NORMALIZED = "insertintoapp_logs";

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (!isStarted() || event == null) {
            return FilterReply.NEUTRAL;
        }

        String loggerName = event.getLoggerName();
        String message = event.getFormattedMessage();
        
        // Check if this is a Hibernate SQL log - filter ALL lines that contain app_logs
        // Since Hibernate SQL logs are split across multiple lines, we filter any line
        // from org.hibernate.SQL logger that contains app_logs
        if (loggerName != null && loggerName.contains("hibernate.SQL")) {
            if (message != null) {
                String lowerMessage = message.toLowerCase();
                if (lowerMessage.contains("app_logs")) {
                    return FilterReply.DENY;
                }
            }
        }

        // Also check formatted message for the pattern (handles single-line logs)
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            // Check for exact pattern
            if (lowerMessage.contains(SUPPRESSED_PATTERN)) {
                return FilterReply.DENY;
            }
            // Check for pattern split across lines (normalize whitespace)
            String normalized = lowerMessage.replaceAll("\\s+", "");
            if (normalized.contains(SUPPRESSED_PATTERN_NORMALIZED)) {
                return FilterReply.DENY;
            }
        }

        return FilterReply.NEUTRAL;
    }

    @Override
    public void start() {
        super.start();
    }
}

