package app.mockly.domain.interview.controller;

import app.mockly.domain.interview.dto.response.ImprovementPracticeStatusInfo;
import app.mockly.domain.interview.entity.ImprovementPracticeStatus;
import app.mockly.domain.interview.service.ImprovementPracticeService;
import app.mockly.domain.interview.service.ImprovementPracticeSseManager;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImprovementPracticeSseRaceTest {

    private ImprovementPracticeService improvementPracticeService;
    private ImprovementPracticeSseManager sseManager;
    private ImprovementPracticeController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        improvementPracticeService = mock(ImprovementPracticeService.class);
        sseManager = new ImprovementPracticeSseManager(new ObjectMapper());
        controller = new ImprovementPracticeController(improvementPracticeService, sseManager);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("SSE 연결 직후 상태 조회 도중 비동기 평가가 완료되어도 터미널 이벤트를 유실하지 않고 즉시 종료")
    void streamEvents_whenAsyncCompletesDuringStatusLookup_deliversTerminalEventWithoutHanging() throws Exception {
        UUID userId = UUID.randomUUID();
        Long practiceId = 101L;

        given(improvementPracticeService.getStatusInfo(null, practiceId)).willAnswer(invocation -> {
            // connect() 이후 getStatusInfo()가 GENERATING을 읽는 사이 비동기 핸들러가 COMPLETED 발행·종료하는 경합 시뮬레이션
            sseManager.send(practiceId, ImprovementPracticeStatus.COMPLETED);
            sseManager.complete(practiceId);
            return new ImprovementPracticeStatusInfo(ImprovementPracticeStatus.GENERATING, null);
        });

        MvcResult mvcResult = mockMvc.perform(
                        get("/api/interviews/improvement-practices/{practiceId}/events", practiceId)
                                .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        String sseBody = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sseBody)
                .contains("event:status")
                .contains("\"practiceStatus\":\"completed\"");
        assertThat(sseManager.hasActiveSubscribers(practiceId)).isFalse();
    }

    @Test
    @DisplayName("connect 이후 비동기 완료와 터미널 재조회가 동시에 겹쳐도 중복 전송 없이 1회만 발행")
    void streamEvents_whenAsyncCompletesAndStatusAlreadyCompleted_emitsSingleTerminalEvent() throws Exception {
        Long practiceId = 102L;

        given(improvementPracticeService.getStatusInfo(null, practiceId)).willAnswer(invocation -> {
            sseManager.send(practiceId, ImprovementPracticeStatus.COMPLETED);
            sseManager.complete(practiceId);
            return new ImprovementPracticeStatusInfo(ImprovementPracticeStatus.COMPLETED, null);
        });

        MvcResult mvcResult = mockMvc.perform(
                        get("/api/interviews/improvement-practices/{practiceId}/events", practiceId)
                                .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        String sseBody = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        int eventCount = sseBody.split("event:status", -1).length - 1;
        assertThat(eventCount).isEqualTo(1);
        assertThat(sseManager.hasActiveSubscribers(practiceId)).isFalse();
    }

    @Test
    @DisplayName("상태 조회에서 권한/미존재 예외 발생 시 선등록된 SseEmitter가 누수 없이 정리됨")
    void streamEvents_whenStatusLookupThrows_cleansUpRegisteredEmitter() {
        UUID userId = UUID.randomUUID();
        Long practiceId = 103L;

        given(improvementPracticeService.getStatusInfo(userId, practiceId))
                .willThrow(new BusinessException(ApiStatusCode.FORBIDDEN, "권한 없음"));

        assertThatThrownBy(() -> controller.streamEvents(userId, practiceId))
                .isInstanceOf(BusinessException.class);
        assertThat(sseManager.hasActiveSubscribers(practiceId)).isFalse();
    }
}
