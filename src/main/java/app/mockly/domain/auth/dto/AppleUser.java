package app.mockly.domain.auth.dto;

public record AppleUser(
        String sub,
        String email,
        boolean emailVerified
) {
}
