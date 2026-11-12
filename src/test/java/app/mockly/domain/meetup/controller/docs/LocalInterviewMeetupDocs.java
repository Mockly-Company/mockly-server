package app.mockly.domain.meetup.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.HeaderDescriptorWithType;
import com.epages.restdocs.apispec.ParameterDescriptorWithType;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class LocalInterviewMeetupDocs {

    private static final String TAG = "LocalInterviewMeetup";

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    private static final List<ParameterDescriptorWithType> SEARCH_QUERY_PARAMETERS = List.of(
            parameterWithName("latitude").description("반경 검색 중심 위도 (bounds 검색과 배타적)").type(SimpleType.NUMBER).optional(),
            parameterWithName("longitude").description("반경 검색 중심 경도 (bounds 검색과 배타적)").type(SimpleType.NUMBER).optional(),
            parameterWithName("radiusMeters").description("반경 검색 거리(m, 기본값 3000, 최대 50000)").type(SimpleType.NUMBER).optional(),
            parameterWithName("swLat").description("영역 검색 남서쪽 위도").type(SimpleType.NUMBER).optional(),
            parameterWithName("swLng").description("영역 검색 남서쪽 경도").type(SimpleType.NUMBER).optional(),
            parameterWithName("neLat").description("영역 검색 북동쪽 위도").type(SimpleType.NUMBER).optional(),
            parameterWithName("neLng").description("영역 검색 북동쪽 경도").type(SimpleType.NUMBER).optional(),
            parameterWithName("jobCategory").description("직군 필터 (DEVELOPER | DESIGNER | PRODUCT_MANAGER)").type(SimpleType.STRING).optional(),
            parameterWithName("jobRole").description("직무 필터 (BACKEND | FRONTEND | AI_ML | DATA_ENGINEERING)").type(SimpleType.STRING).optional(),
            parameterWithName("interviewType").description("면접 유형 필터 (TECHNICAL | CULTURE_FIT | PORTFOLIO)").type(SimpleType.STRING).optional(),
            parameterWithName("experienceLevel").description("경력 수준 필터 (ENTRY | JUNIOR | MID | SENIOR)").type(SimpleType.STRING).optional(),
            parameterWithName("sort").description("정렬 기준 (DISTANCE | STARTS_AT)").type(SimpleType.STRING).optional(),
            parameterWithName("page").description("페이지 번호 (1부터 시작, 기본값 1)").type(SimpleType.NUMBER).optional(),
            parameterWithName("size").description("페이지 크기 (기본값 20)").type(SimpleType.NUMBER).optional()
    );

    private static final List<FieldDescriptor> SEARCH_RESPONSE_FIELDS = List.of(
            fieldWithPath("meetups").description("검색된 동네면접 모임 목록").type(JsonFieldType.ARRAY),
            fieldWithPath("meetups[].meetupId").description("모임 ID (UUID)").type(SimpleType.STRING),
            fieldWithPath("meetups[].title").description("모임 제목").type(SimpleType.STRING),
            fieldWithPath("meetups[].jobCategory").description("직군").type(SimpleType.STRING),
            fieldWithPath("meetups[].jobRole").description("직무").type(SimpleType.STRING),
            fieldWithPath("meetups[].interviewType").description("면접 유형").type(SimpleType.STRING),
            fieldWithPath("meetups[].experienceLevel").description("경력 수준").type(SimpleType.STRING),
            fieldWithPath("meetups[].startsAt").description("모임 시작 시각 (ISO-8601)").type(SimpleType.STRING),
            fieldWithPath("meetups[].distanceMeters").description("중심점으로부터의 거리(m, 영역 검색 시 null)").type(SimpleType.NUMBER).optional(),
            fieldWithPath("meetups[].currentParticipants").description("현재 승인된 참여 인원").type(SimpleType.NUMBER),
            fieldWithPath("meetups[].maxParticipants").description("최대 정원 (2~8)").type(SimpleType.NUMBER),
            fieldWithPath("meetups[].placeName").description("장소명").type(SimpleType.STRING),
            fieldWithPath("meetups[].addressLabel").description("동 단위 공개 주소 라벨").type(SimpleType.STRING),
            fieldWithPath("meetups[].latitude").description("위도").type(SimpleType.NUMBER),
            fieldWithPath("meetups[].longitude").description("경도").type(SimpleType.NUMBER),
            fieldWithPath("pagination").description("페이지네이션 정보").type(JsonFieldType.OBJECT),
            fieldWithPath("pagination.page").description("현재 페이지 번호 (1-based)").type(SimpleType.NUMBER),
            fieldWithPath("pagination.size").description("페이지 크기").type(SimpleType.NUMBER),
            fieldWithPath("pagination.totalElements").description("전체 결과 수").type(SimpleType.NUMBER),
            fieldWithPath("pagination.totalPages").description("전체 페이지 수").type(SimpleType.NUMBER),
            fieldWithPath("pagination.hasNext").description("다음 페이지 존재 여부").type(SimpleType.BOOLEAN)
    );

    private static final List<FieldDescriptor> DETAIL_RESPONSE_FIELDS = List.of(
            fieldWithPath("meetupId").description("모임 ID (UUID)").type(SimpleType.STRING),
            fieldWithPath("title").description("모임 제목").type(SimpleType.STRING),
            fieldWithPath("description").description("모임 상세 소개").type(SimpleType.STRING),
            fieldWithPath("jobCategory").description("직군").type(SimpleType.STRING),
            fieldWithPath("jobRole").description("직무").type(SimpleType.STRING),
            fieldWithPath("interviewType").description("면접 유형").type(SimpleType.STRING),
            fieldWithPath("experienceLevel").description("경력 수준").type(SimpleType.STRING),
            fieldWithPath("startsAt").description("모임 시작 시각 (ISO-8601)").type(SimpleType.STRING),
            fieldWithPath("endsAt").description("모임 종료 시각 (ISO-8601)").type(SimpleType.STRING),
            fieldWithPath("currentParticipants").description("현재 승인된 참여 인원").type(SimpleType.NUMBER),
            fieldWithPath("maxParticipants").description("최대 정원").type(SimpleType.NUMBER),
            fieldWithPath("placeName").description("장소명").type(SimpleType.STRING),
            fieldWithPath("fullAddress").description("상세 주소 (방장 및 APPROVED 참여자에게만 노출, 미승인 시 null)").type(SimpleType.STRING).optional(),
            fieldWithPath("distanceMeters").description("거리(m, 미전달 시 null)").type(SimpleType.NUMBER).optional(),
            fieldWithPath("latitude").description("위도").type(SimpleType.NUMBER),
            fieldWithPath("longitude").description("경도").type(SimpleType.NUMBER),
            fieldWithPath("participantSummaries").description("승인된 참여자 요약 목록").type(JsonFieldType.ARRAY),
            fieldWithPath("participantSummaries[].nickname").description("닉네임").type(SimpleType.STRING),
            fieldWithPath("participantSummaries[].isHost").description("방장 여부").type(SimpleType.BOOLEAN),
            fieldWithPath("participantSummaries[].jobCategory").description("직군").type(SimpleType.STRING),
            fieldWithPath("participantSummaries[].jobRole").description("직무").type(SimpleType.STRING),
            fieldWithPath("participantSummaries[].experienceYears").description("경력 연차").type(SimpleType.NUMBER),
            fieldWithPath("participantSummaries[].experienceLevel").description("경력 수준").type(SimpleType.STRING),
            fieldWithPath("isFull").description("정원 마감 여부").type(SimpleType.BOOLEAN),
            fieldWithPath("createdAt").description("모임 개설 시각 (ISO-8601)").type(SimpleType.STRING),
            fieldWithPath("recruitDeadline").description("모집 마감 시각 (ISO-8601)").type(SimpleType.STRING),
            fieldWithPath("myParticipationStatus").description("내 참여 상태 (PENDING | APPROVED | REJECTED | CANCELLED | null)").type(SimpleType.STRING).optional(),
            fieldWithPath("amIHost").description("로그인 사용자의 방장 여부").type(SimpleType.BOOLEAN)
    );

    private static final List<FieldDescriptor> CREATE_REQUEST_FIELDS = List.of(
            fieldWithPath("title").description("모임 제목 (최대 100자)").type(SimpleType.STRING),
            fieldWithPath("description").description("모임 설명").type(SimpleType.STRING),
            fieldWithPath("jobCategory").description("직군 (DEVELOPER | DESIGNER | PRODUCT_MANAGER)").type(SimpleType.STRING),
            fieldWithPath("jobRole").description("직무 (BACKEND | FRONTEND | AI_ML | DATA_ENGINEERING)").type(SimpleType.STRING),
            fieldWithPath("interviewType").description("면접 유형 (TECHNICAL | CULTURE_FIT | PORTFOLIO)").type(SimpleType.STRING),
            fieldWithPath("experienceLevel").description("경력 수준 (ENTRY | JUNIOR | MID | SENIOR)").type(SimpleType.STRING),
            fieldWithPath("locationStatus").description("장소 확정 상태 (PLACE_CONFIRMED)").type(SimpleType.STRING),
            fieldWithPath("meetingType").description("모임 형태 (OFFLINE)").type(SimpleType.STRING),
            fieldWithPath("placeName").description("장소명").type(SimpleType.STRING),
            fieldWithPath("addressLabel").description("동 단위 주소 라벨").type(SimpleType.STRING),
            fieldWithPath("fullAddress").description("참여 확정자에게 공개할 상세 주소 (선택)").type(SimpleType.STRING).optional(),
            fieldWithPath("latitude").description("위도").type(SimpleType.NUMBER),
            fieldWithPath("longitude").description("경도").type(SimpleType.NUMBER),
            fieldWithPath("startsAt").description("모임 시작 시각 (ISO-8601)").type(SimpleType.STRING),
            fieldWithPath("endsAt").description("모임 종료 시각 (ISO-8601, startsAt 이후)").type(SimpleType.STRING),
            fieldWithPath("recruitDeadline").description("모집 마감 시각 (ISO-8601, startsAt 이전 또는 동일)").type(SimpleType.STRING),
            fieldWithPath("maxParticipants").description("최대 정원 (2~8)").type(SimpleType.NUMBER)
    );

    private static final List<FieldDescriptor> CREATE_RESPONSE_FIELDS = List.of(
            fieldWithPath("meetupId").description("생성된 모임 ID (UUID)").type(SimpleType.STRING)
    );

    private static final List<FieldDescriptor> MY_MEETUPS_RESPONSE_FIELDS = List.of(
            fieldWithPath("meetups").description("내가 개설했거나 참여 신청한 모임 목록").type(JsonFieldType.ARRAY),
            fieldWithPath("meetups[].meetupId").description("모임 ID").type(SimpleType.STRING),
            fieldWithPath("meetups[].title").description("모임 제목").type(SimpleType.STRING),
            fieldWithPath("meetups[].jobCategory").description("직군").type(SimpleType.STRING),
            fieldWithPath("meetups[].jobRole").description("직무").type(SimpleType.STRING),
            fieldWithPath("meetups[].interviewType").description("면접 유형").type(SimpleType.STRING),
            fieldWithPath("meetups[].experienceLevel").description("경력 수준").type(SimpleType.STRING),
            fieldWithPath("meetups[].startsAt").description("시작 시각").type(SimpleType.STRING),
            fieldWithPath("meetups[].distanceMeters").description("거리(m, 내 모임 목록에서는 null)").type(SimpleType.NUMBER).optional(),
            fieldWithPath("meetups[].currentParticipants").description("현재 승인 인원").type(SimpleType.NUMBER),
            fieldWithPath("meetups[].maxParticipants").description("최대 정원").type(SimpleType.NUMBER),
            fieldWithPath("meetups[].placeName").description("장소명").type(SimpleType.STRING),
            fieldWithPath("meetups[].addressLabel").description("동 단위 주소 라벨").type(SimpleType.STRING),
            fieldWithPath("meetups[].latitude").description("위도").type(SimpleType.NUMBER),
            fieldWithPath("meetups[].longitude").description("경도").type(SimpleType.NUMBER),
            fieldWithPath("meetups[].role").description("내 역할 (HOST | PARTICIPANT)").type(SimpleType.STRING),
            fieldWithPath("meetups[].myParticipationStatus").description("내 참여 상태 (PENDING | APPROVED | REJECTED | CANCELLED)").type(SimpleType.STRING),
            fieldWithPath("meetups[].pendingApplicantCount").description("승인 대기 신청자 수 (방장인 경우 집계, 참여자는 0)").type(SimpleType.NUMBER),
            fieldWithPath("meetups[].endsAt").description("종료 시각").type(SimpleType.STRING)
    );

    private static final List<FieldDescriptor> JOIN_REQUEST_FIELDS = List.of(
            fieldWithPath("message").description("방장에게 남기는 한마디 (선택, 최대 300자)").type(SimpleType.STRING).optional()
    );

    private static final List<FieldDescriptor> APPLICANTS_RESPONSE_FIELDS = List.of(
            fieldWithPath("participants").description("모임 참여자 및 신청자 전체 목록").type(JsonFieldType.ARRAY),
            fieldWithPath("participants[].userId").description("사용자 ID (UUID)").type(SimpleType.STRING),
            fieldWithPath("participants[].nickname").description("닉네임").type(SimpleType.STRING),
            fieldWithPath("participants[].isHost").description("방장 여부").type(SimpleType.BOOLEAN),
            fieldWithPath("participants[].jobCategory").description("직군").type(SimpleType.STRING),
            fieldWithPath("participants[].jobRole").description("직무").type(SimpleType.STRING),
            fieldWithPath("participants[].experienceYears").description("경력 연차").type(SimpleType.NUMBER),
            fieldWithPath("participants[].experienceLevel").description("경력 수준").type(SimpleType.STRING),
            fieldWithPath("participants[].status").description("참여 상태 (PENDING | APPROVED | REJECTED | CANCELLED)").type(SimpleType.STRING),
            fieldWithPath("participants[].message").description("신청 시 남긴 한마디").type(SimpleType.STRING).optional(),
            fieldWithPath("participants[].appliedAt").description("신청 시각 (ISO-8601)").type(SimpleType.STRING)
    );

    private static final List<FieldDescriptor> REVIEW_REQUEST_FIELDS = List.of(
            fieldWithPath("status").description("심사 결과 (APPROVED | REJECTED)").type(SimpleType.STRING)
    );

    public static ResourceSnippetParameters searchSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .summary("동네면접 모임 검색 (반경 / 지도 영역)")
                .description("""
                        반경(latitude, longitude, radiusMeters) 또는 지도 영역(swLat, swLng, neLat, neLng) 중 하나를 전달해 아직 시작하지 않은 모임을 검색합니다.
                        두 검색 모드를 동시에 전달하거나 모두 생략하면 400 VALIDATION_ERROR를 반환합니다.
                        """)
                .requestHeaders(REQUEST_HEADERS)
                .queryParameters(SEARCH_QUERY_PARAMETERS)
                .responseFields(ApiResponseDocs.withDataFields(SEARCH_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters searchBoundsSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .queryParameters(SEARCH_QUERY_PARAMETERS)
                .responseFields(ApiResponseDocs.withDataFields(SEARCH_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters searchValidationError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .responseFields(ApiResponseDocs.errorResponse("VALIDATION_ERROR"))
                .build();
    }

    public static ResourceSnippetParameters detailSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .summary("동네면접 모임 상세 조회")
                .description("모임 상세 정보와 승인된 참여자 요약을 조회합니다. fullAddress는 방장 및 APPROVED 참여자에게만 공개되며 그 외에는 null을 반환합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .pathParameters(parameterWithName("meetupId").description("모임 ID (UUID)"))
                .responseFields(ApiResponseDocs.withDataFields(DETAIL_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters detailNotFound() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .pathParameters(parameterWithName("meetupId").description("모임 ID (UUID)"))
                .responseFields(ApiResponseDocs.errorResponse("RESOURCE_NOT_FOUND"))
                .build();
    }

    public static ResourceSnippetParameters createSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .summary("동네면접 오프라인 모임 생성")
                .description("장소가 확정된 오프라인 모의면접 모임을 생성합니다. 개설자는 자동으로 방장(HOST, APPROVED)으로 등록되며 currentParticipants=1로 시작합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .requestFields(CREATE_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.withDataFields(CREATE_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters createValidationError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .requestFields(CREATE_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.errorResponse("VALIDATION_ERROR"))
                .build();
    }

    public static ResourceSnippetParameters myMeetupsSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .summary("내 동네면접 모임 목록 조회")
                .description("내가 개설한 모임(HOST)과 참여 신청한 모임(PARTICIPANT)을 한 번에 조회합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .responseFields(ApiResponseDocs.withDataFields(MY_MEETUPS_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters joinSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .summary("동네면접 모임 참여 신청")
                .description("모임에 참여 신청(PENDING)합니다. 이전에 취소(CANCELLED)한 이력이 있으면 재신청이 허용됩니다.")
                .requestHeaders(REQUEST_HEADERS)
                .pathParameters(parameterWithName("meetupId").description("모임 ID (UUID)"))
                .requestFields(JOIN_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.noContentFields())
                .build();
    }

    public static ResourceSnippetParameters joinDuplicate() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .pathParameters(parameterWithName("meetupId").description("모임 ID (UUID)"))
                .requestFields(JOIN_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.errorResponse("DUPLICATE_RESOURCE"))
                .build();
    }

    public static ResourceSnippetParameters joinValidationError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .pathParameters(parameterWithName("meetupId").description("모임 ID (UUID)"))
                .requestFields(JOIN_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.errorResponse("VALIDATION_ERROR"))
                .build();
    }

    public static ResourceSnippetParameters leaveSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .summary("동네면접 참여 신청 취소 / 모임 나가기")
                .description("내 참여 신청(PENDING) 또는 확정된 참여(APPROVED)를 취소합니다. 방장은 본인 모임에서 탈퇴할 수 없습니다.")
                .requestHeaders(REQUEST_HEADERS)
                .pathParameters(parameterWithName("meetupId").description("모임 ID (UUID)"))
                .responseFields(ApiResponseDocs.noContentFields())
                .build();
    }

    public static ResourceSnippetParameters leaveValidationError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .pathParameters(parameterWithName("meetupId").description("모임 ID (UUID)"))
                .responseFields(ApiResponseDocs.errorResponse("VALIDATION_ERROR"))
                .build();
    }

    public static ResourceSnippetParameters applicantsSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .summary("모임 신청자 및 참여자 목록 조회 (방장 전용)")
                .description("방장이 모임의 신청자·참여자 전체 목록과 상태를 조회합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .pathParameters(parameterWithName("meetupId").description("모임 ID (UUID)"))
                .responseFields(ApiResponseDocs.withDataFields(APPLICANTS_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters applicantsForbidden() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .pathParameters(parameterWithName("meetupId").description("모임 ID (UUID)"))
                .responseFields(ApiResponseDocs.errorResponse("FORBIDDEN"))
                .build();
    }

    public static ResourceSnippetParameters reviewApplicantSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .summary("참여 신청 승인 / 거절 (방장 전용)")
                .description("방장이 대기 중인 신청(PENDING)을 승인(APPROVED)하거나 거절(REJECTED)합니다. 승인 시 비관적 잠금으로 정원 초과를 방지합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .pathParameters(
                        parameterWithName("meetupId").description("모임 ID (UUID)"),
                        parameterWithName("userId").description("대상 신청자 사용자 ID (UUID)")
                )
                .requestFields(REVIEW_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.noContentFields())
                .build();
    }

    public static ResourceSnippetParameters reviewApplicantForbidden() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .pathParameters(
                        parameterWithName("meetupId").description("모임 ID (UUID)"),
                        parameterWithName("userId").description("대상 신청자 사용자 ID (UUID)")
                )
                .requestFields(REVIEW_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.errorResponse("FORBIDDEN"))
                .build();
    }

    public static ResourceSnippetParameters reviewApplicantFull() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag(TAG)
                .pathParameters(
                        parameterWithName("meetupId").description("모임 ID (UUID)"),
                        parameterWithName("userId").description("대상 신청자 사용자 ID (UUID)")
                )
                .requestFields(REVIEW_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.errorResponse("VALIDATION_ERROR"))
                .build();
    }
}
