package app.mockly.domain.appconfig.controller;

import app.mockly.domain.appconfig.dto.request.GetAppConfigRequest;
import app.mockly.domain.appconfig.dto.response.GetAppConfigResponse;
import app.mockly.domain.appconfig.service.AppConfigService;
import app.mockly.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/app-status")
@RequiredArgsConstructor
public class AppConfigController {
    private final AppConfigService appConfigService;

    @GetMapping
    public ResponseEntity<ApiResponse<GetAppConfigResponse>> getAppConfig(
            @RequestHeader(value = "X-Client-Platform", required = false) String platform,
            @RequestHeader(value = "X-Client-Build", required = false) String build,
            @RequestHeader(value = "X-Client-Version", required = false) String version
    ) {
        GetAppConfigRequest request = GetAppConfigRequest.fromHeaders(platform, build, version);
        log.debug("앱 상태 조회: platform={} build={} version={}",
                request.platform(), request.build(), request.version());
        GetAppConfigResponse response = appConfigService.getAppConfig(request.platform(), request.build());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
