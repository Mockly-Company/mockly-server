package app.mockly.domain.interview.interceptor;

import app.mockly.domain.product.service.SubscriptionAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class InterviewSubscriptionAccessInterceptor implements HandlerInterceptor {

    private final SubscriptionAccessService subscriptionAccessService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UUID userId) {
            subscriptionAccessService.validateInterviewAccess(userId);
        }
        return true;
    }
}
