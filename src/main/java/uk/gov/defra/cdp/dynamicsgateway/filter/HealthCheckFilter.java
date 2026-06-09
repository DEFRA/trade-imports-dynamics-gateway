package uk.gov.defra.cdp.dynamicsgateway.filter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

public class HealthCheckFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event.getMDCPropertyMap() == null) {
            return FilterReply.NEUTRAL;
        }

        String url = event.getMDCPropertyMap().get("url.full");
        if (url != null && url.contains("/health")) {
            return FilterReply.DENY;
        }

        return FilterReply.NEUTRAL;
    }
}
