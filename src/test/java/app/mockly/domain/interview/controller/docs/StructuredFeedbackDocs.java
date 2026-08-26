package app.mockly.domain.interview.controller.docs;

import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

final class StructuredFeedbackDocs {
    private StructuredFeedbackDocs() {}

    static List<FieldDescriptor> fields(String path) {
        return fields(path, false);
    }

    static List<FieldDescriptor> optionalFields(String path) {
        return fields(path, true);
    }

    private static List<FieldDescriptor> fields(String path, boolean optionalParent) {
        return List.of(
                descriptor(path + ".overallScore", "종합 점수 (1~100)", SimpleType.NUMBER, optionalParent),
                descriptor(path + ".generatedTier", "피드백 생성 당시 플랜 (FREE | BASIC | PRO)", SimpleType.STRING, optionalParent),
                descriptor(path + ".coachBrief", "코치 브리핑", JsonFieldType.OBJECT, optionalParent),
                descriptor(path + ".coachBrief.summary", "핵심 요약", SimpleType.STRING, optionalParent),
                descriptor(path + ".coachBrief.keyStrength", "핵심 강점 (생성하지 않은 경우 null)", SimpleType.STRING, optionalParent),
                descriptor(path + ".coachBrief.keyImprovement", "핵심 개선점 (생성하지 않은 경우 null)", SimpleType.STRING, optionalParent),
                descriptor(path + ".scores", "4축 점수 (잠금 상태면 null)", JsonFieldType.OBJECT, optionalParent),
                descriptor(path + ".scores.structure", "답변 구조 점수", SimpleType.NUMBER, true),
                descriptor(path + ".scores.specificity", "구체성 점수", SimpleType.NUMBER, true),
                descriptor(path + ".scores.jobRelevance", "직무 연관성 점수", SimpleType.NUMBER, true),
                descriptor(path + ".scores.clarity", "전달 명확성 점수", SimpleType.NUMBER, true),
                descriptor(path + ".strengths", "구조화된 강점 목록", JsonFieldType.ARRAY, optionalParent),
                descriptor(path + ".strengths[].id", "강점 ID", SimpleType.NUMBER, true),
                descriptor(path + ".strengths[].questionNumber", "관련 질문 번호", SimpleType.NUMBER, true),
                descriptor(path + ".strengths[].title", "강점 제목", SimpleType.STRING, true),
                descriptor(path + ".strengths[].detail", "강점 상세 설명", SimpleType.STRING, true),
                descriptor(path + ".strengths[].quote", "사용자 답변 인용", SimpleType.STRING, true),
                descriptor(path + ".strengths[].sortOrder", "표시 순서", SimpleType.NUMBER, true),
                descriptor(path + ".improvements", "구조화된 개선점 목록", JsonFieldType.ARRAY, optionalParent),
                descriptor(path + ".improvements[].id", "개선점 ID", SimpleType.NUMBER, optionalParent),
                descriptor(path + ".improvements[].rank", "추천 순위", SimpleType.NUMBER, optionalParent),
                descriptor(path + ".improvements[].questionNumber", "관련 질문 번호", SimpleType.NUMBER, optionalParent),
                descriptor(path + ".improvements[].title", "개선점 제목", SimpleType.STRING, optionalParent),
                descriptor(path + ".improvements[].summary", "개선점 한 줄 요약", SimpleType.STRING, optionalParent),
                descriptor(path + ".improvements[].detail", "개선점 상세 설명 (생성하지 않은 경우 null)", SimpleType.STRING, optionalParent),
                descriptor(path + ".improvements[].quote", "사용자 답변 인용 (생성하지 않은 경우 null)", SimpleType.STRING, optionalParent),
                descriptor(path + ".improvements[].practiceAvailable", "현재 플랜과 상세 데이터 기준 개선 연습 자격", JsonFieldType.BOOLEAN, optionalParent),
                descriptor(path + ".nextPracticePoint", "다음 연습 포인트 (Pro 생성 피드백만)", SimpleType.STRING, optionalParent)
        );
    }

    private static FieldDescriptor descriptor(String path, String description, Object type, boolean optional) {
        FieldDescriptor descriptor = fieldWithPath(path).description(description).type(type);
        return optional ? descriptor.optional() : descriptor;
    }
}
