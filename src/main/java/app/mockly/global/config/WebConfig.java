package app.mockly.global.config;

import app.mockly.domain.interview.interceptor.InterviewSubscriptionAccessInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final InterviewSubscriptionAccessInterceptor interviewSubscriptionAccessInterceptor;

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interviewSubscriptionAccessInterceptor)
                .addPathPatterns("/api/interviews", "/api/interviews/**");
    }
}
