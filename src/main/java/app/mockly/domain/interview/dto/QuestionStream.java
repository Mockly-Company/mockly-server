package app.mockly.domain.interview.dto;

import reactor.core.publisher.Flux;

public record QuestionStream(int questionNumber, Flux<String> tokens) {}
