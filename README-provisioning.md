# HDP User Provisioning

## 흐름

1. React 콘솔이 `http://localhost:8090/oauth2/authorization/keycloak`로 이동한다.
2. hdp 백엔드가 `oauth2Login()`으로 Keycloak `user-admin-console` 로그인을 처리한다.
3. 콘솔이 세션 쿠키로 hdp `/api/admin/users`를 호출한다.
4. hdp가 `user-admin-console-admin` service account token을 발급받아 Keycloak Admin REST API를 호출한다.
5. Keycloak 사용자 생성 성공 후 반환된 user id를 SCIM `externalId`로 사용해 user 서비스 `/user/scim/v2/Users`를 호출한다.
6. 생성 중 SCIM이 실패하면 `PROVISIONING_CREATE_FAILURE_COMPENSATION` 정책에 따라 Keycloak 사용자를 삭제하거나 비활성화한다.

## Keycloak client

`user-admin-console`

- Client authentication: On
- Standard flow: On
- Valid redirect URI: `http://localhost:8090/login/oauth2/code/keycloak`
- Web origin: `http://localhost:5174`

`user-admin-console-admin`

- Client authentication: On
- Service account roles: On
- Service account에 `realm-management`의 `manage-users`, `view-users` 권한 부여

## 실행

백엔드:

```powershell
Copy-Item .env.example .env
.\gradlew.bat bootRun
```

프런트:

```powershell
cd frontend
Copy-Item .env.example .env
npm install
npm run dev
```

## API

- `GET /api/session/me`: 로그인 세션 조회
- `GET /api/admin/users?search=`: Keycloak 사용자 검색
- `POST /api/admin/users`: Keycloak + SCIM 사용자 생성
- `GET /api/admin/users/{keycloakUserId}`: Keycloak 사용자와 SCIM projection 조회
- `PUT /api/admin/users/{keycloakUserId}`: Keycloak 수정 후 SCIM upsert
- `DELETE /api/admin/users/{keycloakUserId}`: Keycloak disabled 처리 후 SCIM inactive 처리
