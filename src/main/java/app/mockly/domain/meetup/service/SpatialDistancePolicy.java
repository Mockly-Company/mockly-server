package app.mockly.domain.meetup.service;

import app.mockly.domain.meetup.dto.MeetupSearchCriteria;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class SpatialDistancePolicy {

    public static final int DEFAULT_RADIUS_METERS = 3_000;
    public static final int MAX_RADIUS_METERS = 50_000;
    public static final int MAX_PAGE_SIZE = 50;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    public record BoundingBox(
            double minLat,
            double maxLat,
            double minLng,
            double maxLng,
            boolean radiusMode,
            Double originLat,
            Double originLng,
            Integer radiusMeters,
            Double cosOriginLat,
            Double maxHaversineH
    ) {
    }

    public BoundingBox resolveAndValidate(MeetupSearchCriteria criteria) {
        if (criteria.page() < 1) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "page는 1 이상이어야 합니다.");
        }
        if (criteria.size() < 1 || criteria.size() > MAX_PAGE_SIZE) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }

        boolean hasAnyRadius = criteria.latitude() != null
                || criteria.longitude() != null
                || criteria.radiusMeters() != null;
        boolean hasFullRadius = criteria.latitude() != null && criteria.longitude() != null;

        boolean hasAnyBounds = criteria.swLat() != null
                || criteria.swLng() != null
                || criteria.neLat() != null
                || criteria.neLng() != null;
        boolean hasFullBounds = criteria.swLat() != null
                && criteria.swLng() != null
                && criteria.neLat() != null
                && criteria.neLng() != null;

        if ((hasAnyRadius && hasAnyBounds) || (!hasAnyRadius && !hasAnyBounds)) {
            throw new BusinessException(
                    ApiStatusCode.VALIDATION_ERROR,
                    "반경 검색(latitude, longitude)과 지도 영역 검색(swLat, swLng, neLat, neLng) 중 하나만 전달해야 합니다."
            );
        }

        if (hasAnyRadius) {
            if (!hasFullRadius) {
                throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "반경 검색에는 latitude와 longitude가 모두 필요합니다.");
            }
            validateCoordinate(criteria.latitude(), criteria.longitude());
            int radius = criteria.radiusMeters() != null ? criteria.radiusMeters() : DEFAULT_RADIUS_METERS;
            if (radius < 1 || radius > MAX_RADIUS_METERS) {
                throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "radiusMeters는 1~50000 범위여야 합니다.");
            }

            double originLatRad = Math.toRadians(criteria.latitude());
            double actualCosOriginLat = Math.cos(originLatRad);
            double safeCosLat = Math.max(0.01, actualCosOriginLat);
            double latDelta = radius / METERS_PER_DEGREE_LAT;
            double lngDelta = radius / (METERS_PER_DEGREE_LAT * safeCosLat);
            // calculateDistanceMeters rounds half-up (Math.round), so distance <= radius iff exactMeters < radius + 0.5
            double maxHalfAngleSin = Math.sin((radius + 0.5) / (2.0 * EARTH_RADIUS_METERS));
            double maxHaversineH = maxHalfAngleSin * maxHalfAngleSin;

            return new BoundingBox(
                    Math.max(-90.0, criteria.latitude() - latDelta),
                    Math.min(90.0, criteria.latitude() + latDelta),
                    Math.max(-180.0, criteria.longitude() - lngDelta),
                    Math.min(180.0, criteria.longitude() + lngDelta),
                    true,
                    criteria.latitude(),
                    criteria.longitude(),
                    radius,
                    actualCosOriginLat,
                    maxHaversineH
            );
        }

        if (!hasFullBounds) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "영역 검색에는 swLat, swLng, neLat, neLng가 모두 필요합니다.");
        }
        validateCoordinate(criteria.swLat(), criteria.swLng());
        validateCoordinate(criteria.neLat(), criteria.neLng());
        if (criteria.swLat() > criteria.neLat() || criteria.swLng() > criteria.neLng()) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "지도 남서쪽 좌표는 북동쪽 좌표보다 작거나 같아야 합니다.");
        }

        return new BoundingBox(
                criteria.swLat(),
                criteria.neLat(),
                criteria.swLng(),
                criteria.neLng(),
                false,
                null,
                null,
                null,
                null,
                null
        );
    }

    public int calculateDistanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double sinLat = Math.sin(dLat / 2.0);
        double sinLng = Math.sin(dLng / 2.0);
        double h = sinLat * sinLat
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinLng * sinLng;
        double c = 2.0 * Math.asin(Math.min(1.0, Math.sqrt(h)));
        return (int) Math.round(EARTH_RADIUS_METERS * c);
    }

    private void validateCoordinate(double latitude, double longitude) {
        if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "유효하지 않은 위도 또는 경도 값입니다.");
        }
    }
}
