package app.mockly.domain.auth.dto;

public enum Platform {
    ANDROID,
    IOS;

    // ponytail: 클라이언트가 platform을 안 보내던 시절의 요청은 전부 Android였다
    public static final Platform DEFAULT = ANDROID;
}
