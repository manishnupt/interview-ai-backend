# AI Interview Platform — Backend Architecture

## Table of Contents

1. [Overview](#overview)
2. [Tech Stack](#tech-stack)
3. [System Architecture](#system-architecture)
4. [Package Structure](#package-structure)
5. [Database Schema](#database-schema)
6. [API Reference](#api-reference)
7. [Implemented Features](#implemented-features)
8. [End-to-End Flows](#end-to-end-flows)
9. [Security Model](#security-model)
10. [External Integrations](#external-integrations)
11. [Background Jobs (Scheduler)](#background-jobs-scheduler)
12. [Configuration](#configuration)

---

## Overview

Multi-tenant SaaS platform that automates the candidate screening and interview pipeline using AI. Companies post jobs, candidates apply via a public embeddable form, a Python AI service screens resumes and conducts voice interviews, and HR teams review results through a dashboard.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.3.x |
| Security | Spring Security + JWT (jjwt, HS256) |
| Persistence | Spring Data JPA + Hibernate + PostgreSQL 16 |
| Migrations | Liquibase |
| Resume Storage | AWS S3 (presigned URLs, 1-hour expiry) |
| Email | SendGrid (transactional) |
| AI Service comms | Spring WebClient (non-blocking HTTP) |
| Build | Maven |
| Boilerplate | Lombok |

---

## System Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                        React Frontend                          │
│                    http://localhost:5173                        │
└───────────────────────────┬────────────────────────────────────┘
                            │ REST / JSON
                            ▼
┌────────────────────────────────────────────────────────────────┐
│               Spring Boot Backend  :4040                        │
│                                                                 │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  ┌──────────────┐  │
│  │   Auth   │  │   Jobs   │  │Candidates │  │  Pipeline    │  │
│  │ /api/auth│  │ /api/jobs│  │/api/cands │  │/api/pipeline │  │
│  └──────────┘  └──────────┘  └───────────┘  └──────┬───────┘  │
│                                                     │          │
│  ┌────────────┐  ┌──────────────┐  ┌────────────┐  │          │
│  │ Magic Link │  │  Callbacks   │  │  Settings  │  │          │
│  │/api/magic  │  │/api/callbacks│  │/api/setting│  │          │
│  └────────────┘  └──────┬───────┘  └────────────┘  │          │
│                         │                           │          │
│  ┌──────────────────────┼─────────────────────┐    │          │
│  │       PipelineScheduler (Spring @Scheduled) │◄───┘          │
│  │  • every 30 min → runScreeningBatch()       │               │
│  │  • every 15 min → scheduledInterviewTrigger │               │
│  └──────────────────────┬────────────────────-─┘               │
└─────────────────────────┼──────────────────────────────────────┘
                          │ WebClient (HTTP)
                          ▼
┌────────────────────────────────────────────────────────────────┐
│               Python AI Service  :8000                          │
│   POST /screen    → resume screening (returns score/fit)        │
│   POST /interview → triggers Twilio voice call                  │
│   GET  /health    → liveness check                              │
└────────────────────────────────────────────────────────────────┘
         ▲
         │ POST /api/callbacks/interview-complete
         │ (Python calls back when call ends)
         │
┌────────┴──────────────────────────────────────────────────────┐
│                  External Services                              │
│   AWS S3       — resume storage (PDF only, max 5 MB)           │
│   SendGrid     — transactional email                           │
│   Twilio       — voice calls (owned by Python service)         │
└────────────────────────────────────────────────────────────────┘
```

---

## Package Structure

```
com.aiinterview.backend
│
├── BackendApplication.java          ← entry point, @EnableScheduling
│
├── auth/                            ← authentication & authorization
│   ├── AuthController.java          ← /api/auth/**
│   ├── AuthService.java             ← register, login, invite, accept-invite
│   ├── AuthenticatedUser.java       ← record: userId, companyId, role, email
│   ├── CustomUserDetailsService.java
│   ├── JwtAuthFilter.java           ← OncePerRequestFilter
│   ├── JwtUtil.java                 ← generate / validate / extract JWT
│   ├── SecurityConfig.java          ← Spring Security, CORS, route rules
│   ├── Role.java                    ← SUPER_ADMIN | COMPANY_ADMIN | HR_MEMBER
│   ├── User.java                    ← @Entity users table
│   ├── UserRepository.java
│   ├── InviteToken.java             ← @Entity invite_tokens table
│   ├── InviteTokenRepository.java
│   └── dto/
│       ├── RegisterCompanyRequest.java
│       ├── LoginRequest.java
│       ├── InviteUserRequest.java
│       ├── AcceptInviteRequest.java
│       └── AuthResponse.java
│
├── company/
│   ├── Company.java                 ← @Entity companies table
│   └── CompanyRepository.java
│
├── jobs/
│   ├── JobController.java           ← /api/jobs/**  (authenticated)
│   ├── PublicJobController.java     ← /api/public/jobs/{slug}
│   ├── JobService.java              ← CRUD, publish/unpublish/close, slug gen
│   ├── Job.java                     ← @Entity jobs table
│   ├── JobRepository.java
│   ├── JobStatus.java               ← DRAFT | PUBLISHED | CLOSED
│   └── dto/
│       ├── CreateJobRequest.java
│       ├── UpdateJobRequest.java
│       ├── JobResponse.java         ← includes embedCode, applyUrl, counts
│       └── JobSummaryResponse.java
│
├── candidates/
│   ├── CandidateController.java     ← /api/candidates/**  (authenticated)
│   ├── PublicCandidateController.java ← /api/public/apply/{jobSlug}
│   ├── CandidateService.java        ← apply, list, status update, notes
│   ├── Candidate.java               ← @Entity candidates table
│   ├── CandidateRepository.java
│   ├── CandidateStatus.java         ← APPLIED→SCREENING→SHORTLISTED→INTERVIEWED→HR_REVIEW→OFFERED|REJECTED
│   ├── HrNote.java                  ← @Entity hr_notes table
│   ├── HrNoteRepository.java
│   ├── InterviewReport.java         ← @Entity interview_reports table
│   ├── InterviewReportRepository.java
│   ├── ScreeningResult.java         ← @Entity screening_results table
│   ├── ScreeningResultRepository.java
│   └── dto/
│       ├── ApplyRequest.java
│       ├── CandidateResponse.java   ← full view with screening + interview + notes
│       ├── CandidateSummaryResponse.java
│       ├── AddNoteRequest.java
│       ├── HrNoteResponse.java
│       ├── ScreeningResultDto.java
│       └── InterviewReportDto.java
│
├── pipeline/
│   ├── PipelineController.java      ← /api/pipeline/**  (COMPANY_ADMIN+)
│   ├── CallbackController.java      ← /api/callbacks/**  (public, called by Python)
│   ├── PipelineOrchestrator.java    ← screenCandidate(), triggerInterview(), runScreeningBatch()
│   ├── PipelineScheduler.java       ← @Scheduled jobs
│   └── AiServiceClient.java         ← WebClient wrapper for Python AI service
│
├── magiclink/
│   ├── MagicLinkController.java     ← /api/magic-link/**  (public)
│   ├── MagicLinkService.java        ← generate token, send email, resolve status
│   ├── MagicLink.java               ← @Entity magic_links table
│   ├── MagicLinkRepository.java
│   └── MagicLinkStatusResponse.java ← candidate-facing status view
│
├── notifications/
│   └── EmailService.java            ← SendGrid wrapper (6 email types)
│
├── files/
│   ├── S3Config.java                ← S3Client + S3Presigner beans
│   └── S3FileService.java           ← upload, presign, delete, validate
│
├── settings/
│   └── SettingsController.java      ← /api/settings/team  (team member list)
│
└── common/
    ├── ApiResponse.java             ← { success, message, data }
    ├── BaseEntity.java              ← id, createdAt, updatedAt
    ├── BusinessException.java       ← custom exception with HTTP status
    ├── ResourceNotFoundException.java
    ├── GlobalExceptionHandler.java  ← @RestControllerAdvice
    └── SecurityUtils.java           ← getCurrentUser() from SecurityContext
```

---

## Database Schema

### Tables

#### `companies`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| name | VARCHAR(255) | |
| slug | VARCHAR(100) UNIQUE | URL-safe identifier |
| plan | VARCHAR(50) | default: `trial` |
| is_active | BOOLEAN | |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

#### `users`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| company_id | BIGINT FK → companies | multi-tenant FK |
| name | VARCHAR(255) | |
| email | VARCHAR(255) UNIQUE | |
| password_hash | VARCHAR(255) | BCrypt strength 10 |
| role | VARCHAR(50) | SUPER_ADMIN / COMPANY_ADMIN / HR_MEMBER |
| is_active | BOOLEAN | |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

#### `invite_tokens`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| token | VARCHAR(255) UNIQUE | UUID |
| email | VARCHAR(255) | invitee email |
| name | VARCHAR(255) | invitee name |
| role | VARCHAR(50) | role to assign |
| company_id | BIGINT | scope |
| expires_at | TIMESTAMP | 48 hours TTL |
| used | BOOLEAN | one-time use |

#### `jobs`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| company_id | BIGINT FK → companies | |
| title | VARCHAR(255) | |
| description | TEXT | used for AI screening |
| required_experience_years | INT | |
| required_skills | TEXT | |
| screening_threshold | INT | min score to shortlist (default: 6) |
| status | VARCHAR(50) | DRAFT / PUBLISHED / CLOSED |
| slug | VARCHAR(255) UNIQUE | `{company-slug}-{title-slug}` |
| openings_count | INT | |
| created_by | BIGINT FK → users | |
| created_at / updated_at | TIMESTAMP | |

#### `candidates`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| company_id | BIGINT FK → companies | |
| job_id | BIGINT FK → jobs | |
| name | VARCHAR(255) | |
| email | VARCHAR(255) | |
| phone | VARCHAR(50) | used for voice interview |
| resume_url | TEXT | presigned S3 URL (refreshed on GET) |
| resume_s3_key | TEXT | permanent S3 key |
| status | VARCHAR(50) | see CandidateStatus |
| applied_at | TIMESTAMP | |
| created_at / updated_at | TIMESTAMP | |

#### `screening_results`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| candidate_id | BIGINT FK UNIQUE | one result per candidate |
| company_id | BIGINT FK | |
| score | INT | 0–10 from Python AI |
| match_percentage | INT | |
| fit | BOOLEAN | |
| fit_reasons | TEXT | pipe-delimited |
| concerns | TEXT | pipe-delimited |
| missing_skills | TEXT | pipe-delimited |
| screened_at | TIMESTAMP | |

#### `interview_reports`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| candidate_id | BIGINT FK UNIQUE | one report per candidate |
| company_id | BIGINT FK | |
| score | INT | 0–10 from AI interview |
| strengths | TEXT | pipe-delimited |
| weaknesses | TEXT | pipe-delimited |
| recommendation | VARCHAR(50) | e.g. HIRE / HOLD / REJECT |
| summary | TEXT | |
| full_transcript | TEXT | verbatim call transcript |
| raw_json | TEXT | raw Python response |
| interviewed_at | TIMESTAMP | |

#### `magic_links`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| token | VARCHAR(255) UNIQUE | double UUID, URL-safe |
| candidate_id | BIGINT FK → candidates | |
| expires_at | TIMESTAMP | 48 hours TTL |
| used | BOOLEAN | |

#### `hr_notes`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| candidate_id | BIGINT FK | |
| company_id | BIGINT FK | |
| author_id | BIGINT FK → users | |
| content | TEXT | |
| created_at | TIMESTAMP | |

### Liquibase Migrations (applied in order)
```
V1 — initial-schema.xml     → creates all 8 core tables + indexes
V2 — seed-data.xml          → dev seed data
V3 — add-audit-columns.xml  → audit timestamps
V3 — invite-tokens.xml      → invite_tokens table
V4 — candidates-constraints.xml → unique email+job constraint
V4 — fix-seed-passwords.xml → re-hashes dev passwords
```

---

## API Reference

All responses are wrapped in `ApiResponse<T>`:
```json
{ "success": true, "message": "Success", "data": { ... } }
```

### Authentication — `/api/auth/**` (public unless noted)

| Method | Path | Auth | Body / Params | Description |
|---|---|---|---|---|
| POST | `/api/auth/register` | Public | `RegisterCompanyRequest` | Creates company + COMPANY_ADMIN user, returns JWT |
| POST | `/api/auth/login` | Public | `LoginRequest` | Validates credentials, returns JWT |
| GET | `/api/auth/me` | Authenticated | — | Returns current user profile |
| POST | `/api/auth/invite` | COMPANY_ADMIN+ | `InviteUserRequest` | Generates invite token, sends email |
| POST | `/api/auth/accept-invite` | Public | `AcceptInviteRequest` | Consumes invite token, creates user, returns JWT |

**JWT payload**: `{ sub: userId, companyId, role, email, iat, exp }`
**Token delivery**: `Authorization: Bearer <token>` header OR `jwt` HttpOnly cookie.

---

### Jobs — `/api/jobs/**` (authenticated)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/jobs` | COMPANY_ADMIN+ | Create job (status: DRAFT, auto-generates slug) |
| GET | `/api/jobs` | Any | List all jobs for caller's company |
| GET | `/api/jobs/{id}` | Any | Get single job with applicant counts |
| PUT | `/api/jobs/{id}` | COMPANY_ADMIN+ | Partial update (any field) |
| POST | `/api/jobs/{id}/publish` | COMPANY_ADMIN+ | Set status → PUBLISHED |
| POST | `/api/jobs/{id}/unpublish` | COMPANY_ADMIN+ | Set status → DRAFT |
| POST | `/api/jobs/{id}/close` | COMPANY_ADMIN+ | Set status → CLOSED |
| DELETE | `/api/jobs/{id}` | COMPANY_ADMIN+ | Delete (DRAFT only) |

**`JobResponse`** includes: `embedCode` (iframe HTML), `applyUrl`, `applicantCount`, `shortlistedCount`.

---

### Public — `/api/public/**` (no auth)

| Method | Path | Description |
|---|---|---|
| GET | `/api/public/jobs/{slug}` | Fetch published job details for apply page |
| POST | `/api/public/apply/{jobSlug}` | Submit candidate application (multipart: form fields + PDF resume) |

`ApplyRequest` fields: `name`, `email`, `phone`. Resume is optional (`resume` multipart file, PDF only, max 5 MB).

---

### Candidates — `/api/candidates/**` (authenticated)

| Method | Path | Description |
|---|---|---|
| GET | `/api/candidates/job/{jobId}` | All candidates for a job (sorted newest first) |
| GET | `/api/candidates/status/{status}` | Filter candidates by status across company |
| GET | `/api/candidates/{id}` | Full candidate view: profile + screening result + interview report + notes |
| PUT | `/api/candidates/{id}/status?newStatus=` | Manually override candidate status |
| POST | `/api/candidates/{id}/notes` | Add HR note to candidate |
| GET | `/api/candidates/{id}/notes` | List all notes (newest first) |

All queries are scoped to the caller's `companyId` extracted from JWT.

---

### Pipeline — `/api/pipeline/**` (COMPANY_ADMIN+)

| Method | Path | Description |
|---|---|---|
| POST | `/api/pipeline/run-screening` | Manually kick off screening batch (async) |
| POST | `/api/pipeline/trigger-interview/{candidateId}` | Manually trigger AI interview for a SHORTLISTED candidate |
| GET | `/api/pipeline/health` | Check Spring Boot + Python AI service health |

---

### Callbacks — `/api/callbacks/**` (public — called by Python)

| Method | Path | Description |
|---|---|---|
| POST | `/api/callbacks/interview-complete` | Python AI service posts interview result here when call ends |

**Payload** includes: `candidateId`, `companyId`, `callSid`, `status` (completed/timeout/error), `score`, `strengths`, `weaknesses`, `recommendation`, `summary`, `fullTranscript`, `rawJson`.

On `completed`: saves `InterviewReport`, sets candidate status to `INTERVIEWED`, emails all HR users.
On `timeout` / `error`: reverts candidate to `SHORTLISTED`.

---

### Magic Links — `/api/magic-link/**` (public)

| Method | Path | Description |
|---|---|---|
| POST | `/api/magic-link/send?email=` | Looks up latest application by email, generates token, sends status-check email |
| GET | `/api/magic-link/status/{token}` | Returns candidate-facing status view (no internal IDs exposed) |

**`MagicLinkStatusResponse`** includes: `candidateName`, `status`, `statusLabel`, `statusDescription`, `screeningScore`, `interviewScore`, `recommendation`, `appliedAt`. Token expires after 48 hours.

---

### Settings — `/api/settings/**` (authenticated)

| Method | Path | Description |
|---|---|---|
| GET | `/api/settings/team` | List all users in caller's company |

---

## Implemented Features

### Auth & User Management
- Company self-registration (creates company + COMPANY_ADMIN in one call)
- JWT authentication (HS256, 24-hour expiry, HttpOnly cookie + Bearer header)
- Role-based access control: SUPER_ADMIN > COMPANY_ADMIN > HR_MEMBER
- Team invite flow: signed token → email → recipient sets password
- `GET /auth/me` for session restoration

### Job Management
- Full CRUD for job postings
- Job lifecycle: DRAFT → PUBLISHED → CLOSED
- Auto-generated slug: `{company-slug}-{title-slug}` (collision-safe with numeric suffix)
- Embeddable apply form (iframe HTML returned with each job)
- Screening threshold per job (configurable score, default 6/10)

### Candidate Application
- Public multipart apply form: name, email, phone, PDF resume
- Duplicate application prevention (same email + job)
- PDF validation (type + 5 MB size limit)
- Resume upload to AWS S3, presigned URL generated on retrieval (1-hour expiry)
- Application confirmation email via SendGrid

### AI Screening Pipeline
- Automated: scheduler polls every 30 minutes for `APPLIED` candidates
- Calls Python AI service `POST /screen` with resume URL + job description
- Saves `ScreeningResult`: score, matchPercentage, fit, fitReasons, concerns, missingSkills
- Auto-decision: score ≥ threshold → `SHORTLISTED` + shortlist email; below → `REJECTED` + rejection email
- Skip logic: already screened candidates are skipped; no-resume candidates are auto-rejected

### AI Voice Interview Pipeline
- Automated: scheduler polls every 15 minutes for `SHORTLISTED` candidates without an interview report
- Calls Python AI service `POST /interview` with candidate phone + job description
- Python service places Twilio voice call asynchronously; status set to `HR_REVIEW`
- On call completion, Python POSTs to `/api/callbacks/interview-complete`
- Saves `InterviewReport`: score, strengths, weaknesses, recommendation, full transcript
- Notifies all HR users in the company via email

### Manual Pipeline Controls
- `POST /api/pipeline/run-screening` — trigger screening batch on demand
- `POST /api/pipeline/trigger-interview/{id}` — trigger interview for a specific shortlisted candidate
- `GET /api/pipeline/health` — verify Python AI service connectivity

### HR Workflow
- View candidates by job or by status
- Full candidate profile with screening result + interview report + notes in one API call
- Manual status override (supports full status range)
- Add / view timestamped HR notes per candidate

### Candidate Self-Service (Magic Links)
- Candidates request status link by email (no account needed)
- Tokenized link (48-hour TTL) renders status in plain language (e.g. "You've been shortlisted!")
- No internal IDs or raw data exposed to candidates

### Settings
- Team member listing (name, email, role, active flag) scoped to company

### Infrastructure
- Multi-tenancy: every entity carries `companyId`, every query filters by it
- GlobalExceptionHandler: validation errors, 404s, auth errors, business rules all return consistent `ApiResponse`
- Liquibase versioned migrations
- CORS configured for React dev server (`localhost:5173`) + configurable `FRONTEND_URL`
- All config via environment variables, no hardcoded secrets

---

## End-to-End Flows

### 1. Company Onboarding

```
HR Admin → POST /api/auth/register
           { companyName, adminName, adminEmail, password }
                ↓
           Company row created (slug auto-generated)
           User row created (role: COMPANY_ADMIN)
           JWT issued
                ↓
           HR Admin logs in, creates jobs via POST /api/jobs
           Publishes job via POST /api/jobs/{id}/publish
```

### 2. Team Expansion (Invite Flow)

```
COMPANY_ADMIN → POST /api/auth/invite
                { email, name, role }
                     ↓
                InviteToken saved (48-hour TTL)
                SendGrid email → invitee@company.com
                     ↓
Invitee → POST /api/auth/accept-invite
          { token, name, password }
               ↓
          Token validated (not used, not expired)
          User created with role from token
          JWT issued → invitee is now logged in
```

### 3. Candidate Application Flow

```
Candidate → GET /api/public/jobs/{slug}
            (sees job title, description, apply URL)
                 ↓
            POST /api/public/apply/{jobSlug}
            multipart: { name, email, phone, resume.pdf }
                 ↓
            Job existence + PUBLISHED status checked
            Duplicate application check
            PDF validated (type, size ≤ 5 MB)
            Candidate saved (status: APPLIED)
            Resume uploaded to S3 → s3Key stored
            Presigned URL generated and stored
            SendGrid confirmation email sent
                 ↓
            CandidateResponse returned (201 Created)
```

### 4. Automated Screening Flow

```
PipelineScheduler (every 30 min)
       ↓
candidateRepository.findAllByStatus(APPLIED)
       ↓ for each candidate
PipelineOrchestrator.screenCandidate()
       ↓
  [skip if already in screening_results]
  [auto-reject + email if no resume]
       ↓
  candidate.status → SCREENING (saved)
       ↓
  AiServiceClient.screenResume()
  POST http://python-service:8000/screen
  {candidate_id, resume_url, job_description, job_id, company_id}
       ↓  (up to 120s timeout)
  ScreenResponse {score, matchPercentage, fit, fitReasons, concerns, missingSkills}
       ↓
  ScreeningResult saved to DB
       ↓
  score ≥ job.screeningThreshold?
  YES → status: SHORTLISTED + shortlist email
  NO  → status: REJECTED + rejection email
```

### 5. Automated Interview Flow

```
PipelineScheduler (every 15 min)
       ↓
candidateRepository.findAllByStatus(SHORTLISTED)
  filter: no entry in interview_reports
       ↓ for each, with 5s delay between
PipelineOrchestrator.triggerInterview()
       ↓
  AiServiceClient.triggerInterview()
  POST http://python-service:8000/interview
  {candidate_id, phone, candidate_name, resume_url, job_description, job_id, company_id}
       ↓  (30s timeout)
  InterviewTriggerResponse {callSid, status}
       ↓
  candidate.status → HR_REVIEW (call in progress)

  [Python places Twilio call — async, takes ~10 min]
       ↓
Python AI Service → POST /api/callbacks/interview-complete
  { candidateId, status: "completed", score, strengths,
    weaknesses, recommendation, summary, fullTranscript, rawJson }
       ↓
CallbackController
  InterviewReport saved to DB
  candidate.status → INTERVIEWED
  All HR users in company emailed: "Interview complete — {name} scored {score}/10"
```

### 6. HR Review Flow

```
HR Member logs in → GET /api/candidates/job/{jobId}
                    (list view: name, status, scores)
                         ↓
                    GET /api/candidates/{id}
                    (full view: profile + screening result +
                     interview report + transcript + notes)
                         ↓
                    POST /api/candidates/{id}/notes
                    { content: "Strong Python background, follow up on system design" }
                         ↓
                    PUT /api/candidates/{id}/status?newStatus=OFFERED
```

### 7. Candidate Status Self-Check (Magic Link)

```
Candidate → POST /api/magic-link/send?email=candidate@email.com
            (looks up latest application by email)
                 ↓
            MagicLink token generated (double UUID, 48h TTL)
            SendGrid email → candidate with URL: {frontendUrl}/status/{token}
                 ↓
Candidate → GET /api/magic-link/status/{token}
                 ↓
            Token validated (exists, not expired)
            Candidate fetched → ScreeningResult + InterviewReport joined
            Returns human-readable status:
            { status: "SHORTLISTED",
              statusLabel: "Shortlisted",
              statusDescription: "Great news — you've been shortlisted! Expect a call soon.",
              screeningScore: 8, interviewScore: null }
```

---

## Security Model

### Route Classification

| Pattern | Access |
|---|---|
| `OPTIONS /**` | Allow all (CORS preflight) |
| `/api/auth/register`, `/api/auth/login`, `/api/auth/accept-invite` | Public |
| `/api/public/**` | Public (apply form, job view) |
| `/api/callbacks/**` | Public (Python AI service callback) |
| `/api/magic-link/**` | Public (candidate self-service) |
| `/api/auth/me`, `/api/auth/invite` | Authenticated |
| `/api/admin/**` | SUPER_ADMIN only |
| `/api/**` (everything else) | Any authenticated user |

### Role Hierarchy

```
SUPER_ADMIN
    └── COMPANY_ADMIN
            └── HR_MEMBER
```

Role-specific endpoints use `@PreAuthorize("hasAnyRole('COMPANY_ADMIN','SUPER_ADMIN')")`.

### JWT Details

- Algorithm: HS256
- Payload: `{ sub: userId, companyId, role, email }`
- Expiry: 24 hours (configurable via `app.jwt.expiration-ms`)
- Delivery: `Authorization: Bearer <token>` header **or** `jwt` HttpOnly cookie (both supported simultaneously)
- Extraction order: Authorization header first, then cookie fallback

### Multi-Tenancy Enforcement

- JWT carries `companyId` — never read from request body
- Every repository query includes `companyId` filter
- Every entity has a `companyId` column
- `SecurityUtils.getCurrentUser()` is the only way services read identity

### Password Security

- BCrypt with strength 10
- Never stored or returned in plain text
- `AuthResponse` never includes `passwordHash`

---

## External Integrations

### AWS S3

- SDK: `software.amazon.awssdk:s3`
- Bucket: configurable via `AWS_S3_BUCKET` env var (default: `ai-interview-resumes-prod`)
- Region: configurable via `AWS_REGION` (default: `ap-south-1`)
- Key pattern: `resumes/{jobId}/{candidateId}/{candidateId}-{uuid8}.pdf`
- Presigned URL expiry: 1 hour (regenerated fresh on every `GET /candidates/{id}`)
- File validation: PDF content-type only, max 5 MB

### SendGrid

- 6 transactional emails implemented:

| Method | Trigger | Recipient |
|---|---|---|
| `sendInviteEmail` | HR invites team member | Invitee |
| `sendApplicationConfirmation` | Candidate applies | Candidate |
| `sendRejectionEmail` | AI screening fails | Candidate |
| `sendShortlistEmail` | AI screening passes | Candidate |
| `sendInterviewCompleteToHR` | Interview callback received | All HR users in company |
| `sendMagicLink` | Magic link requested | Candidate |

- Stub mode: if `SENDGRID_API_KEY` is blank, emails are logged to stdout instead of sent (safe for local dev).

### Python AI Service

- Base URL: configurable via `PYTHON_AI_URL` (default: `http://localhost:8001`)
- Endpoints called:

| Endpoint | Timeout | Purpose |
|---|---|---|
| `POST /screen` | 120s | Resume screening |
| `POST /interview` | 30s | Trigger Twilio voice call |
| `GET /health` | 5s | Liveness check |

- Max response body: 2 MB (WebClient codec configured)
- Error handling: 4xx/5xx from Python mapped to `BusinessException`

---

## Background Jobs (Scheduler)

Spring `@EnableScheduling` on main class. Two scheduled tasks:

### Screening Batch (`fixedDelay = 30 minutes`)
```
PipelineScheduler.scheduledScreeningRun()
  → PipelineOrchestrator.runScreeningBatch()
  → fetches all APPLIED candidates across ALL companies
  → screens each one sequentially
  → logs: Screened / Shortlisted / Rejected / Errors counts
```

### Interview Trigger (`fixedDelay = 15 minutes`)
```
PipelineScheduler.scheduledInterviewTrigger()
  → fetches all SHORTLISTED candidates
  → filters to those without an interview_report
  → calls triggerInterview() for each, with 5s sleep between calls
    (prevents Twilio rate limiting)
```

Both jobs can also be triggered manually via `/api/pipeline/run-screening` and `/api/pipeline/trigger-interview/{id}`.

---

## Configuration

All config in `application.yml`. Override with environment variables for prod. Local dev overrides go in `application-local.yml`.

```yaml
server:
  port: 4040                         # Spring Boot port

spring:
  datasource:
    url: jdbc:postgresql://${PGHOST:localhost}:${PGPORT:5434}/${PGDATABASE:ai_interview_db}
    username: ${PGUSER:postgres}
    password: ${PGPASSWORD:admin}
  jpa:
    hibernate.ddl-auto: validate      # Liquibase owns DDL
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
  servlet.multipart:
    max-file-size: 5MB
    max-request-size: 10MB

app:
  jwt:
    secret: ${JWT_SECRET}             # min 32-char secret
    expiration-ms: 86400000           # 24 hours
  python-ai-service:
    url: ${PYTHON_AI_URL:http://localhost:8001}
  frontend-url: ${FRONTEND_URL:http://localhost:5173}
  aws:
    bucket: ${AWS_S3_BUCKET}
    region: ${AWS_REGION:ap-south-1}
    access-key: ${AWS_ACCESS_KEY_ID}
    secret-key: ${AWS_SECRET_ACCESS_KEY}
  sendgrid:
    api-key: ${SENDGRID_API_KEY}
    from-email: noreply@aiinterview.com
    from-name: AI Interview Platform
```

### Key Ports (local dev)

| Service | Port |
|---|---|
| Spring Boot backend | 4040 |
| React frontend | 5173 |
| Python AI service | 8000 / 8001 |
| PostgreSQL | 5434 |
