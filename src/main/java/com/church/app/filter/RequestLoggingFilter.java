package com.church.app.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Logs one line per request completion: method, path, response status and elapsed time.
 *
 * <p>Static assets and actuator polling are skipped -- they would otherwise dominate the
 * log without telling us anything. Query strings are logged but request bodies are not,
 * deliberately: form posts in this application carry member and family personal data.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final Set<String> IGNORED_PREFIXES = Set.of(
            "/css/", "/js/", "/images/", "/webjars/", "/favicon.ico", "/actuator/"
    );

    /** Slower than this and the line is promoted to WARN so it stands out. */
    private static final long SLOW_REQUEST_THRESHOLD_MS = 2000L;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return IGNORED_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            String query = request.getQueryString();
            String path = StringUtils.hasText(query)
                    ? request.getRequestURI() + "?" + query
                    : request.getRequestURI();

            if (durationMs >= SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("{} {} -> {} ({} ms) SLOW", request.getMethod(), path, response.getStatus(), durationMs);
            } else {
                log.info("{} {} -> {} ({} ms)", request.getMethod(), path, response.getStatus(), durationMs);
            }
        }
    }
}
