package app.mockly.domain.meetup.dto.request;

import app.mockly.domain.meetup.entity.*;
import jakarta.validation.constraints.*;

import java.time.Instant;

public record CreateLocalInterviewMeetupRequest(
        @NotBlank(message = "모임 제목은 필수입니다.")
        @Size(max = 100, message = "모임 제목은 100자 이하여야 합니다.")
        String title,

        @NotBlank(message = "모임 설명은 필수입니다.")
        @Size(max = 3000, message = "모임 설명은 3000자 이하여야 합니다.")
        String description,

        @NotNull(message = "직군은 필수입니다.")
        MeetupJobCategory jobCategory,

        @NotNull(message = "직무는 필수입니다.")
        MeetupJobRole jobRole,

        @NotNull(message = "면접 유형은 필수입니다.")
        MeetupInterviewType interviewType,

        @NotNull(message = "경력 수준은 필수입니다.")
        MeetupExperienceLevel experienceLevel,

        @NotNull(message = "장소 상태는 필수입니다.")
        MeetupLocationStatus locationStatus,

        @NotNull(message = "모임 방식은 필수입니다.")
        MeetupMeetingType meetingType,

        @NotBlank(message = "장소명은 필수입니다.")
        @Size(max = 120, message = "장소명은 120자 이하여야 합니다.")
        String placeName,

        @NotBlank(message = "동 단위 주소 라벨은 필수입니다.")
        @Size(max = 200, message = "주소 라벨은 200자 이하여야 합니다.")
        String addressLabel,

        @Size(max = 300, message = "상세 주소는 300자 이하여야 합니다.")
        String fullAddress,

        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
        Double latitude,

        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
        Double longitude,

        @NotNull(message = "시작 시각은 필수입니다.")
        Instant startsAt,

        @NotNull(message = "종료 시각은 필수입니다.")
        Instant endsAt,

        @NotNull(message = "모집 마감 시각은 필수입니다.")
        Instant recruitDeadline,

        @NotNull(message = "최대 인원은 필수입니다.")
        @Min(value = 2, message = "최대 인원은 최소 2명이어야 합니다.")
        @Max(value = 8, message = "최대 인원은 최대 8명이어야 합니다.")
        Integer maxParticipants,

        MeetupJobCategory hostJobCategory,

        MeetupJobRole hostJobRole,

        @Min(value = 0, message = "방장 경력 연차는 0 이상이어야 합니다.")
        @Max(value = 50, message = "방장 경력 연차는 50 이하여야 합니다.")
        Integer hostExperienceYears,

        MeetupExperienceLevel hostExperienceLevel
) {
    public CreateLocalInterviewMeetupRequest(
            String title,
            String description,
            MeetupJobCategory jobCategory,
            MeetupJobRole jobRole,
            MeetupInterviewType interviewType,
            MeetupExperienceLevel experienceLevel,
            MeetupLocationStatus locationStatus,
            MeetupMeetingType meetingType,
            String placeName,
            String addressLabel,
            String fullAddress,
            Double latitude,
            Double longitude,
            Instant startsAt,
            Instant endsAt,
            Instant recruitDeadline,
            Integer maxParticipants
    ) {
        this(
                title,
                description,
                jobCategory,
                jobRole,
                interviewType,
                experienceLevel,
                locationStatus,
                meetingType,
                placeName,
                addressLabel,
                fullAddress,
                latitude,
                longitude,
                startsAt,
                endsAt,
                recruitDeadline,
                maxParticipants,
                null,
                null,
                null,
                null
        );
    }
}
