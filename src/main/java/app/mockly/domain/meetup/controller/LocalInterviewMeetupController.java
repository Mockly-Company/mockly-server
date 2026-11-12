package app.mockly.domain.meetup.controller;

import app.mockly.domain.meetup.dto.MeetupSearchCriteria;
import app.mockly.domain.meetup.dto.request.CreateLocalInterviewMeetupRequest;
import app.mockly.domain.meetup.dto.request.CreateMeetupParticipantRequest;
import app.mockly.domain.meetup.dto.request.UpdateMeetupParticipantStatusRequest;
import app.mockly.domain.meetup.dto.response.*;
import app.mockly.domain.meetup.entity.*;
import app.mockly.domain.meetup.service.LocalInterviewMeetupService;
import app.mockly.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/local-interview-meetups")
@RequiredArgsConstructor
public class LocalInterviewMeetupController {

    private final LocalInterviewMeetupService localInterviewMeetupService;

    @GetMapping
    public ResponseEntity<ApiResponse<GetLocalInterviewMeetupsResponse>> searchMeetups(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer radiusMeters,
            @RequestParam(required = false) Double swLat,
            @RequestParam(required = false) Double swLng,
            @RequestParam(required = false) Double neLat,
            @RequestParam(required = false) Double neLng,
            @RequestParam(required = false) MeetupJobCategory jobCategory,
            @RequestParam(required = false) MeetupJobRole jobRole,
            @RequestParam(required = false) MeetupInterviewType interviewType,
            @RequestParam(required = false) MeetupExperienceLevel experienceLevel,
            @RequestParam(required = false) MeetupSortType sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        MeetupSearchCriteria criteria = new MeetupSearchCriteria(
                latitude,
                longitude,
                radiusMeters,
                swLat,
                swLng,
                neLat,
                neLng,
                jobCategory,
                jobRole,
                interviewType,
                experienceLevel,
                sort,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(localInterviewMeetupService.searchMeetups(criteria)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<GetMyLocalInterviewMeetupsResponse>> getMyMeetups(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(localInterviewMeetupService.getMyMeetups(userId)));
    }

    @GetMapping("/{meetupId}")
    public ResponseEntity<ApiResponse<GetLocalInterviewMeetupDetailResponse>> getMeetupDetail(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID meetupId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                localInterviewMeetupService.getMeetupDetail(userId, meetupId, latitude, longitude)
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateLocalInterviewMeetupResponse>> createMeetup(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateLocalInterviewMeetupRequest request
    ) {
        CreateLocalInterviewMeetupResponse response = localInterviewMeetupService.createMeetup(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{meetupId}/participants")
    public ResponseEntity<ApiResponse<Void>> joinMeetup(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID meetupId,
            @Valid @RequestBody(required = false) CreateMeetupParticipantRequest request
    ) {
        localInterviewMeetupService.joinMeetup(userId, meetupId, request);
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @DeleteMapping("/{meetupId}/participants/me")
    public ResponseEntity<ApiResponse<Void>> leaveMeetup(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID meetupId
    ) {
        localInterviewMeetupService.leaveMeetup(userId, meetupId);
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @GetMapping("/{meetupId}/participants")
    public ResponseEntity<ApiResponse<GetMeetupApplicantsResponse>> getMeetupApplicants(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID meetupId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                localInterviewMeetupService.getMeetupApplicants(userId, meetupId)
        ));
    }

    @PatchMapping("/{meetupId}/participants/{userId}")
    public ResponseEntity<ApiResponse<Void>> reviewApplicant(
            @AuthenticationPrincipal UUID hostUserId,
            @PathVariable UUID meetupId,
            @PathVariable("userId") UUID applicantUserId,
            @Valid @RequestBody UpdateMeetupParticipantStatusRequest request
    ) {
        localInterviewMeetupService.reviewApplicant(hostUserId, meetupId, applicantUserId, request);
        return ResponseEntity.ok(ApiResponse.noContent());
    }
}
