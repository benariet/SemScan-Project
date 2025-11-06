package edu.bgu.semscanapi.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Simple Logback filter that suppresses SQL statements targeting the app_logs table.
 */
public class AppLogInsertFilter extends Filter<ILoggingEvent> {

    private static final String SUPPRESSED_PATTERN = "insert into app_logs";

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (!isStarted() || event == null) {
            return FilterReply.NEUTRAL;
        }

        String message = event.getFormattedMessage();
        if (message != null && message.toLowerCase().contains(SUPPRESSED_PATTERN)) {
            return FilterReply.DENY;
        }

        return FilterReply.NEUTRAL;
    }

    @Override
    public void start() {
        super.start();
    }
}

