import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  createUser,
  deactivateUser,
  getSession,
  getUser,
  login,
  logout,
  searchUsers,
  updateUser
} from "./api";
import type {
  AdminUserDetail,
  ApiError,
  KeycloakUserSummary,
  Session,
  TenancyType,
  UserPayload,
  UserRole
} from "./types";
import "./styles.css";

const roles: UserRole[] = [
  "ADMIN",
  "HQ_MANAGER",
  "HQ_STAFF",
  "BRANCH_MANAGER",
  "BRANCH_STAFF"
];

const tenancyTypes: TenancyType[] = ["HQ", "BRANCH"];

const emptyPayload: UserPayload = {
  username: "",
  email: "",
  firstName: "",
  lastName: "",
  displayName: "",
  password: "",
  temporaryPassword: true,
  enabled: true,
  emailVerified: false,
  employeeNumber: "",
  position: "",
  role: "HQ_STAFF",
  tenancyType: "HQ",
  tenancyName: "",
  sourceActive: true,
  attributes: {}
};

export default function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [users, setUsers] = useState<KeycloakUserSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<AdminUserDetail | null>(null);
  const [form, setForm] = useState<UserPayload>(emptyPayload);
  const [search, setSearch] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  const selectedUser = useMemo(
    () => users.find((user) => user.id === selectedId) ?? null,
    [selectedId, users]
  );

  useEffect(() => {
    void refreshSession();
  }, []);

  useEffect(() => {
    if (session?.authenticated) {
      void loadUsers();
    }
  }, [session?.authenticated]);

  async function refreshSession() {
    setBusy(true);
    try {
      setSession(await getSession());
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  async function loadUsers() {
    setBusy(true);
    setError("");
    try {
      setUsers(await searchUsers(search));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  async function selectUser(userId: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const loaded = await getUser(userId);
      setSelectedId(userId);
      setDetail(loaded);
      setForm(payloadFromDetail(loaded));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");

    try {
      const result = selectedId
        ? await updateUser(selectedId, form)
        : await createUser(form);
      setNotice(`${result.result}: ${result.username}`);
      setSelectedId(result.keycloakUserId);
      await loadUsers();
      await selectUser(result.keycloakUserId);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  async function deactivateSelected() {
    if (!selectedId) {
      return;
    }
    if (!window.confirm("선택한 사용자를 비활성화할까요?")) {
      return;
    }

    setBusy(true);
    setError("");
    setNotice("");
    try {
      const result = await deactivateUser(selectedId);
      setNotice(`${result.result}: ${result.username}`);
      await loadUsers();
      await selectUser(selectedId);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  function resetForm() {
    setSelectedId(null);
    setDetail(null);
    setForm(emptyPayload);
    setNotice("");
    setError("");
  }

  if (session && !session.authenticated) {
    return (
      <main className="login-shell">
        <section className="login-panel">
          <div>
            <p className="eyebrow">HDP</p>
            <h1>User Admin</h1>
            <p className="muted">Keycloak 관리자 계정으로 로그인</p>
          </div>
          <button className="primary wide" type="button" onClick={login}>
            로그인
          </button>
        </section>
      </main>
    );
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">HDP</p>
          <h1>User Admin</h1>
        </div>
        <div className="session">
          <span>{session?.username ?? "loading"}</span>
          <button type="button" onClick={logout}>
            로그아웃
          </button>
        </div>
      </header>

      <section className="toolbar">
        <form
          className="search"
          onSubmit={(event) => {
            event.preventDefault();
            void loadUsers();
          }}
        >
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="username, email, name"
          />
          <button type="submit">검색</button>
        </form>
        <button type="button" onClick={resetForm}>
          신규
        </button>
      </section>

      {(notice || error) && (
        <section className={`banner ${error ? "error" : "success"}`}>
          {error || notice}
        </section>
      )}

      <section className="workspace">
        <aside className="user-list">
          <div className="panel-heading">
            <h2>Keycloak Users</h2>
            <span>{users.length}</span>
          </div>
          <div className="rows">
            {users.map((user) => (
              <button
                className={`user-row ${user.id === selectedId ? "selected" : ""}`}
                key={user.id}
                type="button"
                onClick={() => void selectUser(user.id)}
              >
                <span className="row-main">{user.username}</span>
                <span className="row-sub">{user.email ?? user.id}</span>
                <span className={`status ${user.enabled === false ? "off" : "on"}`}>
                  {user.enabled === false ? "disabled" : "enabled"}
                </span>
              </button>
            ))}
            {!users.length && <p className="empty">검색 결과 없음</p>}
          </div>
        </aside>

        <section className="editor">
          <form className="form-grid" onSubmit={submit}>
            <div className="panel-heading span-2">
              <h2>{selectedId ? "사용자 수정" : "사용자 생성"}</h2>
              <span>{busy ? "working" : selectedUser?.id ?? "new"}</span>
            </div>

            <label>
              Username
              <input
                required
                value={form.username}
                onChange={(event) => setFormField("username", event.target.value)}
              />
            </label>

            <label>
              Email
              <input
                type="email"
                value={form.email}
                onChange={(event) => setFormField("email", event.target.value)}
              />
            </label>

            <label>
              First name
              <input
                value={form.firstName}
                onChange={(event) => setFormField("firstName", event.target.value)}
              />
            </label>

            <label>
              Last name
              <input
                value={form.lastName}
                onChange={(event) => setFormField("lastName", event.target.value)}
              />
            </label>

            <label>
              Display name
              <input
                value={form.displayName}
                onChange={(event) => setFormField("displayName", event.target.value)}
              />
            </label>

            <label>
              Employee No.
              <input
                required
                value={form.employeeNumber}
                onChange={(event) => setFormField("employeeNumber", event.target.value)}
              />
            </label>

            <label>
              Position
              <input
                value={form.position}
                onChange={(event) => setFormField("position", event.target.value)}
              />
            </label>

            <label>
              Password
              <input
                type="password"
                value={form.password}
                onChange={(event) => setFormField("password", event.target.value)}
              />
            </label>

            <label>
              Role
              <select
                value={form.role}
                onChange={(event) => setFormField("role", event.target.value as UserRole)}
              >
                {roles.map((role) => (
                  <option key={role} value={role}>
                    {role}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Tenancy
              <select
                value={form.tenancyType}
                onChange={(event) =>
                  setFormField("tenancyType", event.target.value as TenancyType)
                }
              >
                {tenancyTypes.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </label>

            <label className="span-2">
              Tenancy name
              <input
                value={form.tenancyName}
                onChange={(event) => setFormField("tenancyName", event.target.value)}
              />
            </label>

            <div className="checks span-2">
              <label>
                <input
                  checked={form.enabled}
                  type="checkbox"
                  onChange={(event) => setFormField("enabled", event.target.checked)}
                />
                Keycloak enabled
              </label>
              <label>
                <input
                  checked={form.emailVerified}
                  type="checkbox"
                  onChange={(event) => setFormField("emailVerified", event.target.checked)}
                />
                Email verified
              </label>
              <label>
                <input
                  checked={form.temporaryPassword}
                  type="checkbox"
                  onChange={(event) =>
                    setFormField("temporaryPassword", event.target.checked)
                  }
                />
                Temporary password
              </label>
              <label>
                <input
                  checked={form.sourceActive}
                  type="checkbox"
                  onChange={(event) => setFormField("sourceActive", event.target.checked)}
                />
                SCIM active
              </label>
            </div>

            <div className="actions span-2">
              <button className="primary" disabled={busy} type="submit">
                {selectedId ? "수정" : "생성"}
              </button>
              <button disabled={busy || !selectedId} type="button" onClick={deactivateSelected}>
                비활성화
              </button>
              <button disabled={busy} type="button" onClick={resetForm}>
                초기화
              </button>
            </div>
          </form>

          <section className="snapshot">
            <div className="panel-heading">
              <h2>SCIM Projection</h2>
              <span>{detail?.scim ? detail.scim.id : "none"}</span>
            </div>
            <dl>
              <dt>externalId</dt>
              <dd>{detail?.scim?.externalId ?? "-"}</dd>
              <dt>employeeNumber</dt>
              <dd>{detail?.scim?.employeeNumber ?? "-"}</dd>
              <dt>role</dt>
              <dd>{detail?.scim?.role ?? "-"}</dd>
              <dt>tenancy</dt>
              <dd>{detail?.scim?.tenancyType ?? "-"}</dd>
              <dt>active</dt>
              <dd>{detail?.scim?.active == null ? "-" : String(detail.scim.active)}</dd>
            </dl>
          </section>
        </section>
      </section>
    </main>
  );

  function setFormField<K extends keyof UserPayload>(key: K, value: UserPayload[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }
}

function payloadFromDetail(detail: AdminUserDetail): UserPayload {
  const attrs = detail.keycloak.attributes ?? {};
  return {
    username: detail.keycloak.username ?? "",
    email: detail.keycloak.email ?? "",
    firstName: detail.keycloak.firstName ?? "",
    lastName: detail.keycloak.lastName ?? "",
    displayName: detail.scim?.displayName ?? "",
    password: "",
    temporaryPassword: true,
    enabled: detail.keycloak.enabled !== false,
    emailVerified: detail.keycloak.emailVerified === true,
    employeeNumber: detail.scim?.employeeNumber ?? firstAttr(attrs.employeeNumber),
    position: "",
    role: roleValue(detail.scim?.role ?? firstAttr(attrs.erpRole)),
    tenancyType: tenancyValue(detail.scim?.tenancyType ?? firstAttr(attrs.tenancyType)),
    tenancyName: detail.scim?.tenancyName ?? firstAttr(attrs.tenancyName),
    sourceActive: detail.scim?.active !== false,
    attributes: {}
  };
}

function firstAttr(value: string[] | undefined) {
  return value?.[0] ?? "";
}

function roleValue(value: string | null | undefined): UserRole {
  return roles.includes(value as UserRole) ? (value as UserRole) : "HQ_STAFF";
}

function tenancyValue(value: string | null | undefined): TenancyType {
  return tenancyTypes.includes(value as TenancyType) ? (value as TenancyType) : "HQ";
}

function errorMessage(caught: unknown) {
  const apiError = caught as Partial<ApiError>;
  return apiError.message ?? "요청 처리 중 오류가 발생했습니다.";
}
