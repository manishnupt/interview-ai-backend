# Multi-Tenancy in AI Interview Platform

## Overview

The platform uses a **shared database, shared schema** multi-tenancy model. All tenants (companies) live in the same database and tables. Isolation is enforced at the application layer by scoping every query with a `companyId` column that is present on every tenant-owned entity.

The tenant identity is established at login, embedded in a signed JWT, and carried through every request — the backend never trusts a `companyId` supplied by the client in a request body or URL parameter.

---

## End-to-End Flow

```
HTTP Request
    │
    ▼
JwtAuthFilter
    ├── Extract token (Authorization header OR jwt HttpOnly cookie)
    ├── Validate JWT signature (HS256)
    └── Set AuthenticatedUser(userId, companyId, role, email) into SecurityContext
    │
    ▼
Controller
    └── SecurityUtils.getCurrentUser().companyId()   ← tenant identity, read-only
    │
    ▼
Service
    └── passes companyId to every repository method
    │
    ▼
Repository (Spring Data JPA)
    └── SQL WHERE always includes companyId          ← hard isolation at DB layer
```

---

## Step 1 — Token Generation

**File:** `auth/JwtUtil.java:40`

When a user logs in or accepts an invite, `generateAccessToken(User user)` embeds the tenant into the JWT payload as a custom claim:

```java
Jwts.builder()
    .subject(user.getId().toString())
    .claim("companyId", user.getCompanyId())   // tenant identity
    .claim("role",      user.getRole().name())
    .claim("email",     user.getEmail())
    .expiration(new Date(now.getTime() + accessTokenExpirationMs))
    .signWith(getSigningKey(), Jwts.SIG.HS256)
    .compact();
```

- `companyId` is sourced from the `User` row in the database — it is set at company registration or invite acceptance and never changes.
- The token is signed with a secret (`app.jwt.secret`) so the claim cannot be tampered with client-side.
- Default access token lifetime: **15 minutes** (`app.jwt.access-token-expiration-ms=900000`). Refresh tokens last **7 days**.

---

## Step 2 — Per-Request Tenant Resolution

**File:** `auth/JwtAuthFilter.java`

Every request runs through `JwtAuthFilter` (a `OncePerRequestFilter`) before hitting any controller:

1. Extract token from `Authorization: Bearer <token>` header **or** `jwt` HttpOnly cookie (both are supported).
2. Validate the JWT signature — reject silently if invalid (passes through as unauthenticated).
3. Parse `userId`, `companyId`, `role`, `email` from the token claims.
4. Wrap them in `AuthenticatedUser` and register it as the `SecurityContext` principal.

```java
AuthenticatedUser principal = new AuthenticatedUser(userId, companyId, role, email);
UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
);
SecurityContextHolder.getContext().setAuthentication(auth);
```

After this point, any code running in the same thread can safely read the tenant identity from the security context.

---

## Step 3 — Accessing Tenant Identity in Controllers

**File:** `common/SecurityUtils.java`

Controllers never read `companyId` from request parameters. They call:

```java
Long companyId = SecurityUtils.getCurrentUser().companyId();
```

`SecurityUtils.getCurrentUser()` casts the `SecurityContext` principal to `AuthenticatedUser`:

```java
return (AuthenticatedUser) SecurityContextHolder
        .getContext()
        .getAuthentication()
        .getPrincipal();
```

Example from `CandidateController`:

```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<CandidateResponse>> getCandidate(@PathVariable Long id) {
    Long companyId = SecurityUtils.getCurrentUser().companyId(); // from JWT, not request
    return ResponseEntity.ok(ApiResponse.ok(candidateService.getCandidate(id, companyId)));
}
```

---

## Step 4 — Data Isolation at the Repository Layer

**File:** `candidates/CandidateRepository.java` (and all other repositories)

The service always passes `companyId` into every repository call. Spring Data JPA derives SQL `WHERE` clauses from method names, ensuring cross-tenant leakage is impossible:

```java
Optional<Candidate> findByIdAndCompanyId(Long id, Long companyId);
List<Candidate>     findAllByJobIdAndCompanyId(Long jobId, Long companyId);
List<Candidate>     findAllByCompanyIdAndStatus(Long companyId, CandidateStatus status);
```

If a user from company A requests a resource that belongs to company B, the `companyId` filter causes Spring Data to return `Optional.empty()` → the service throws `ResourceNotFoundException` → **404 response with no data exposure**.

---

## Data Model Contract

Every entity that belongs to a company **must** carry a `companyId` column:

| Entity         | `companyId` column | Enforced by        |
|----------------|--------------------|--------------------|
| `Job`          | yes                | repository queries |
| `Candidate`    | yes                | repository queries |
| `HrNote`       | yes                | repository queries |
| `InviteToken`  | yes                | repository queries |
| `RefreshToken` | via `User`         | user-scoped lookup |

New entities added to the system must follow this pattern — a `companyId` field and repository methods that always filter by it.

---

## Public Routes (No Tenant Required)

Some routes are intentionally tenant-agnostic and bypass JWT authentication entirely:

| Pattern           | Purpose                              |
|-------------------|--------------------------------------|
| `/api/auth/**`    | Login, register, invite acceptance   |
| `/api/public/**`  | Candidate apply form (job slug-based)|

The candidate apply endpoint (`POST /api/public/jobs/{slug}/apply`) resolves the tenant indirectly: it looks up the `Job` by its public `slug`, and then reads `job.getCompanyId()` to stamp the new `Candidate` row — no JWT needed.

---

## Security Properties

- **`companyId` is never trusted from the client.** It is always read from the JWT or derived from a slug lookup.
- **JWT is tamper-proof.** The HS256 signature is verified on every request; a forged `companyId` claim would fail validation.
- **DB queries are the last line of defense.** Even if middleware were bypassed, `findByIdAndCompanyId` ensures no cross-tenant data can be returned.
- **Roles are tenant-scoped.** A `COMPANY_ADMIN` for tenant A cannot act on behalf of tenant B; their role grant only applies within their `companyId`.
- **`SUPER_ADMIN`** is the only role that can access `/api/admin/**` routes, which are not scoped to a single tenant.
