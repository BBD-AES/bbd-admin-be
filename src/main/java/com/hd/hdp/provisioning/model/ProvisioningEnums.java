package com.hd.hdp.provisioning.model;

public final class ProvisioningEnums {

    private ProvisioningEnums() {
    }

    public enum UserRole {
        ADMIN,
        HQ_MANAGER,
        HQ_STAFF,
        BRANCH_MANAGER,
        BRANCH_STAFF
    }

    public enum TenancyType {
        HQ,
        BRANCH
    }
}
