package app.mockly.domain.interview.controller.docs;

import com.epages.restdocs.apispec.HeaderDescriptorWithType;
import com.epages.restdocs.apispec.ResourceSnippetParameters;

import java.util.List;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;

public class StreamQuestionDocs {

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .summary("면접 질문 SSE 스트리밍")
                .description("""
                        면접 질문을 SSE(Server-Sent Events)로 스트리밍합니다.
                        세션 생성(POST /api/interviews) 또는 답변 제출(POST /api/interviews/{sessionId}/answer) 후 호출하세요.

                        이벤트 타입:
                        - token: 질문 토큰 (data: "영속성")
                        - done: 스트리밍 완료 (data: {})
                        - error: 오류 발생 (data: {"message": "..."})

                        재연결 시 이미 생성된 질문이 있으면 DB에서 반환합니다 (OpenAI 재호출 없음).
                        """)
                .requestHeaders(REQUEST_HEADERS)
                .build();
    }

    public static ResourceSnippetParameters notFound() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .build();
    }
}
