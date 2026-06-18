# User Provisioning Flow

## Flow

1. The React console navigates to `http://localhost:8090/oauth2/authorization/keycloak`.
2. `bbd-admin-be` handles Keycloak login with Spring Security `oauth2Login()`.
3. The console calls `bbd-admin-be` admin APIs with the browser session cookie.
4. `bbd-admin-be` gets a service-account token from the `user-admin-console-admin` Keycloak client.
5. `bbd-admin-be` creates or updates the Keycloak user through Keycloak Admin REST.
6. `bbd-admin-be` calls the ERP user service SCIM API and stores the Keycloak user id as SCIM `externalId`.
7. If SCIM create fails after Keycloak create, `PROVISIONING_CREATE_FAILURE_COMPENSATION` controls whether the Keycloak user is deleted, disabled, or left as-is.

## Why Backend First

The frontend is intentionally thin. It does not call Keycloak Admin REST directly and it does not store admin client secrets. All privileged work stays inside `bbd-admin-be`.

## Main APIs

- `GET /api/session/me`
- `GET /api/session/login-url`
- `GET /api/admin/users?search=`
- `POST /api/admin/users`
- `GET /api/admin/users/{keycloakUserId}`
- `PUT /api/admin/users/{keycloakUserId}`
- `DELETE /api/admin/users/{keycloakUserId}`

## Compensation Note

Create can be partially compensated because the Keycloak user id is known immediately after the Keycloak create call.

Update is different. Once Keycloak update succeeds, automatic rollback is risky unless the previous representation is stored and carefully restored. This implementation reports `SCIM_UPDATE_FAILED_AFTER_KEYCLOAK_UPDATE` and leaves operator recovery explicit.
