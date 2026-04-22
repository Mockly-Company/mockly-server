package app.mockly.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties("interview.ai")
public class InterviewAiProperties {
    private String keywordExtractionModel;
    private double keywordExtractionTemperature;
}
