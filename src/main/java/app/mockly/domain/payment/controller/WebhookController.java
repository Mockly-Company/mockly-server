package app.mockly.domain.payment.controller;

import app.mockly.domain.payment.service.WebhookService;
import io.portone.sdk.server.errors.WebhookVerificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {
    private final WebhookService webhookService;

    @PostMapping("/portone")
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "webhook-id", required = false) String webhookId,
            @RequestHeader(value = "webhook-signature", required = false) String signature,
            @RequestHeader(value = "webhook-timestamp", required = false) String timestamp,
            @RequestBody String rawBody
    ) {

        try {
            webhookService.processWebhook(rawBody, webhookId, signature, timestamp);
        } catch (WebhookVerificationException e) {
            log.warn("웹훅 검증 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok().build();
    }
}
