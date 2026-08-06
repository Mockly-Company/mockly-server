package app.mockly.domain.auth.dto;

import java.util.regex.Pattern;

public enum Platform {
    ANDROID,
    IOS;

    // ponytail: 판별 불가 시 ANDROID. 첫 클라이언트가 Android였고 지금도 대부분이다
    public static final Platform DEFAULT = ANDROID;

    /**
     * 애플 클라이언트가 User-Agent에 남기는 시그니처.
     * <p>
     * URLSession은 {@code mockly/1 CFNetwork/3860.100.1 Darwin/25.5.0}처럼 CFNetwork/Darwin을 붙이지만,
     * Alamofire는 UA를 직접 덮어써서 {@code Mockly/2.0 (com.mockly.app; iOS 18.0) Alamofire/5.9.1}처럼
     * 플랫폼명만 남긴다. ios는 단어 경계로 매칭해 {@code axios/1.6.0} 같은 HTTP 클라이언트명과 구분한다.
     */
    private static final Pattern APPLE_SIGNATURE =
            Pattern.compile("cfnetwork|darwin|iphone|ipad|\\bios\\b", Pattern.CASE_INSENSITIVE);

    /**
     * User-Agent로 클라이언트 플랫폼을 추론한다.
     * <p>
     * UA는 우리가 통제하는 값이 아니다. 앱이 HTTP 스택을 교체하면(예: Flutter의 {@code Dart/3.x (dart:io)})
     * 시그니처가 사라져 ANDROID로 잘못 잡히고, 증상은 조용한 401이다. 판별 결과는 반드시 로그로 남긴다.
     */
    public static Platform fromUserAgent(String userAgent) {
        if (userAgent == null) {
            return DEFAULT;
        }
        return APPLE_SIGNATURE.matcher(userAgent).find() ? IOS : DEFAULT;
    }
}
