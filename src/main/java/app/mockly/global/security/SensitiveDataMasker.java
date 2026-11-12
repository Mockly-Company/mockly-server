package app.mockly.global.security;

public final class SensitiveDataMasker {

    private SensitiveDataMasker() {
    }

    public static String maskBillingKey(String billingKey) {
        if (billingKey == null || billingKey.isBlank()) {
            return "****";
        }
        String trimmed = billingKey.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****";
    }

    public static String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return null;
        }
        String trimmed = cardNumber.trim();
        if (trimmed.contains("*")) {
            return trimmed;
        }
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.length() >= 8) {
            return digits.substring(0, 4) + "****" + digits.substring(digits.length() - 4);
        }
        if (digits.length() >= 4) {
            return "****" + digits.substring(digits.length() - 4);
        }
        return "****";
    }
}
