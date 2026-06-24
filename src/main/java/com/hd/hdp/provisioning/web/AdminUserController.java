package com.hd.hdp.provisioning.web;

import com.hd.hdp.provisioning.model.AdminUserRequests;
import com.hd.hdp.provisioning.model.AdminUserResponses;
import com.hd.hdp.provisioning.security.AdminAuthorizationService;
import com.hd.hdp.provisioning.service.AdminUserProvisioningService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserProvisioningService provisioningService;
    private final AdminAuthorizationService adminAuthorizationService;

    public AdminUserController(
            AdminUserProvisioningService provisioningService,
            AdminAuthorizationService adminAuthorizationService
    ) {
        this.provisioningService = provisioningService;
        this.adminAuthorizationService = adminAuthorizationService;
    }

    @PostMapping
    AdminUserResponses.ProvisionedUserResponse create(
            @Valid @RequestBody AdminUserRequests.CreateUserRequest request,
            Authentication authentication
    ) {
        adminAuthorizationService.requireAdmin(authentication);
        return provisioningService.create(request);
    }

    @PostMapping("/bulk")
    AdminUserResponses.BulkProvisionedUsersResponse createBulk(
            @Valid @RequestBody AdminUserRequests.BulkCreateUsersRequest request,
            Authentication authentication
    ) {
        adminAuthorizationService.requireAdmin(authentication);
        return provisioningService.createBulk(request);
    }

    @PostMapping("/apply-current-settings")
    AdminUserResponses.UserMaintenanceResponse applyCurrentSettings(
            Authentication authentication
    ) {
        adminAuthorizationService.requireAdmin(authentication);
        return provisioningService.applyCurrentSettingsToAllUsers();
    }

    @GetMapping
    List<AdminUserResponses.KeycloakUserSummary> search(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int first,
            @RequestParam(defaultValue = "20") int max,
            Authentication authentication
    ) {
        adminAuthorizationService.requireAdmin(authentication);
        return provisioningService.search(search, first, max);
    }

    @GetMapping("/{keycloakUserId:[0-9a-fA-F-]{36}}")
    AdminUserResponses.AdminUserDetailResponse get(
            @PathVariable String keycloakUserId,
            Authentication authentication
    ) {
        adminAuthorizationService.requireAdmin(authentication);
        return provisioningService.get(keycloakUserId);
    }

    @PutMapping("/{keycloakUserId:[0-9a-fA-F-]{36}}")
    AdminUserResponses.ProvisionedUserResponse update(
            @PathVariable String keycloakUserId,
            @Valid @RequestBody AdminUserRequests.UpdateUserRequest request,
            Authentication authentication
    ) {
        adminAuthorizationService.requireAdmin(authentication);
        return provisioningService.update(keycloakUserId, request);
    }

    @DeleteMapping("/{keycloakUserId:[0-9a-fA-F-]{36}}")
    AdminUserResponses.ProvisionedUserResponse deactivate(
            @PathVariable String keycloakUserId,
            Authentication authentication
    ) {
        adminAuthorizationService.requireAdmin(authentication);
        return provisioningService.deactivate(keycloakUserId);
    }
}
