package app.mockly.domain.meetup.service;

import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.meetup.dto.MeetupSearchCriteria;
import app.mockly.domain.meetup.dto.request.CreateLocalInterviewMeetupRequest;
import app.mockly.domain.meetup.dto.request.CreateMeetupParticipantRequest;
import app.mockly.domain.meetup.dto.request.UpdateMeetupParticipantStatusRequest;
import app.mockly.domain.meetup.dto.response.*;
import app.mockly.domain.meetup.entity.*;
import app.mockly.domain.meetup.repository.LocalInterviewMeetupParticipantRepository;
import app.mockly.domain.meetup.repository.LocalInterviewMeetupRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LocalInterviewMeetupService {

    private final LocalInterviewMeetupRepository meetupRepository;
    private final LocalInterviewMeetupParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final SpatialDistancePolicy spatialDistancePolicy;

    @Transactional(readOnly = true)
    public GetLocalInterviewMeetupsResponse searchMeetups(MeetupSearchCriteria criteria) {
        SpatialDistancePolicy.BoundingBox box = spatialDistancePolicy.resolveAndValidate(criteria);
        Instant now = Instant.now();

        List<LocalInterviewMeetup> candidates = meetupRepository.findInBoundingBox(
                now,
                box.minLat(),
                box.maxLat(),
                box.minLng(),
                box.maxLng(),
                criteria.jobCategory(),
                criteria.jobRole(),
                criteria.interviewType(),
                criteria.experienceLevel()
        );

        MeetupSortType effectiveSort = criteria.sort() != null
                ? criteria.sort()
                : (box.radiusMode() ? MeetupSortType.DISTANCE : MeetupSortType.STARTS_AT);

        List<GetLocalInterviewMeetupsResponse.MeetupListItem> filtered;
        if (box.radiusMode()) {
            filtered = candidates.stream()
                    .map(meetup -> {
                        int distance = spatialDistancePolicy.calculateDistanceMeters(
                                box.originLat(),
                                box.originLng(),
                                meetup.getLatitude(),
                                meetup.getLongitude()
                        );
                        return GetLocalInterviewMeetupsResponse.MeetupListItem.from(meetup, distance);
                    })
                    .filter(item -> item.distanceMeters() <= box.radiusMeters())
                    .sorted(effectiveSort == MeetupSortType.DISTANCE
                            ? Comparator.comparingInt(GetLocalInterviewMeetupsResponse.MeetupListItem::distanceMeters)
                                    .thenComparing(GetLocalInterviewMeetupsResponse.MeetupListItem::startsAt)
                            : Comparator.comparing(GetLocalInterviewMeetupsResponse.MeetupListItem::startsAt)
                                    .thenComparingInt(GetLocalInterviewMeetupsResponse.MeetupListItem::distanceMeters))
                    .toList();
        } else {
            filtered = candidates.stream()
                    .map(meetup -> GetLocalInterviewMeetupsResponse.MeetupListItem.from(meetup, null))
                    .sorted(Comparator.comparing(GetLocalInterviewMeetupsResponse.MeetupListItem::startsAt))
                    .toList();
        }

        int totalElements = filtered.size();
        int size = criteria.size();
        int page = criteria.page();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min((page - 1) * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<GetLocalInterviewMeetupsResponse.MeetupListItem> pageItems = filtered.subList(fromIndex, toIndex);
        return new GetLocalInterviewMeetupsResponse(
                pageItems,
                new GetLocalInterviewMeetupsResponse.Pagination(
                        page,
                        size,
                        totalElements,
                        totalPages,
                        page < totalPages
                )
        );
    }

    @Transactional(readOnly = true)
    public GetLocalInterviewMeetupDetailResponse getMeetupDetail(
            UUID userId,
            UUID meetupId,
            Double latitude,
            Double longitude
    ) {
        LocalInterviewMeetup meetup = meetupRepository.findByIdWithHost(meetupId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "모임을 찾을 수 없습니다."));

        List<LocalInterviewMeetupParticipant> approvedParticipants = participantRepository
                .findAllByMeetupIdAndStatusWithUser(meetupId, MeetupParticipationStatus.APPROVED);

        Optional<LocalInterviewMeetupParticipant> myRecord = userId != null
                ? participantRepository.findByMeetupIdAndUserId(meetupId, userId)
                : Optional.empty();

        boolean amIHost = meetup.isHost(userId);
        MeetupParticipationStatus myStatus = myRecord
                .map(LocalInterviewMeetupParticipant::getStatus)
                .orElse(null);

        boolean canViewFullAddress = amIHost || myStatus == MeetupParticipationStatus.APPROVED;
        String exposedFullAddress = canViewFullAddress ? meetup.resolveConfirmedAddress() : null;

        Integer distanceMeters = (latitude != null && longitude != null)
                ? spatialDistancePolicy.calculateDistanceMeters(
                        latitude, longitude, meetup.getLatitude(), meetup.getLongitude())
                : null;

        List<GetLocalInterviewMeetupDetailResponse.ParticipantSummary> summaries = approvedParticipants.stream()
                .map(GetLocalInterviewMeetupDetailResponse.ParticipantSummary::from)
                .toList();

        return new GetLocalInterviewMeetupDetailResponse(
                meetup.getId().toString(),
                meetup.getTitle(),
                meetup.getDescription(),
                meetup.getJobCategory(),
                meetup.getJobRole(),
                meetup.getInterviewType(),
                meetup.getExperienceLevel(),
                meetup.getStartsAt(),
                meetup.getEndsAt(),
                meetup.getCurrentParticipants(),
                meetup.getMaxParticipants(),
                meetup.getPlaceName(),
                exposedFullAddress,
                distanceMeters,
                meetup.getLatitude(),
                meetup.getLongitude(),
                summaries,
                meetup.isFull(),
                meetup.getCreatedAt(),
                meetup.getRecruitDeadline(),
                myStatus,
                amIHost
        );
    }

    @Transactional
    public CreateLocalInterviewMeetupResponse createMeetup(UUID userId, CreateLocalInterviewMeetupRequest request) {
        User hostUser = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.USER_NOT_FOUND));

        if (!request.startsAt().isBefore(request.endsAt())) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "시작 시각(startsAt)은 종료 시각(endsAt)보다 이전이어야 합니다.");
        }
        if (request.recruitDeadline().isAfter(request.startsAt())) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "모집 마감 시각(recruitDeadline)은 시작 시각(startsAt) 이전이어야 합니다.");
        }

        String normalizedFullAddress = request.fullAddress() != null && !request.fullAddress().isBlank()
                ? request.fullAddress().trim()
                : null;

        LocalInterviewMeetup meetup = LocalInterviewMeetup.builder()
                .host(hostUser)
                .title(request.title().trim())
                .description(request.description().trim())
                .jobCategory(request.jobCategory())
                .jobRole(request.jobRole())
                .interviewType(request.interviewType())
                .experienceLevel(request.experienceLevel())
                .locationStatus(request.locationStatus())
                .meetingType(request.meetingType())
                .placeName(request.placeName().trim())
                .addressLabel(request.addressLabel().trim())
                .fullAddress(normalizedFullAddress)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .recruitDeadline(request.recruitDeadline())
                .currentParticipants(1)
                .maxParticipants(request.maxParticipants())
                .build();

        LocalInterviewMeetup savedMeetup = meetupRepository.saveAndFlush(meetup);
        participantRepository.save(LocalInterviewMeetupParticipant.createHost(savedMeetup, hostUser, Instant.now()));

        return new CreateLocalInterviewMeetupResponse(savedMeetup.getId().toString());
    }

    @Transactional(readOnly = true)
    public GetMyLocalInterviewMeetupsResponse getMyMeetups(UUID userId) {
        List<LocalInterviewMeetupParticipant> myParticipations = participantRepository.findAllByUserIdWithMeetup(userId);
        if (myParticipations.isEmpty()) {
            return new GetMyLocalInterviewMeetupsResponse(List.of());
        }

        List<UUID> hostedMeetupIds = myParticipations.stream()
                .filter(LocalInterviewMeetupParticipant::isHost)
                .map(p -> p.getMeetup().getId())
                .toList();

        Map<UUID, Integer> pendingCountByMeetupId = hostedMeetupIds.isEmpty()
                ? Map.of()
                : participantRepository.countByMeetupIdsAndStatus(hostedMeetupIds, MeetupParticipationStatus.PENDING)
                        .stream()
                        .collect(Collectors.toMap(
                                LocalInterviewMeetupParticipantRepository.MeetupPendingCountProjection::getMeetupId,
                                p -> Math.toIntExact(p.getCnt())
                        ));

        List<GetMyLocalInterviewMeetupsResponse.MyMeetupItem> items = myParticipations.stream()
                .map(p -> {
                    int pendingCount = p.isHost()
                            ? pendingCountByMeetupId.getOrDefault(p.getMeetup().getId(), 0)
                            : 0;
                    return GetMyLocalInterviewMeetupsResponse.MyMeetupItem.from(p, pendingCount);
                })
                .toList();

        return new GetMyLocalInterviewMeetupsResponse(items);
    }

    @Transactional
    public void joinMeetup(UUID userId, UUID meetupId, CreateMeetupParticipantRequest request) {
        User applicant = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.USER_NOT_FOUND));

        LocalInterviewMeetup meetup = meetupRepository.findByIdForUpdate(meetupId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "모임을 찾을 수 없습니다."));

        if (meetup.isHost(userId)) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "본인이 개설한 모임에는 참여 신청할 수 없습니다.");
        }

        Instant now = Instant.now();
        if (meetup.isRecruitmentClosed(now)) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "모집이 마감된 모임입니다.");
        }
        if (meetup.isFull()) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "정원이 마감된 모임입니다.");
        }

        String normalizedMessage = request != null && request.message() != null && !request.message().isBlank()
                ? request.message().trim()
                : null;

        Optional<LocalInterviewMeetupParticipant> existingOpt = participantRepository
                .findByMeetupIdAndUserId(meetupId, userId);

        if (existingOpt.isPresent()) {
            LocalInterviewMeetupParticipant existing = existingOpt.get();
            if (existing.getStatus().isActive()) {
                throw new BusinessException(ApiStatusCode.DUPLICATE_RESOURCE, "이미 참여 신청했거나 참여 중인 모임입니다.");
            }
            if (existing.getStatus() == MeetupParticipationStatus.REJECTED) {
                throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "거절된 모임에는 다시 신청할 수 없습니다.");
            }
            existing.reapply(normalizedMessage, now);
            participantRepository.save(existing);
            return;
        }

        participantRepository.save(LocalInterviewMeetupParticipant.createApplicant(meetup, applicant, normalizedMessage, now));
    }

    @Transactional
    public void leaveMeetup(UUID userId, UUID meetupId) {
        LocalInterviewMeetup meetup = meetupRepository.findByIdForUpdate(meetupId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "모임을 찾을 수 없습니다."));

        if (meetup.isHost(userId)) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "방장은 본인 모임에서 탈퇴할 수 없습니다.");
        }

        LocalInterviewMeetupParticipant participant = participantRepository
                .findByMeetupIdAndUserId(meetupId, userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.VALIDATION_ERROR, "취소 가능한 참여 신청 내역이 없습니다."));

        if (!participant.getStatus().isActive()) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "취소 가능한 참여 신청 내역이 없습니다.");
        }

        if (participant.getStatus() == MeetupParticipationStatus.APPROVED) {
            meetup.decrementParticipants();
            meetupRepository.save(meetup);
        }

        participant.cancel(Instant.now());
        participantRepository.save(participant);
    }

    @Transactional(readOnly = true)
    public GetMeetupApplicantsResponse getMeetupApplicants(UUID userId, UUID meetupId) {
        LocalInterviewMeetup meetup = meetupRepository.findByIdWithHost(meetupId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "모임을 찾을 수 없습니다."));

        if (!meetup.isHost(userId)) {
            throw new BusinessException(ApiStatusCode.FORBIDDEN, "방장만 신청자 목록을 조회할 수 있습니다.");
        }

        List<GetMeetupApplicantsResponse.MeetupApplicantItem> items = participantRepository
                .findAllByMeetupIdWithUser(meetupId)
                .stream()
                .map(GetMeetupApplicantsResponse.MeetupApplicantItem::from)
                .toList();

        return new GetMeetupApplicantsResponse(items);
    }

    @Transactional
    public void reviewApplicant(
            UUID hostUserId,
            UUID meetupId,
            UUID applicantUserId,
            UpdateMeetupParticipantStatusRequest request
    ) {
        if (request.status() != MeetupParticipationStatus.APPROVED
                && request.status() != MeetupParticipationStatus.REJECTED) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "심사 결과는 APPROVED 또는 REJECTED만 허용됩니다.");
        }

        LocalInterviewMeetup meetup = meetupRepository.findByIdForUpdate(meetupId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "모임을 찾을 수 없습니다."));

        if (!meetup.isHost(hostUserId)) {
            throw new BusinessException(ApiStatusCode.FORBIDDEN, "방장만 참여 신청을 승인하거나 거절할 수 있습니다.");
        }

        LocalInterviewMeetupParticipant applicant = participantRepository
                .findByMeetupIdAndUserId(meetupId, applicantUserId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "해당 사용자의 참여 신청 내역을 찾을 수 없습니다."));

        if (applicant.isHost() || applicant.getStatus() != MeetupParticipationStatus.PENDING) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "승인 대기(PENDING) 상태의 신청자만 처리할 수 있습니다.");
        }

        Instant now = Instant.now();
        if (request.status() == MeetupParticipationStatus.APPROVED) {
            if (meetup.isFull()) {
                throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "모임 정원이 가득 차 더 이상 승인할 수 없습니다.");
            }
            applicant.approve(now);
            meetup.incrementParticipants();
            meetupRepository.save(meetup);
        } else {
            applicant.reject(now);
        }
        participantRepository.save(applicant);
    }
}
