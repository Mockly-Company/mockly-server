package app.mockly.global.common;

import lombok.Getter;

@Getter
public enum ApiStatusCode {
    // 200: 성공 상태 코드
    OK(200, "OK", "요청이 성공했습니다"),
    CREATED(201, "CREATED", "리소스가 생성되었습니다"),
    ACCEPTED(202, "ACCEPTED", "요청이 접수되었습니다"),
    NO_CONTENT(204, "NO_CONTENT", "콘텐츠가 없습니다"),

    // 400: 클라이언트 오류
    BAD_REQUEST(400, "BAD_REQUEST", "잘못된 요청입니다"),
    VALIDATION_ERROR(400, "VALIDATION_ERROR", "입력값 검증에 실패했습니다"),
    DUPLICATE_RESOURCE(400, "DUPLICATE_RESOURCE", "이미 존재하는 리소스입니다"),

    // 401: 인증 오류
    UNAUTHORIZED(401, "UNAUTHORIZED", "인증이 필요합니다"),
    TOKEN_REQUIRED(401, "TOKEN_REQUIRED", "토큰이 필요합니다"),
    INVALID_TOKEN(401, "INVALID_TOKEN", "유효하지 않은 토큰입니다"),
    EXPIRED_TOKEN(401, "EXPIRED_TOKEN", "만료된 토큰입니다"),
    INVALID_GOOGLE_TOKEN(401, "INVALID_GOOGLE_TOKEN", "Google 토큰 검증에 실패했습니다"),

    // 403: 권한 오류
    FORBIDDEN(403, "FORBIDDEN", "접근 권한이 없습니다"),

    // 404: 리소스 찾을 수 없음
    USER_NOT_FOUND(404, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다"),
    RESOURCE_NOT_FOUND(404, "RESOURCE_NOT_FOUND", "리소스를 찾을 수 없습니다"),

    // 500: 서버 오류
    INTERNAL_SERVER_ERROR(500, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다"),
    FILE_UPLOAD_ERROR(500, "FILE_UPLOAD_ERROR", "파일 업로드에 실패했습니다");

    private final int status;
    private final String code;
    private final String message;

    ApiStatusCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
