package app.mockly.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoogleToken(
//        @JsonProperty("token_type")
//        String tokenType,
        @JsonProperty("id_token")
        String idToken
) {
}
