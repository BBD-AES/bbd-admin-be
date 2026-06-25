package com.hd.hdp.provisioning.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public final class AdminUserRequests {

    private AdminUserRequests() {
    }

    public record CreateUserRequest(
            @Email String email,
            @NotBlank String displayName,
            @NotBlank String password,
            Boolean temporaryPassword,
            Boolean enabled,
            Boolean emailVerified,
            String employeeNumber,
            Boolean autoEmployeeNumber,
            @NotBlank String position,
            @NotNull ProvisioningEnums.UserRole role,
            @NotNull ProvisioningEnums.TenancyType tenancyType,
            @NotBlank String tenancyName,
            Boolean sourceActive,
            Boolean requireTotp,
            Map<String, List<String>> attributes
    ) {
    }

    public record UpdateUserRequest(
            @Email String email,
            @NotBlank String displayName,
            String password,
            Boolean temporaryPassword,
            Boolean enabled,
            Boolean emailVerified,
            @NotBlank String employeeNumber,
            @NotBlank String position,
            @NotNull ProvisioningEnums.UserRole role,
            @NotNull ProvisioningEnums.TenancyType tenancyType,
            @NotBlank String tenancyName,
            Boolean sourceActive,
            Boolean requireTotp,
            Map<String, List<String>> attributes
    ) {
    }

    public record BulkCreateUsersRequest(
            @NotEmpty List<@Valid CreateUserRequest> users
    ) {
    }
}
