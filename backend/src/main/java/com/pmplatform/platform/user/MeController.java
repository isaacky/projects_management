package com.pmplatform.platform.user;

import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    private final CurrentUserService currentUser;

    public MeController(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    public record UserResponse(UUID id, String email, String displayName, Set<String> roles) {

        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getRoles());
        }
    }

    /** The signed-in user and their roles. */
    @GetMapping("/api/v1/me")
    public UserResponse me() {
        return UserResponse.from(currentUser.require());
    }
}
