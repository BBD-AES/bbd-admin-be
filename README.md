# bbd-admin-be

Spring Boot backend for the BBD admin console.

This service owns the server-side security boundary:

- Browser login with Keycloak through Spring Security `oauth2Login()`.
- Session-based admin APIs for the React frontend.
- Server-to-server Keycloak Admin REST calls through Spring HTTP Interface clients.
- SCIM calls to the ERP user service through Spring HTTP Interface clients after Keycloak user create/update/deactivate.

The frontend must not hold Keycloak admin client secrets. It should only call this backend with browser credentials.

Outbound HTTP follows the same style as the ERP services: `@HttpExchange` interfaces with a `RestClient` transport created in `HttpServiceConfig`. The SCIM transport is created manually so mTLS key/trust stores can still be applied.

## Local Run

```powershell
Copy-Item .env.example .env
.\gradlew.bat bootRun
```

Default backend URL:

```text
http://localhost:8090
```

## Keycloak Clients

Browser login client:

```text
client_id: user-admin-console
valid redirect URI: http://localhost:8090/login/oauth2/code/keycloak
web origin: http://localhost:5174
standard flow: on
client authentication: on
PKCE: S256
```

Admin REST client:

```text
client_id: user-admin-console-admin
client authentication: on
service account roles: on
realm-management roles: manage-users, view-users
```

## Frontend Pairing

Run `bbd-admin-fe` separately on `http://localhost:5174`.

The backend CORS and post-login redirect are controlled by:

```text
FRONTEND_ORIGIN=http://localhost:5174
```

After login, Spring Security redirects back to the frontend. API calls use the backend session cookie.

## SCIM

`USER_SCIM_BASE_URL` should point at the ERP user service root through the gateway, for example:

```text
USER_SCIM_BASE_URL=https://bbd.inwoohub.com/user
```

The backend calls:

```text
https://bbd.inwoohub.com/user/scim/v2/Users
```

For local development, `USER_SCIM_AUTH_MODE=NONE` is convenient. For integrated environments, use the mode expected by the user service, usually `MTLS` or `CLIENT_CREDENTIALS`.

## Verification

```powershell
.\gradlew.bat test
```
