package app.mockly.domain.meetup.controller;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.auth.service.TokenBlacklistService;
import app.mockly.domain.meetup.controller.docs.LocalInterviewMeetupDocs;
import app.mockly.domain.meetup.dto.request.CreateLocalInterviewMeetupRequest;
import app.mockly.domain.meetup.dto.request.CreateMeetupParticipantRequest;
import app.mockly.domain.meetup.dto.request.UpdateMeetupParticipantStatusRequest;
import app.mockly.domain.meetup.entity.*;
import app.mockly.domain.meetup.repository.LocalInterviewMeetupParticipantRepository;
import app.mockly.domain.meetup.repository.LocalInterviewMeetupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional
class LocalInterviewMeetupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocalInterviewMeetupRepository meetupRepository;

    @Autowired
    private LocalInterviewMeetupParticipantRepository participantRepository;

    private static final LocalInterviewMeetupParticipant.ProfileSnapshot HOST_PROFILE =
            new LocalInterviewMeetupParticipant.ProfileSnapshot(
                    MeetupJobCategory.DEVELOPER,
                    MeetupJobRole.BACKEND,
                    5,
                    MeetupExperienceLevel.MID
            );

    private static final LocalInterviewMeetupParticipant.ProfileSnapshot APPLICANT_PROFILE =
            new LocalInterviewMeetupParticipant.ProfileSnapshot(
                    MeetupJobCategory.DEVELOPER,
                    MeetupJobRole.FRONTEND,
                    2,
                    MeetupExperienceLevel.JUNIOR
            );

    private static final LocalInterviewMeetupParticipant.ProfileSnapshot THIRD_PROFILE =
            new LocalInterviewMeetupParticipant.ProfileSnapshot(
                    MeetupJobCategory.DEVELOPER,
                    MeetupJobRole.AI_ML,
                    0,
                    MeetupExperienceLevel.ENTRY
            );

    private User hostUser;
    private String hostToken;
    private User applicantUser;
    private String applicantToken;
    private User thirdUser;
    private String thirdToken;

    @BeforeEach
    void setUp() {
        given(tokenBlacklistService.isBlacklisted(anyString())).willReturn(false);

        hostUser = userRepository.save(User.builder()
                .email("host@mockly.app")
                .name("방장메이트")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("google-host")
                .build());
        hostToken = jwtService.generateAccessToken(hostUser.getId());

        applicantUser = userRepository.save(User.builder()
                .email("applicant@mockly.app")
                .name("신청자나무")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("google-applicant")
                .build());
        applicantToken = jwtService.generateAccessToken(applicantUser.getId());

        thirdUser = userRepository.save(User.builder()
                .email("third@mockly.app")
                .name("세번째지원자")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("google-third")
                .build());
        thirdToken = jwtService.generateAccessToken(thirdUser.getId());
    }

    @Test
    @DisplayName("GET /api/local-interview-meetups - 성공: 반경 검색 시 거리순 정렬 및 distanceMeters 반환")
    void searchMeetups_radius_success() throws Exception {
        Instant now = Instant.now();
        saveMeetupWithHost(
                "연남동 백엔드 기술면접 스터디",
                37.5626, 126.9247,
                now.plus(2, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.DAYS),
                4
        );
        saveMeetupWithHost(
                "신촌역 백엔드 설계 면접",
                37.5552, 126.9369,
                now.plus(3, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                4
        );
        // 이미 시작 시각이 지난 모임은 탐색에서 제외됨
        saveMeetupWithHost(
                "지난 연남동 모임",
                37.5626, 126.9247,
                now.minus(1, ChronoUnit.HOURS),
                now.minus(3, ChronoUnit.HOURS),
                4
        );

        mockMvc.perform(get("/api/local-interview-meetups")
                        .header("Authorization", "Bearer " + applicantToken)
                        .param("latitude", "37.5626")
                        .param("longitude", "126.9247")
                        .param("radiusMeters", "3000")
                        .param("jobCategory", "DEVELOPER")
                        .param("jobRole", "BACKEND")
                        .param("interviewType", "TECHNICAL")
                        .param("experienceLevel", "JUNIOR")
                        .param("sort", "DISTANCE")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.meetups.length()").value(2))
                .andExpect(jsonPath("$.data.meetups[0].title").value("연남동 백엔드 기술면접 스터디"))
                .andExpect(jsonPath("$.data.meetups[0].distanceMeters").value(0))
                .andExpect(jsonPath("$.data.meetups[1].distanceMeters").isNumber())
                .andExpect(jsonPath("$.data.pagination.page").value(1))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2))
                .andExpect(jsonPath("$.data.pagination.hasNext").value(false))
                .andDo(document("meetup-search",
                        resource(LocalInterviewMeetupDocs.searchSuccess())
                ));
    }

    @Test
    @DisplayName("GET /api/local-interview-meetups - 성공: DB 레벨 Haversine 반경 필터링(바운딩박스 모서리 제외) 및 페이지네이션 정확성, size 상한 검증")
    void searchMeetups_dbPaginationAndRadiusExclusion() throws Exception {
        Instant now = Instant.now();
        // 1. 중심점 (0m)
        saveMeetupWithHost(
                "연남동 0m 모임",
                37.5626, 126.9247,
                now.plus(2, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.DAYS),
                4
        );
        // 2. 반경 내 (~1,353m)
        saveMeetupWithHost(
                "신촌역 1.3km 모임",
                37.5552, 126.9369,
                now.plus(3, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                4
        );
        // 3. 바운딩박스 내부 모서리지만 원형 반경(3000m) 바깥인 지점 (~3,650m -> DB Haversine 조건에서 제외되어야 함)
        saveMeetupWithHost(
                "바운딩박스 모서리 반경 밖 모임",
                37.5868, 126.9525,
                now.plus(4, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                4
        );

        // page 1 (size=1) -> totalElements=2, totalPages=2, hasNext=true
        mockMvc.perform(get("/api/local-interview-meetups")
                        .header("Authorization", "Bearer " + applicantToken)
                        .param("latitude", "37.5626")
                        .param("longitude", "126.9247")
                        .param("radiusMeters", "3000")
                        .param("sort", "DISTANCE")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetups.length()").value(1))
                .andExpect(jsonPath("$.data.meetups[0].title").value("연남동 0m 모임"))
                .andExpect(jsonPath("$.data.pagination.page").value(1))
                .andExpect(jsonPath("$.data.pagination.size").value(1))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2))
                .andExpect(jsonPath("$.data.pagination.totalPages").value(2))
                .andExpect(jsonPath("$.data.pagination.hasNext").value(true));

        // page 2 (size=1) -> 두 번째 모임 반환, hasNext=false
        mockMvc.perform(get("/api/local-interview-meetups")
                        .header("Authorization", "Bearer " + applicantToken)
                        .param("latitude", "37.5626")
                        .param("longitude", "126.9247")
                        .param("radiusMeters", "3000")
                        .param("sort", "DISTANCE")
                        .param("page", "2")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetups.length()").value(1))
                .andExpect(jsonPath("$.data.meetups[0].title").value("신촌역 1.3km 모임"))
                .andExpect(jsonPath("$.data.pagination.page").value(2))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2))
                .andExpect(jsonPath("$.data.pagination.totalPages").value(2))
                .andExpect(jsonPath("$.data.pagination.hasNext").value(false));

        // size > 50 요청 시 400 VALIDATION_ERROR
        mockMvc.perform(get("/api/local-interview-meetups")
                        .header("Authorization", "Bearer " + applicantToken)
                        .param("latitude", "37.5626")
                        .param("longitude", "126.9247")
                        .param("radiusMeters", "3000")
                        .param("page", "1")
                        .param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/local-interview-meetups - 성공: 지도 영역(bounds) 검색 시 distanceMeters는 null")
    void searchMeetups_bounds_success() throws Exception {
        Instant now = Instant.now();
        saveMeetupWithHost(
                "합정역 모의면접",
                37.5496, 126.9139,
                now.plus(2, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.DAYS),
                4
        );

        mockMvc.perform(get("/api/local-interview-meetups")
                        .header("Authorization", "Bearer " + applicantToken)
                        .param("swLat", "37.5400")
                        .param("swLng", "126.9000")
                        .param("neLat", "37.5700")
                        .param("neLng", "126.9500")
                        .param("sort", "STARTS_AT")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.meetups.length()").value(1))
                .andExpect(jsonPath("$.data.meetups[0].distanceMeters").value(nullValue()))
                .andDo(document("meetup-search-bounds",
                        resource(LocalInterviewMeetupDocs.searchBoundsSuccess())
                ));
    }

    @Test
    @DisplayName("GET /api/local-interview-meetups - 실패: 반경과 영역 파라미터 동시 전달 시 400 VALIDATION_ERROR")
    void searchMeetups_xorViolation_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/local-interview-meetups")
                        .header("Authorization", "Bearer " + applicantToken)
                        .param("latitude", "37.5626")
                        .param("longitude", "126.9247")
                        .param("swLat", "37.5400")
                        .param("swLng", "126.9000")
                        .param("neLat", "37.5700")
                        .param("neLng", "126.9500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andDo(document("meetup-search-validation-error",
                        resource(LocalInterviewMeetupDocs.searchValidationError())
                ));
    }

    @Test
    @DisplayName("GET /api/local-interview-meetups/:meetupId - 성공: 미승인 사용자는 fullAddress가 null, 방장/승인 참여자는 상세 주소 공개")
    void getMeetupDetail_addressPrivacyAndHostVisibility() throws Exception {
        Instant now = Instant.now();
        LocalInterviewMeetup meetup = saveMeetupWithHost(
                "연남동 스터디룸 기술면접",
                37.5626, 126.9247,
                now.plus(3, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                4
        );

        // 1. 미참여 제3자 조회 -> fullAddress == null, myParticipationStatus == null, amIHost == false
        mockMvc.perform(get("/api/local-interview-meetups/{meetupId}", meetup.getId())
                        .header("Authorization", "Bearer " + thirdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullAddress").value(nullValue()))
                .andExpect(jsonPath("$.data.myParticipationStatus").value(nullValue()))
                .andExpect(jsonPath("$.data.amIHost").value(false));

        // 2. 신청 후 PENDING 상태 -> 여전히 fullAddress == null
        LocalInterviewMeetupParticipant pendingApplicant = participantRepository.saveAndFlush(
                LocalInterviewMeetupParticipant.createApplicant(
                        meetup, applicantUser, "열심히 참여하겠습니다!", APPLICANT_PROFILE, now)
        );
        mockMvc.perform(get("/api/local-interview-meetups/{meetupId}", meetup.getId())
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullAddress").value(nullValue()))
                .andExpect(jsonPath("$.data.myParticipationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.amIHost").value(false));

        // 3. APPROVED 승인 후 -> fullAddress 공개!
        pendingApplicant.approve(now);
        meetup.incrementParticipants();
        participantRepository.saveAndFlush(pendingApplicant);
        meetupRepository.saveAndFlush(meetup);

        mockMvc.perform(get("/api/local-interview-meetups/{meetupId}", meetup.getId())
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.meetupId").value(meetup.getId().toString()))
                .andExpect(jsonPath("$.data.fullAddress").value("서울 마포구 동교로 240 3층 302호"))
                .andExpect(jsonPath("$.data.myParticipationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.amIHost").value(false))
                .andExpect(jsonPath("$.data.isFull").value(false))
                .andExpect(jsonPath("$.data.participantSummaries.length()").value(2))
                .andDo(document("meetup-detail",
                        resource(LocalInterviewMeetupDocs.detailSuccess())
                ));
    }

    @Test
    @DisplayName("GET /api/local-interview-meetups/:meetupId - 실패: 존재하지 않는 모임 (404)")
    void getMeetupDetail_notFound() throws Exception {
        mockMvc.perform(get("/api/local-interview-meetups/{meetupId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andDo(document("meetup-detail-not-found",
                        resource(LocalInterviewMeetupDocs.detailNotFound())
                ));
    }

    @Test
    @DisplayName("POST /api/local-interview-meetups - 성공: 생성자가 HOST + APPROVED로 자동 등록되고 currentParticipants=1 (201)")
    void createMeetup_success() throws Exception {
        Instant startsAt = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        CreateLocalInterviewMeetupRequest request = new CreateLocalInterviewMeetupRequest(
                "강남역 주니어 백엔드 모의면접",
                "Spring Boot, JPA 기술 질문 위주로 90분간 진행합니다.",
                MeetupJobCategory.DEVELOPER,
                MeetupJobRole.BACKEND,
                MeetupInterviewType.TECHNICAL,
                MeetupExperienceLevel.JUNIOR,
                MeetupLocationStatus.PLACE_CONFIRMED,
                MeetupMeetingType.OFFLINE,
                "강남역 스터디룸 A",
                "역삼동",
                "서울 강남구 테헤란로 101 4층",
                37.4980,
                127.0276,
                startsAt,
                startsAt.plus(90, ChronoUnit.MINUTES),
                startsAt.minus(6, ChronoUnit.HOURS),
                4,
                MeetupJobCategory.DEVELOPER,
                MeetupJobRole.BACKEND,
                6,
                MeetupExperienceLevel.SENIOR
        );

        String responseBody = mockMvc.perform(post("/api/local-interview-meetups")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.meetupId").isNotEmpty())
                .andDo(document("meetup-create",
                        resource(LocalInterviewMeetupDocs.createSuccess())
                ))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String createdId = objectMapper.readTree(responseBody).path("data").path("meetupId").asText();
        LocalInterviewMeetup saved = meetupRepository.findById(UUID.fromString(createdId)).orElseThrow();
        assertThat(saved.getCurrentParticipants()).isEqualTo(1);

        LocalInterviewMeetupParticipant hostParticipant = participantRepository
                .findByMeetupIdAndUserId(saved.getId(), hostUser.getId())
                .orElseThrow();
        assertThat(hostParticipant.isHost()).isTrue();
        assertThat(hostParticipant.getStatus()).isEqualTo(MeetupParticipationStatus.APPROVED);
        assertThat(hostParticipant.getExperienceYears()).isEqualTo(6);
        assertThat(hostParticipant.getExperienceLevel()).isEqualTo(MeetupExperienceLevel.SENIOR);
    }

    @Test
    @DisplayName("참여자 스냅샷 격리: 모임 모집 대상(JUNIOR)과 무관하게 방장·신청자 각자의 직무·연차·레벨 스냅샷이 보존됨")
    void participantProfileSnapshot_preservedIndependentlyFromMeetupTarget() throws Exception {
        Instant startsAt = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        // 방장: 모임 대상은 BACKEND + JUNIOR이지만, 방장 본인은 BACKEND 7년차(SENIOR 자동 파생)
        CreateLocalInterviewMeetupRequest createRequest = new CreateLocalInterviewMeetupRequest(
                "직무 혼합 기술면접 스터디",
                "서로 다른 연차와 직무 관점에서 피드백을 주고받습니다.",
                MeetupJobCategory.DEVELOPER,
                MeetupJobRole.BACKEND,
                MeetupInterviewType.TECHNICAL,
                MeetupExperienceLevel.JUNIOR,
                MeetupLocationStatus.PLACE_CONFIRMED,
                MeetupMeetingType.OFFLINE,
                "강남 스터디룸",
                "역삼동",
                "서울 강남구 테헤란로 101",
                37.4980,
                127.0276,
                startsAt,
                startsAt.plus(90, ChronoUnit.MINUTES),
                startsAt.minus(6, ChronoUnit.HOURS),
                4,
                MeetupJobCategory.DEVELOPER,
                MeetupJobRole.BACKEND,
                7,
                null
        );

        String createBody = mockMvc.perform(post("/api/local-interview-meetups")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String meetupId = objectMapper.readTree(createBody).path("data").path("meetupId").asText();

        // 신청자 1: FRONTEND 4년차(MID 자동 파생)로 신청
        CreateMeetupParticipantRequest applicantRequest = new CreateMeetupParticipantRequest(
                "프론트엔드 4년차 지원합니다.",
                MeetupJobCategory.DEVELOPER,
                MeetupJobRole.FRONTEND,
                4,
                null
        );
        mockMvc.perform(post("/api/local-interview-meetups/{meetupId}/participants", meetupId)
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(applicantRequest)))
                .andExpect(status().isOk());

        // 신청자 2: 프로필 필드 생략(하위 호환 요청) -> 모임의 JUNIOR(1년)를 복사하지 않고 0년차/ENTRY로 기본 기록
        mockMvc.perform(post("/api/local-interview-meetups/{meetupId}/participants", meetupId)
                        .header("Authorization", "Bearer " + thirdToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateMeetupParticipantRequest("신입 지원합니다."))))
                .andExpect(status().isOk());

        // 신청자 1 승인
        mockMvc.perform(patch("/api/local-interview-meetups/{meetupId}/participants/{userId}",
                        meetupId, applicantUser.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateMeetupParticipantStatusRequest(MeetupParticipationStatus.APPROVED))))
                .andExpect(status().isOk());

        // 방장용 전체 참여자 목록 검증: 방장(7년/SENIOR), 신청자1(FRONTEND/4년/MID), 신청자2(0년/ENTRY)
        mockMvc.perform(get("/api/local-interview-meetups/{meetupId}/participants", meetupId)
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participants.length()").value(3))
                .andExpect(jsonPath("$.data.participants[0].isHost").value(true))
                .andExpect(jsonPath("$.data.participants[0].jobRole").value("BACKEND"))
                .andExpect(jsonPath("$.data.participants[0].experienceYears").value(7))
                .andExpect(jsonPath("$.data.participants[0].experienceLevel").value("SENIOR"))
                .andExpect(jsonPath("$.data.participants[1].jobRole").value("FRONTEND"))
                .andExpect(jsonPath("$.data.participants[1].experienceYears").value(4))
                .andExpect(jsonPath("$.data.participants[1].experienceLevel").value("MID"))
                .andExpect(jsonPath("$.data.participants[2].experienceYears").value(0))
                .andExpect(jsonPath("$.data.participants[2].experienceLevel").value("ENTRY"));

        // 모임 상세의 participantSummaries(승인 참여자) 검증
        mockMvc.perform(get("/api/local-interview-meetups/{meetupId}", meetupId)
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.experienceLevel").value("JUNIOR"))
                .andExpect(jsonPath("$.data.participantSummaries.length()").value(2))
                .andExpect(jsonPath("$.data.participantSummaries[0].experienceYears").value(7))
                .andExpect(jsonPath("$.data.participantSummaries[0].experienceLevel").value("SENIOR"))
                .andExpect(jsonPath("$.data.participantSummaries[1].jobRole").value("FRONTEND"))
                .andExpect(jsonPath("$.data.participantSummaries[1].experienceYears").value(4))
                .andExpect(jsonPath("$.data.participantSummaries[1].experienceLevel").value("MID"));
    }

    @Test
    @DisplayName("POST /api/local-interview-meetups - 실패: recruitDeadline이 startsAt 이후면 400 VALIDATION_ERROR")
    void createMeetup_invalidDeadline_returnsBadRequest() throws Exception {
        Instant startsAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        CreateLocalInterviewMeetupRequest request = new CreateLocalInterviewMeetupRequest(
                "잘못된 마감 시각 모임",
                "설명",
                MeetupJobCategory.DEVELOPER,
                MeetupJobRole.BACKEND,
                MeetupInterviewType.TECHNICAL,
                MeetupExperienceLevel.JUNIOR,
                MeetupLocationStatus.PLACE_CONFIRMED,
                MeetupMeetingType.OFFLINE,
                "장소",
                "역삼동",
                "서울 강남구 테헤란로 1",
                37.4980,
                127.0276,
                startsAt,
                startsAt.plus(90, ChronoUnit.MINUTES),
                startsAt.plus(1, ChronoUnit.HOURS),
                4
        );

        mockMvc.perform(post("/api/local-interview-meetups")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andDo(document("meetup-create-validation-error",
                        resource(LocalInterviewMeetupDocs.createValidationError())
                ));
    }

    @Test
    @DisplayName("GET /api/local-interview-meetups/me - 성공: 방장 모임의 pendingApplicantCount와 참여 모임을 함께 반환")
    void getMyMeetups_success() throws Exception {
        Instant now = Instant.now();
        LocalInterviewMeetup hostedMeetup = saveMeetupWithHost(
                "내가 만든 연남동 모임",
                37.5626, 126.9247,
                now.plus(2, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.DAYS),
                4
        );
        participantRepository.saveAndFlush(
                LocalInterviewMeetupParticipant.createApplicant(
                        hostedMeetup, applicantUser, "신청합니다", APPLICANT_PROFILE, now)
        );

        mockMvc.perform(get("/api/local-interview-meetups/me")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.meetups.length()").value(1))
                .andExpect(jsonPath("$.data.meetups[0].role").value("HOST"))
                .andExpect(jsonPath("$.data.meetups[0].myParticipationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.meetups[0].pendingApplicantCount").value(1))
                .andExpect(jsonPath("$.data.meetups[0].endsAt").isNotEmpty())
                .andDo(document("meetup-mine",
                        resource(LocalInterviewMeetupDocs.myMeetupsSuccess())
                ));
    }

    @Test
    @DisplayName("POST /api/local-interview-meetups/:meetupId/participants - 성공 및 중복/본인/마감 검증, 취소 후 재신청 허용")
    void joinMeetup_flows() throws Exception {
        Instant now = Instant.now();
        LocalInterviewMeetup meetup = saveMeetupWithHost(
                "합정동 프론트엔드 스터디",
                37.5496, 126.9139,
                now.plus(2, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.DAYS),
                4
        );

        // 1. 정상 신청 (200)
        CreateMeetupParticipantRequest joinRequest = new CreateMeetupParticipantRequest(
                "함께 연습해요!",
                MeetupJobCategory.DEVELOPER,
                MeetupJobRole.FRONTEND,
                2,
                MeetupExperienceLevel.JUNIOR
        );
        mockMvc.perform(post("/api/local-interview-meetups/{meetupId}/participants", meetup.getId())
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andDo(document("meetup-join",
                        resource(LocalInterviewMeetupDocs.joinSuccess())
                ));

        // 2. 중복 신청 시 400 DUPLICATE_RESOURCE
        mockMvc.perform(post("/api/local-interview-meetups/{meetupId}/participants", meetup.getId())
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateMeetupParticipantRequest("중복 신청"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"))
                .andDo(document("meetup-join-duplicate",
                        resource(LocalInterviewMeetupDocs.joinDuplicate())
                ));

        // 3. 방장 본인 모임 신청 시 400 VALIDATION_ERROR
        mockMvc.perform(post("/api/local-interview-meetups/{meetupId}/participants", meetup.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateMeetupParticipantRequest("방장 신청"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andDo(document("meetup-join-validation-error",
                        resource(LocalInterviewMeetupDocs.joinValidationError())
                ));

        // 4. 신청 취소 후 재신청 가능 검증
        mockMvc.perform(delete("/api/local-interview-meetups/{meetupId}/participants/me", meetup.getId())
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/local-interview-meetups/{meetupId}/participants", meetup.getId())
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateMeetupParticipantRequest("다시 신청합니다!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/local-interview-meetups/:meetupId/participants/me - 승인 참여자 취소 시 currentParticipants 감소 및 방장 탈퇴 차단")
    void leaveMeetup_decrementsApprovedCountAndBlocksHost() throws Exception {
        Instant now = Instant.now();
        LocalInterviewMeetup meetup = saveMeetupWithHost(
                "신촌 면접 스터디",
                37.5552, 126.9369,
                now.plus(2, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.DAYS),
                4
        );
        LocalInterviewMeetupParticipant approvedApplicant = LocalInterviewMeetupParticipant
                .createApplicant(meetup, applicantUser, "참여", APPLICANT_PROFILE, now);
        approvedApplicant.approve(now);
        meetup.incrementParticipants();
        participantRepository.saveAndFlush(approvedApplicant);
        meetupRepository.saveAndFlush(meetup);
        assertThat(meetup.getCurrentParticipants()).isEqualTo(2);

        // 승인된 참여자 취소 -> currentParticipants 1로 감소
        mockMvc.perform(delete("/api/local-interview-meetups/{meetupId}/participants/me", meetup.getId())
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andDo(document("meetup-leave",
                        resource(LocalInterviewMeetupDocs.leaveSuccess())
                ));

        assertThat(meetupRepository.findById(meetup.getId()).orElseThrow().getCurrentParticipants()).isEqualTo(1);

        // 방장 탈퇴 시도 -> 400 VALIDATION_ERROR
        mockMvc.perform(delete("/api/local-interview-meetups/{meetupId}/participants/me", meetup.getId())
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andDo(document("meetup-leave-host-forbidden",
                        resource(LocalInterviewMeetupDocs.leaveValidationError())
                ));
    }

    @Test
    @DisplayName("GET /api/local-interview-meetups/:meetupId/participants - 방장만 조회 가능하며 비방장은 403 FORBIDDEN")
    void getMeetupApplicants_hostOnly() throws Exception {
        Instant now = Instant.now();
        LocalInterviewMeetup meetup = saveMeetupWithHost(
                "종각역 스터디",
                37.5702, 126.9831,
                now.plus(2, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.DAYS),
                4
        );
        participantRepository.saveAndFlush(
                LocalInterviewMeetupParticipant.createApplicant(
                        meetup, applicantUser, "꼭 참여하고 싶습니다.", APPLICANT_PROFILE, now)
        );

        mockMvc.perform(get("/api/local-interview-meetups/{meetupId}/participants", meetup.getId())
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.participants.length()").value(2))
                .andExpect(jsonPath("$.data.participants[0].isHost").value(true))
                .andExpect(jsonPath("$.data.participants[1].status").value("PENDING"))
                .andDo(document("meetup-applicants",
                        resource(LocalInterviewMeetupDocs.applicantsSuccess())
                ));

        mockMvc.perform(get("/api/local-interview-meetups/{meetupId}/participants", meetup.getId())
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andDo(document("meetup-applicants-forbidden",
                        resource(LocalInterviewMeetupDocs.applicantsForbidden())
                ));
    }

    @Test
    @DisplayName("PATCH /api/local-interview-meetups/:meetupId/participants/:userId - 방장 승인 시 정원 증가, 정원 마감 시 초과 승인 차단 및 비방장 403")
    void reviewApplicant_approveCapacityAndPermissions() throws Exception {
        Instant now = Instant.now();
        LocalInterviewMeetup meetup = saveMeetupWithHost(
                "정원 2명 소규모 모의면접",
                37.5626, 126.9247,
                now.plus(2, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.DAYS),
                2
        );
        participantRepository.saveAndFlush(
                LocalInterviewMeetupParticipant.createApplicant(
                        meetup, applicantUser, "첫 신청자", APPLICANT_PROFILE, now)
        );
        participantRepository.saveAndFlush(
                LocalInterviewMeetupParticipant.createApplicant(
                        meetup, thirdUser, "두 번째 신청자", THIRD_PROFILE, now)
        );

        // 1. 비방장 승인 시도 -> 403 FORBIDDEN
        mockMvc.perform(patch("/api/local-interview-meetups/{meetupId}/participants/{userId}",
                        meetup.getId(), applicantUser.getId())
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateMeetupParticipantStatusRequest(MeetupParticipationStatus.APPROVED))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andDo(document("meetup-review-applicant-forbidden",
                        resource(LocalInterviewMeetupDocs.reviewApplicantForbidden())
                ));

        // 2. 방장이 첫 신청자 승인 -> currentParticipants 1 -> 2 (정원 도달)
        mockMvc.perform(patch("/api/local-interview-meetups/{meetupId}/participants/{userId}",
                        meetup.getId(), applicantUser.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateMeetupParticipantStatusRequest(MeetupParticipationStatus.APPROVED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andDo(document("meetup-review-applicant",
                        resource(LocalInterviewMeetupDocs.reviewApplicantSuccess())
                ));

        assertThat(meetupRepository.findById(meetup.getId()).orElseThrow().getCurrentParticipants()).isEqualTo(2);

        // 3. 정원이 가득 찬 상태에서 두 번째 신청자 추가 승인 시도 -> 400 VALIDATION_ERROR
        mockMvc.perform(patch("/api/local-interview-meetups/{meetupId}/participants/{userId}",
                        meetup.getId(), thirdUser.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateMeetupParticipantStatusRequest(MeetupParticipationStatus.APPROVED))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andDo(document("meetup-review-applicant-full",
                        resource(LocalInterviewMeetupDocs.reviewApplicantFull())
                ));

        // 4. 거절(REJECTED)은 정원이 찬 상태에서도 정상 처리 가능
        mockMvc.perform(patch("/api/local-interview-meetups/{meetupId}/participants/{userId}",
                        meetup.getId(), thirdUser.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateMeetupParticipantStatusRequest(MeetupParticipationStatus.REJECTED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private LocalInterviewMeetup saveMeetupWithHost(
            String title,
            double latitude,
            double longitude,
            Instant startsAt,
            Instant recruitDeadline,
            int maxParticipants
    ) {
        LocalInterviewMeetup meetup = meetupRepository.saveAndFlush(LocalInterviewMeetup.builder()
                .host(hostUser)
                .title(title)
                .description("모의면접 스터디 설명입니다.")
                .jobCategory(MeetupJobCategory.DEVELOPER)
                .jobRole(MeetupJobRole.BACKEND)
                .interviewType(MeetupInterviewType.TECHNICAL)
                .experienceLevel(MeetupExperienceLevel.JUNIOR)
                .locationStatus(MeetupLocationStatus.PLACE_CONFIRMED)
                .meetingType(MeetupMeetingType.OFFLINE)
                .placeName("연남동 스터디카페")
                .addressLabel("연남동")
                .fullAddress("서울 마포구 동교로 240 3층 302호")
                .latitude(latitude)
                .longitude(longitude)
                .startsAt(startsAt)
                .endsAt(startsAt.plus(90, ChronoUnit.MINUTES))
                .recruitDeadline(recruitDeadline)
                .currentParticipants(1)
                .maxParticipants(maxParticipants)
                .build());
        participantRepository.saveAndFlush(
                LocalInterviewMeetupParticipant.createHost(meetup, hostUser, HOST_PROFILE, Instant.now())
        );
        return meetup;
    }
}
