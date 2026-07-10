package uk.gov.defra.cdp.dynamicsgateway.filter;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminSecretFilterTest {

    private static final String HEADER_NAME = "Trade-Imports-Animals-Admin-Secret";
    private static final String SECRET = "s3cr3t";

    @Mock private FilterChain chain;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private AdminSecretFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AdminSecretFilter(SECRET);
    }

    @Test
    void allowsGetList_withoutSecret() throws Exception {
        // shouldNotFilter short-circuits on the non-mutating method, so the URI is never inspected.
        when(request.getMethod()).thenReturn("GET");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void allowsNonDlqMutation_withoutSecret() throws Exception {
        // Only /dlq/notifications is guarded — other mutating endpoints (e.g. /events) pass through.
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/events");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void allowsReplay_whenSecretMatches() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/dlq/notifications/replay");
        when(request.getHeader(HEADER_NAME)).thenReturn(SECRET);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void rejectsReplay_whenHeaderMissing() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/dlq/notifications/replay");
        when(request.getHeader(HEADER_NAME)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void rejectsReplay_whenSecretMismatches() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/dlq/notifications/replay-all");
        when(request.getHeader(HEADER_NAME)).thenReturn("wrong");

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void rejectsReplay_whenConfiguredSecretBlank_failClosed() throws Exception {
        filter = new AdminSecretFilter("");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/dlq/notifications/replay");
        when(request.getHeader(HEADER_NAME)).thenReturn("anything");

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(request, response);
    }
}
