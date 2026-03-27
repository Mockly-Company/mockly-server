package app.mockly.domain.interview.entity;

import java.util.List;

public enum InterviewType {
    TECHNICAL,
    BEHAVIORAL,
    MIXED;

    public String getDescription() {
        return switch (this) {
            case TECHNICAL -> "기술 면접";
            case BEHAVIORAL -> "인성 면접";
            case MIXED -> "혼합";
        };
    }

    public List<String> getCategories() {
        return switch (this) {
            case TECHNICAL -> List.of("기술 역량", "문제 해결", "시스템 설계", "코드 품질");
            case BEHAVIORAL -> List.of("의사소통", "태도 및 열정", "팀워크", "리더십");
            case MIXED -> List.of("의사소통", "기술 역량", "문제 해결", "태도 및 열정");
        };
    }
}
