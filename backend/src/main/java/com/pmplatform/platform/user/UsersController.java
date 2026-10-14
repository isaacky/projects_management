package com.pmplatform.platform.user;

import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.platform.user.MeController.UserResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Active users of the organisation, e.g. to record who attended a bid opening. */
@RestController
public class UsersController {

    private final CurrentUserService currentUser;

    public UsersController(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/users")
    public List<UserResponse> users() {
        currentUser.require();
        return currentUser.all(TenantContext.require()).stream().map(UserResponse::from).toList();
    }
}
