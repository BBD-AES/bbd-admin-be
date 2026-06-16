package com.hd.hdp.provisioning.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public final class AdminUserRequests {

    private AdminUserRequests() {
    }

    public record CreateUserRequest(
            @NotBlank String username,
            @Email String email,
            String firstName,
            String lastName,
            String displayName,
            String password,
            Boolean temporaryPassword,
            Boolean enabled,
            Boolean emailVerified,
            @NotBlank String employeeNumber,
            String position,
            @NotNull ProvisioningEnums.UserRole role,
            @NotNull ProvisioningEnums.TenancyType tenancyType,
            String tenancyName,
            Boolean sourceActive,
            Map<String, List<String>> attributes
    ) {
    }

    public record UpdateUserRequest(
            @NotBlank String username,
            @Email String email,
            String firstName,
            String lastName,
            String displayName,
            String password,
            Boolean temporaryPassword,
            Boolean enabled,
            Boolean emailVerified,
            @NotBlank String employeeNumber,
            String position,
            @NotNull ProvisioningEnums.UserRole role,
            @NotNull ProvisioningEnums.TenancyType tenancyType,
            String tenancyName,
            Boolean sourceActive,
            Map<String, List<String>> attributes
    ) {
    }
}
