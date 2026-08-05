package app.mockly.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("preprod")
class PreprodRequestAccessLogFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void registersExactlyOneAccessLogFilterInPreprod() {
        assertThat(applicationContext.getBeansOfType(PreprodRequestAccessLogFilter.class)).hasSize(1);
    }

    @Test
    void logsSpringSecurityUnauthorizedCompletionWithOnlyRequiredMetadata() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(PreprodRequestAccessLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
                mockMvc.perform(get("/api/auth/me")
                                .queryParam("debugToken", "query-secret")
                                .header(HttpHeaders.AUTHORIZATION, "Basic authorization-secret"))
                    .andExpect(status().isUnauthorized());

            assertThat(appender.list).hasSize(1);
            String message = appender.list.getFirst().getFormattedMessage();
            assertThat(message)
                    .matches("preprod_request method=GET path=/api/auth/me status=401 duration_ms=\\d+");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void logsAsyncRequestOnlyAfterCompletionWithFinalMetadata() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(PreprodRequestAccessLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/interviews/stream");
            request.setAsyncSupported(true);
            request.setQueryString("debugToken=query-secret");
            request.addHeader(HttpHeaders.AUTHORIZATION, "Basic authorization-secret");
            MockHttpServletResponse response = new MockHttpServletResponse();
            new PreprodRequestAccessLogFilter().doFilter(request, response, (req, res) -> {
                response.setStatus(202);
                request.startAsync();
            });

            assertThat(appender.list).isEmpty();

            ((MockAsyncContext) request.getAsyncContext()).complete();

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                    .matches("preprod_request method=GET path=/api/interviews/stream status=202 duration_ms=\\d+");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void asyncTimeoutDoesNotLogBeforeCompletionAndUsesFinalStatus() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(PreprodRequestAccessLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/interviews/timeout");
            request.setAsyncSupported(true);
            MockHttpServletResponse response = new MockHttpServletResponse();
            new PreprodRequestAccessLogFilter().doFilter(request, response, (req, res) -> request.startAsync());
            MockAsyncContext asyncContext = (MockAsyncContext) request.getAsyncContext();

            asyncContext.getListeners().getFirst()
                    .onTimeout(new AsyncEvent(asyncContext, request, response));

            assertThat(appender.list).isEmpty();

            response.setStatus(504);
            asyncContext.complete();

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                    .matches("preprod_request method=GET path=/api/interviews/timeout status=504 duration_ms=\\d+");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void asyncErrorDoesNotLogBeforeCompletionAndUsesFinalStatus() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(PreprodRequestAccessLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/interviews/error");
            request.setAsyncSupported(true);
            MockHttpServletResponse response = new MockHttpServletResponse();
            new PreprodRequestAccessLogFilter().doFilter(request, response, (req, res) -> request.startAsync());
            MockAsyncContext asyncContext = (MockAsyncContext) request.getAsyncContext();

            asyncContext.getListeners().getFirst()
                    .onError(new AsyncEvent(asyncContext, request, response, new IllegalStateException("async failure")));

            assertThat(appender.list).isEmpty();

            response.setStatus(500);
            asyncContext.complete();

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                    .matches("preprod_request method=GET path=/api/interviews/error status=500 duration_ms=\\d+");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void errorDispatchLogsOriginalRequestMethodAndPathWithFinalStatus() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(PreprodRequestAccessLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/interviews/session");
            MockHttpServletResponse response = new MockHttpServletResponse();
            PreprodRequestAccessLogFilter filter = new PreprodRequestAccessLogFilter();

            assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
                throw new ServletException("request failure");
            })).isInstanceOf(ServletException.class);
            assertThat(appender.list).isEmpty();

            request.setDispatcherType(DispatcherType.ERROR);
            request.setRequestURI("/error");
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/interviews/session");
            filter.doFilter(request, response, (req, res) -> response.setStatus(500));

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                    .matches("preprod_request method=POST path=/api/interviews/session status=500 duration_ms=\\d+");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
