package app.mockly.domain.meetup.service;

import app.mockly.domain.meetup.dto.MeetupSearchCriteria;
import app.mockly.domain.meetup.entity.MeetupSortType;
import app.mockly.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpatialDistancePolicyTest {

    private final SpatialDistancePolicy policy = new SpatialDistancePolicy();

    @Test
    @DisplayName("반경 파라미터와 영역 파라미터가 동시에 들어오면 400 VALIDATION_ERROR 예외가 발생한다")
    void resolveAndValidate_bothRadiusAndBounds_throwsValidationError() {
        MeetupSearchCriteria criteria = new MeetupSearchCriteria(
                37.55, 126.92, 3000,
                37.50, 126.90, 37.60, 127.00,
                null, null, null, null, MeetupSortType.DISTANCE, 1, 20
        );

        assertThatThrownBy(() -> policy.resolveAndValidate(criteria))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("반경 파라미터와 영역 파라미터가 모두 비어 있으면 400 VALIDATION_ERROR 예외가 발생한다")
    void resolveAndValidate_neitherRadiusNorBounds_throwsValidationError() {
        MeetupSearchCriteria criteria = new MeetupSearchCriteria(
                null, null, null,
                null, null, null, null,
                null, null, null, null, null, 1, 20
        );

        assertThatThrownBy(() -> policy.resolveAndValidate(criteria))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("radiusMeters 미지정 시 기본 반경 3000m로 BoundingBox를 계산한다")
    void resolveAndValidate_defaultRadius3000() {
        MeetupSearchCriteria criteria = new MeetupSearchCriteria(
                37.5626, 126.9247, null,
                null, null, null, null,
                null, null, null, null, null, 1, 20
        );

        SpatialDistancePolicy.BoundingBox box = policy.resolveAndValidate(criteria);
        assertThat(box.radiusMode()).isTrue();
        assertThat(box.radiusMeters()).isEqualTo(3000);
        assertThat(box.minLat()).isLessThan(37.5626);
        assertThat(box.maxLat()).isGreaterThan(37.5626);
    }

    @Test
    @DisplayName("Haversine 거리 계산은 연남동-신촌역 간 약 1.3~1.4km를 반환한다")
    void calculateDistanceMeters_returnsExpectedDistance() {
        int meters = policy.calculateDistanceMeters(37.5626, 126.9247, 37.5552, 126.9369);
        assertThat(meters).isBetween(1250, 1450);
    }

    @Test
    @DisplayName("size가 최대 상한(50)을 초과하면 400 VALIDATION_ERROR 예외가 발생한다")
    void resolveAndValidate_sizeExceedsMaxPageSize_throwsValidationError() {
        MeetupSearchCriteria criteria = new MeetupSearchCriteria(
                37.5626, 126.9247, 3000,
                null, null, null, null,
                null, null, null, null, MeetupSortType.DISTANCE, 1, 51
        );

        assertThatThrownBy(() -> policy.resolveAndValidate(criteria))
                .isInstanceOf(BusinessException.class);
    }
}
