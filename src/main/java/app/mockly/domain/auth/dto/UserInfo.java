package app.mockly.domain.auth.dto;

import app.mockly.domain.auth.entity.User;

public record UserInfo(
        String id,
        String email,
        String name,
        boolean hasAgreedToTerms,
        String jobCategory,
        String jobRole,
        Integer experienceYears
) {
    public static UserInfo from(User user) {
        return new UserInfo(
                user.getId().toString(),
                user.getEmail(),
                user.getName(),
                user.hasAgreedToTerms(),
                user.getJobCategory(),
                user.getJobRole(),
                user.getExperienceYears()
        );
    }
}
