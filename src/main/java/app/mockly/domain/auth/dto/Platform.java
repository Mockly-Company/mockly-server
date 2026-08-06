package app.mockly.domain.auth.dto;

import java.util.regex.Pattern;

public enum Platform {
    ANDROID,
    IOS;

    // ponytail: 클라이언트가 platform을 안 보내던 시절의 요청은 전부 Android였다
    public static final Platform DEFAULT = ANDROID;

    /**
     * 요청 본문의 platform을 우선 사용하고, 없으면 User-Agent로 추론한다.
     * <p>
     * User-Agent 추론은 명시적 계약이 아니라 폴백이다. 앱이 HTTP 스택을 바꾸면
     * (예: Flutter의 {@code Dart/3.x (dart:io)}) 아래 시그니처가 사라져 ANDROID로 잘못 잡힌다.
     * 클라이언트가 platform을 보내기 시작하면 이 경로는 타지 않는다.
     */
    public static Platform resolve(Platform requested, String userAgent) {
        if (requested != null) {
            return requested;
        }
        return fromUserAgent(userAgent);
    }

    /**
     * 애플 클라이언트가 User-Agent에 남기는 시그니처.
     * <p>
     * URLSession은 {@code mockly/1 CFNetwork/3860.100.1 Darwin/25.5.0}처럼 CFNetwork/Darwin을 붙이지만,
     * Alamofire는 UA를 직접 덮어써서 {@code Mockly/2.0 (com.mockly.app; iOS 18.0) Alamofire/5.9.1}처럼
     * 플랫폼명만 남는다. ios는 단어 경계로 매칭해 {@code axios/1.6.0} 같은 HTTP 클라이언트명과 구분한다.
     */
    private static final Pattern APPLE_SIGNATURE =
            Pattern.compile("cfnetwork|darwin|iphone|ipad|\\bios\\b", Pattern.CASE_INSENSITIVE);

    public static Platform fromUserAgent(String userAgent) {
        if (userAgent == null) {
            return DEFAULT;
        }
        return APPLE_SIGNATURE.matcher(userAgent).find() ? IOS : DEFAULT;
    }

    /**
     * 어떤 근거로 플랫폼이 정해졌는지. 로그로 남겨 오판을 추적한다.
     */
    public static String resolveSource(Platform requested) {
        return requested != null ? "request" : "user-agent";
    }
}
