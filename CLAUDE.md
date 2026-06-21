# AI Interview Platform — Spring Boot Backend

## What this is
Spring Boot 3.3 backend for an AI hiring platform.
Handles auth, job management, candidate pipeline,
orchestration of Python AI services, magic links,
email notifications, analytics, and file uploads.

## Tech stack
- Java 21
- Spring Boot 3.3.x
- Spring Security + JWT (jjwt library)
- Spring Data JPA + Hibernate
- PostgreSQL 16
- liquibase for DB migrations
- AWS S3 for resume storage
- SendGrid for transactional email
- Spring WebClient for calling Python AI services
- Lombok for boilerplate reduction
- Maven build

## Package structure
com.aiinterview.backend
├── auth/          → JWT, Spring Security config, login, register
├── company/       → Company entity, multi-tenant management
├── jobs/          → Job posting CRUD, embed code, slug
├── candidates/    → Candidate ingestion, pipeline, profile
├── pipeline/      → Orchestrator, scheduler, AI service client
├── magiclink/     → Magic link token generation and validation
├── notifications/ → SendGrid email service
├── analytics/     → Metrics aggregation queries
├── files/         → S3 upload and URL generation
└── common/        → Base entity, response wrapper, exceptions

## Multi-tenancy rules
- EVERY entity that belongs to a company MUST have a 
  companyId field (Long)
- EVERY repository query MUST filter by companyId
- NEVER return data across company boundaries
- JWT token carries: userId, companyId, role
- Service layer extracts companyId from SecurityContext

## Hard rules
- NEVER expose internal IDs in URLs — use UUID for public routes
- ALWAYS return responses wrapped in ApiResponse<T>
- ALWAYS validate request bodies with @Valid
- NEVER put business logic in controllers — only in services
- ALL database changes go through Liquibase migration scripts
- ALWAYS use ResponseEntity in controllers

## Key ports
- Spring Boot: 8099 (overridden in application.properties; application.yml says 4040 but .properties wins)
- Python AI service: 8000
- React frontend: 5173
- PostgreSQL: 5434

## Environment
All config in application.yml — never hardcode values.
Local dev uses application-local.yml for overrides.

## Auth system (Sprint 10)
- JWT stored in HttpOnly cookie OR Authorization header (support both)
- Token payload: { userId, companyId, role, email }
- Token expiry: 24 hours (configurable via app.jwt.expiration-ms)
- Password hashing: BCrypt strength 10
- Role hierarchy: SUPER_ADMIN > COMPANY_ADMIN > HR_MEMBER
- All protected routes extract companyId from JWT — never from request body
- Invite flow: generate signed token → email link → recipient sets password

## Security rules
- /api/auth/** → public
- /api/public/** → public (candidate apply form)
- /api/admin/** → SUPER_ADMIN only
- /api/** → authenticated (any role)
- CORS: allow http://localhost:5173 in dev

## Usage storage (Sprint 21)
Python sends usageMetrics as part of the existing 
/api/callbacks/interview-complete payload, and as a new
field in the /screen response (consumed by PipelineOrchestrator).

New tables:
- usage_records — one row per interview or screening call
- usage_daily_summary — pre-aggregated per company per day
- plan_limits — per-company monthly caps (used in Sprint 24)

Hard rule: usage tracking must NEVER block or fail the main
flow. If usageMetrics is null or malformed, log it and continue —
never throw an exception that affects candidate status updates.

Aggregation is computed by a nightly @Scheduled job, not live
on every request. The admin dashboard reads from 
usage_daily_summary, never directly from usage_records 
for display (only for CSV export/drill-down).

## Tenant Management API (Sprint 22)
Super Admin-only endpoints that consolidate three things 
into one cohesive API surface:
1. Tenant onboarding (replaces public /api/auth/register 
   as the primary path going forward — admin creates tenants)
2. Usage visibility (reads from usage_records / usage_daily_summary 
   built in Sprint 21)
3. User administration (add users to any tenant without 
   requiring self-invite flow)

All endpoints under /api/admin/tenants/** — already covered 
by the @PreAuthorize("hasRole('SUPER_ADMIN')") pattern and 
the accessDeniedHandler from Sprint 21.

Hard rule: every endpoint here can see across ALL companies — 
this is the one place in the app where company_id scoping is 
intentionally bypassed. Every method must be triple-checked 
for SUPER_ADMIN-only access since a leak here exposes every 
tenant's data.

The existing /api/auth/register stays functional for now 
(self-serve signup) — Sprint 23 will decide whether to keep 
both paths or deprecate self-serve.

## Seed credentials (local dev only)
admin@aiinterview.com / password   (SUPER_ADMIN)
hr@acmetech.com / password          (COMPANY_ADMIN)

These are set via V4-fix-seed-passwords.xml using 
BCryptPasswordEncoder(10). Never use these patterns 
in any non-local environment.
