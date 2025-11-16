package app.mockly.domain.auth.dto;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

public record GoogleUser(
        String sub,
        String email,
        String name,
        Boolean emailVerified
) {
    public static GoogleUser from(GoogleIdToken.Payload payload) {
        return new GoogleUser(
                payload.getSubject(),
                payload.getEmail(),
                (String) payload.get("name"),
                payload.getEmailVerified()
        );
    }
}
