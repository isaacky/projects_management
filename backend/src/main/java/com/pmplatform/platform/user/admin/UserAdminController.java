package com.pmplatform.platform.user.admin;

import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.platform.user.admin.UserAdminDtos.AdminUserView;
import com.pmplatform.platform.user.admin.UserAdminDtos.RoleView;
import com.pmplatform.platform.user.admin.UserAdminDtos.UserRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Users and roles (system administrators only). */
@RestController
@RequestMapping("/api/v1/admin")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping("/users")
    public List<AdminUserView> users() {
        return service.list(TenantContext.require());
    }

    @GetMapping("/roles")
    public List<RoleView> roles() {
        return service.roles(TenantContext.require());
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserView create(@Valid @RequestBody UserRequest body) {
        return service.create(TenantContext.require(), body);
    }

    @PutMapping("/users/{userId}")
    public AdminUserView update(@PathVariable UUID userId, @Valid @RequestBody UserRequest body) {
        return service.update(TenantContext.require(), userId, body);
    }

    @PostMapping("/users/{userId}/unlink")
    public AdminUserView unlink(@PathVariable UUID userId) {
        return service.unlink(TenantContext.require(), userId);
    }
}
