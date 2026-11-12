package app.mockly.domain.user.entity;

import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum AgreementType {
    AGE_14("age14", true),
    TERMS("terms", true),
    PRIVACY("privacy", true),
    LOCATION("location", false),
    MARKETING("marketing", false);

    private final String code;
    private final boolean required;

    AgreementType(String code, boolean required) {
        this.code = code;
        this.required = required;
    }

    public static AgreementType fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "약관 유형이 비어 있습니다.");
        }
        String trimmed = raw.trim();
        return Arrays.stream(values())
                .filter(type -> type.code.equalsIgnoreCase(trimmed) || type.name().equalsIgnoreCase(trimmed))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ApiStatusCode.VALIDATION_ERROR,
                        "유효하지 않은 약관 유형입니다: " + trimmed
                ));
    }

    public static List<AgreementType> requiredTypes() {
        return Arrays.stream(values())
                .filter(AgreementType::isRequired)
                .toList();
    }
}
