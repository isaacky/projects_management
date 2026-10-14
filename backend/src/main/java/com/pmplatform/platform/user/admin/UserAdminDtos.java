package com.pmplatform.platform.user.admin;

import com.pmplatform.platform.user.Roles;
import com.pmplatform.platform.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UserAdminDtos {

    private UserAdminDtos() {
    }

    public record UserRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 200) String displayName,
            @NotNull @Size(max = 20) Set<@NotBlank @Pattern(regexp = Roles.CODE_PATTERN,
                    message = "Role codes use capital letters, digits and underscores") String> roles,
            Boolean active) {
    }

    public record AdminUserView(UUID id, String email, String displayName, List<String> roles, boolean active,
            boolean linked, Instant createdAt) {

        public static AdminUserView from(User u) {
            return new AdminUserView(u.getId(), u.getEmail(), u.getDisplayName(),
                    u.getRoles().stream().sorted().toList(), u.isActive(), u.getExternalSubject() != null,
                    u.getCreatedAt());
        }
    }

    /** A role code, what it allows (blank for codes only the approval thresholds use), and how many hold it. */
    public record RoleView(String code, String description, boolean builtIn, long holders) {
    }
}
