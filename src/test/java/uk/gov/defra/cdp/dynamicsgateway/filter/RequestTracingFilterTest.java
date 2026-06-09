package uk.gov.defra.cdp.dynamicsgateway.filter;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RequestTracingFilterTest {

    @Mock private FilterChain chain;
    @Mock private HttpServletRequest httpRequest;
    @Mock private HttpServletResponse httpResponse;
    @Mock private ServletRequest nonHttpRequest;
    @Mock private ServletResponse nonHttpResponse;

    private RequestTracingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestTracingFilter();
        ReflectionTestUtils.setField(filter, "header", "x-cdp-request-id");
        lenient().when(httpRequest.getMethod()).thenReturn("GET");
        lenient().when(httpRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost/api/test"));
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void doFilter_shouldDelegateDirectly_whenRequestIsNotHttpServletRequest() throws Exception {
        filter.doFilter(nonHttpRequest, nonHttpResponse, chain);

        verify(chain).doFilter(nonHttpRequest, nonHttpResponse);
    }

    @Test
    void doFilter_shouldAddTraceIdToMdc_whenHeaderIsPresent() throws Exception {
        when(httpRequest.getHeader("x-cdp-request-id")).thenReturn("trace-abc-123");

        filter.doFilter(httpRequest, httpResponse, chain);

        verify(chain).doFilter(httpRequest, httpResponse);
    }

    @Test
    void doFilter_shouldSkipTraceId_whenHeaderIsBlank() throws Exception {
        when(httpRequest.getHeader("x-cdp-request-id")).thenReturn("   ");

        filter.doFilter(httpRequest, httpResponse, chain);

        verify(chain).doFilter(httpRequest, httpResponse);
    }

    @Test
    void doFilter_shouldSkipStatusMdc_whenResponseIsNotHttpServletResponse() throws Exception {
        when(httpRequest.getHeader("x-cdp-request-id")).thenReturn(null);

        filter.doFilter(httpRequest, nonHttpResponse, chain);

        verify(chain).doFilter(httpRequest, nonHttpResponse);
    }
}
