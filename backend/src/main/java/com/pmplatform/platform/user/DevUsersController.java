package com.pmplatform.platform.user;

import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.platform.user.MeController.UserResponse;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Development only: the users offered in the web app's "Viewing as" switcher. */
@Profile("dev & !sso")
@RestController
public class DevUsersController {

    private final CurrentUserService currentUser;

    public DevUsersController(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/dev/users")
    public List<UserResponse> users() {
        return currentUser.all(TenantContext.require()).stream().map(UserResponse::from).toList();
    }
}
