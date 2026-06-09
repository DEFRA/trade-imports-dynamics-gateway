package uk.gov.defra.cdp.dynamicsgateway.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestTracingFilter implements Filter {

    private static final String MDC_TRACE_ID = "trace.id";
    private static final String MDC_HTTP_METHOD = "http.request.method";
    private static final String MDC_HTTP_STATUS = "http.response.status_code";
    private static final String MDC_URL_FULL = "url.full";

    @Value("${cdp.tracing.header-name}")
    private String header;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String traceId = httpRequest.getHeader(header);
            if (traceId != null && !traceId.isBlank()) {
                MDC.put(MDC_TRACE_ID, traceId);
            }

            final String method = httpRequest.getMethod();
            final String url = httpRequest.getRequestURL().toString();
            MDC.put(MDC_HTTP_METHOD, method);
            MDC.put(MDC_URL_FULL, url);

            log.debug("{} {}", method, url);

            chain.doFilter(request, response);

            if (response instanceof HttpServletResponse httpResponse) {
                MDC.put(MDC_HTTP_STATUS, String.valueOf(httpResponse.getStatus()));
                log.debug("Response status {} for {}", httpResponse.getStatus(), url);
            }

        } finally {
            MDC.clear();
        }
    }
}
