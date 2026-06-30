package uk.gov.defra.cdp.dynamicsgateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards the privileged DLQ operations with a shared secret. The mutating endpoints under
 * {@code /dlq/notifications} (replay via {@code POST}, delete via {@code DELETE}) require the
 * {@value #HEADER_NAME} header to match {@code admin.secret}; the read-only list ({@code GET}) is left
 * open. The secret is the same value the admin app already holds and sends to the backend, so both
 * services must be configured with the same value per environment.
 *
 * <p>Mirrors {@code trade-imports-animals-backend}'s {@code AdminSecretFilter}: a constant-time
 * comparison ({@link MessageDigest#isEqual}), a blank configured secret treated as a fail-closed
 * mismatch, and a bare {@code 401} with no body on rejection.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class AdminSecretFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "Trade-Imports-Animals-Admin-Secret";
    private static final String GUARDED_PATH = "/dlq/notifications";

    @Value("${admin.secret}")
    private String adminSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws IOException, ServletException {
        String providedSecret = request.getHeader(HEADER_NAME);

        if (adminSecret.isBlank()
                || providedSecret == null
                || !MessageDigest.isEqual(
                    adminSecret.getBytes(StandardCharsets.UTF_8),
                    providedSecret.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Rejected {} {} — missing or invalid admin secret",
                request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        boolean mutating = "POST".equalsIgnoreCase(request.getMethod())
            || "DELETE".equalsIgnoreCase(request.getMethod());
        return !(mutating && request.getRequestURI().startsWith(GUARDED_PATH));
    }
}
