package app.mockly.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@Profile("preprod")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PreprodRequestAccessLogFilter extends OncePerRequestFilter {

    private static final String STARTED_AT_ATTRIBUTE =
            PreprodRequestAccessLogFilter.class.getName() + ".startedAt";
    private static final String LOGGED_ATTRIBUTE =
            PreprodRequestAccessLogFilter.class.getName() + ".logged";
    private static final String ORIGINAL_METHOD_ATTRIBUTE =
            PreprodRequestAccessLogFilter.class.getName() + ".originalMethod";
    private static final String ORIGINAL_PATH_ATTRIBUTE =
            PreprodRequestAccessLogFilter.class.getName() + ".originalPath";

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        captureOriginalRequestMetadata(request);
        long startedAt = getOrCreateStartedAt(request);
        AtomicBoolean logged = getOrCreateLogged(request);
        boolean dispatchCompleted = false;

        try {
            filterChain.doFilter(request, response);
            dispatchCompleted = true;
        } finally {
            if (dispatchCompleted) {
                if (request.isAsyncStarted()) {
                    registerAsyncCompletionLog(request, response, startedAt, logged);
                } else {
                    logRequestOnce(request, response, startedAt, logged);
                }
            }
        }
    }

    private long getOrCreateStartedAt(HttpServletRequest request) {
        Object existing = request.getAttribute(STARTED_AT_ATTRIBUTE);
        if (existing instanceof Long startedAt) {
            return startedAt;
        }

        long startedAt = System.nanoTime();
        request.setAttribute(STARTED_AT_ATTRIBUTE, startedAt);
        return startedAt;
    }

    private AtomicBoolean getOrCreateLogged(HttpServletRequest request) {
        Object existing = request.getAttribute(LOGGED_ATTRIBUTE);
        if (existing instanceof AtomicBoolean logged) {
            return logged;
        }

        AtomicBoolean logged = new AtomicBoolean();
        request.setAttribute(LOGGED_ATTRIBUTE, logged);
        return logged;
    }

    private void captureOriginalRequestMetadata(HttpServletRequest request) {
        if (request.getDispatcherType() == DispatcherType.REQUEST) {
            request.setAttribute(ORIGINAL_METHOD_ATTRIBUTE, request.getMethod());
            request.setAttribute(ORIGINAL_PATH_ATTRIBUTE, request.getRequestURI());
        }
    }

    private void registerAsyncCompletionLog(
            HttpServletRequest request,
            HttpServletResponse response,
            long startedAt,
            AtomicBoolean logged
    ) {
        request.getAsyncContext().addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
                logRequestOnce(request, response, startedAt, logged);
            }

            @Override
            public void onTimeout(AsyncEvent event) {
            }

            @Override
            public void onError(AsyncEvent event) {
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
                event.getAsyncContext().addListener(this);
            }
        });
    }

    private void logRequestOnce(
            HttpServletRequest request,
            HttpServletResponse response,
            long startedAt,
            AtomicBoolean logged
    ) {
        if (logged.compareAndSet(false, true)) {
            logRequest(request, response, startedAt);
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long startedAt) {
        long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "preprod_request method={} path={} status={} duration_ms={}",
                getOriginalMethod(request),
                getOriginalPath(request),
                response.getStatus(),
                durationMillis
        );
    }

    private String getOriginalMethod(HttpServletRequest request) {
        Object originalMethod = request.getAttribute(ORIGINAL_METHOD_ATTRIBUTE);
        return originalMethod instanceof String method ? method : request.getMethod();
    }

    private String getOriginalPath(HttpServletRequest request) {
        Object originalPath = request.getAttribute(ORIGINAL_PATH_ATTRIBUTE);
        return originalPath instanceof String path ? path : request.getRequestURI();
    }
}
