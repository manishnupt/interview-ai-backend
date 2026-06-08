# Python AI Service Integration

## Overview

The Spring Boot backend integrates with a Python AI service for two core operations:
1. **Resume Screening** — AI scores a candidate's resume against a job description
2. **Interview Triggering** — AI initiates a phone interview with the candidate (via Twilio) and reports back results asynchronously

Python service runs on port `8000` (configured via `PYTHON_AI_URL`, default `http://localhost:8001`).

---

## How Calls Are Made

**File:** `pipeline/AiServiceClient.java`

Uses Spring `WebClient` (from `spring-boot-starter-webflux`) for non-blocking HTTP calls. The base URL is set from `app.python-ai-service.url` in `application.yml`.

```yaml
app:
  python-ai-service:
    url: ${PYTHON_AI_URL:http://localhost:8001}
```

WebClient is configured with a 2 MB in-memory buffer size. All calls are blocking (`.block()`) at the orchestrator layer since the pipeline runs on background threads.

---

## When Calls Are Triggered

There are two trigger mechanisms: **scheduled** and **manual**.

### Scheduled (Automatic)

**File:** `pipeline/PipelineScheduler.java`

| Scheduler | Interval | What it does |
|-----------|----------|--------------|
| Screening batch | every 30 min | Fetches all `APPLIED` candidates and screens each one |
| Interview batch | every 15 min | Fetches all `SHORTLISTED` candidates without a report, triggers interview for each (5s delay between calls) |

### Manual (Admin-triggered via UI)

**File:** `pipeline/PipelineController.java`

| Endpoint | Role required | What it does |
|----------|--------------|--------------|
| `POST /api/pipeline/run-screening` | `COMPANY_ADMIN` or `SUPER_ADMIN` | Runs screening batch immediately (async) |
| `POST /api/pipeline/trigger-interview/{candidateId}` | `COMPANY_ADMIN` or `SUPER_ADMIN` | Triggers interview for one specific `SHORTLISTED` candidate |
| `GET /api/pipeline/health` | `COMPANY_ADMIN` or `SUPER_ADMIN` | Checks if Python service is reachable |

---

## Call 1: Resume Screening

### When triggered
Candidate status is `APPLIED` and no prior `ScreeningResult` exists for them.

### Request sent to Python

`POST /screen`  — timeout: 120 seconds

```json
{
  "candidate_id": 42,
  "resume_url": "https://s3.amazonaws.com/.../resume.pdf",
  "job_description": "We are looking for a Senior Java Engineer...",
  "job_id": 7,
  "company_id": 3
}
```

### Response received from Python

```json
{
  "candidate_id": 42,
  "fit": true,
  "score": 8,
  "match_percentage": 82,
  "fit_reasons": ["Strong Java experience", "Led distributed systems"],
  "concerns": ["No cloud certifications"],
  "missing_skills": ["Kubernetes", "Terraform"]
}
```

### What gets stored in the database

**Table: `screening_results`** — Entity: `ScreeningResult`

| Column | Type | Value stored |
|--------|------|-------------|
| `candidate_id` | BIGINT (unique) | from response |
| `company_id` | BIGINT | from candidate |
| `score` | INTEGER | `score` (0–10) |
| `match_percentage` | INTEGER | `match_percentage` (0–100) |
| `fit` | BOOLEAN | `fit` |
| `fit_reasons` | TEXT | pipe-delimited list |
| `concerns` | TEXT | pipe-delimited list |
| `missing_skills` | TEXT | pipe-delimited list |
| `raw_json` | TEXT | full response JSON (optional) |
| `screened_at` | TIMESTAMP | time of save |

### Candidate status after screening

| Condition | New status | Email sent |
|-----------|-----------|------------|
| `score >= job.screeningThreshold` | `SHORTLISTED` | Yes — candidate notified |
| `score < job.screeningThreshold` | `REJECTED` | Yes — rejection email |
| Exception / Python unreachable | Reverted to `APPLIED` | No |

During the call, status is temporarily set to `SCREENING`.

---

## Call 2: Interview Trigger

### When triggered
Candidate status is `SHORTLISTED` and no `InterviewReport` exists for them.

### Request sent to Python

`POST /interview`  — timeout: 30 seconds

```json
{
  "candidate_id": 42,
  "phone": "+14155550123",
  "candidate_name": "Jane Doe",
  "resume_url": "https://s3.amazonaws.com/.../resume.pdf",
  "job_description": "We are looking for a Senior Java Engineer...",
  "job_id": 7,
  "company_id": 3
}
```

### Response received from Python (immediate)

Python returns immediately after initiating the Twilio call — it does **not** wait for the interview to finish.

```json
{
  "candidate_id": 42,
  "call_sid": "CA1234abcd5678efgh",
  "status": "initiated"
}
```

### What gets stored immediately

- Candidate status updated to `HR_REVIEW` (interview in progress)
- `call_sid` is stored on the candidate record for tracking

---

## Call 3: Interview Callback (Python → Backend)

After the phone interview completes, Python calls back the Spring Boot backend.

### Callback endpoint

`POST /api/callbacks/interview-complete`  — **unauthenticated, internal only**

**File:** `pipeline/CallbackController.java`

### Payload sent by Python to the backend

```json
{
  "candidateId": 42,
  "jobId": 7,
  "companyId": 3,
  "callSid": "CA1234abcd5678efgh",
  "status": "completed",
  "score": 7,
  "strengths": ["Clear communicator", "Strong system design"],
  "weaknesses": ["Nervous under pressure"],
  "recommendation": "Proceed to HR round",
  "summary": "Candidate demonstrated solid backend knowledge...",
  "fullTranscript": "Interviewer: Tell me about yourself...",
  "rawJson": "{...}"
}
```

`status` can be `"completed"`, `"timeout"`, or `"error"`.

### What gets stored in the database

**Table: `interview_reports`** — Entity: `InterviewReport`

| Column | Type | Value stored |
|--------|------|-------------|
| `candidate_id` | BIGINT (unique) | from payload |
| `company_id` | BIGINT | from payload |
| `score` | INTEGER | 0–10 |
| `strengths` | TEXT | pipe-delimited list |
| `weaknesses` | TEXT | pipe-delimited list |
| `recommendation` | VARCHAR | text recommendation |
| `summary` | TEXT | short summary |
| `full_transcript` | TEXT | full interview transcript |
| `raw_json` | TEXT | raw response JSON |
| `interviewed_at` | TIMESTAMP | time of save |

### Candidate status after callback

| `status` value | New candidate status | Action |
|----------------|---------------------|--------|
| `"completed"` | `INTERVIEWED` | Save report, email HR team |
| `"timeout"` | `SHORTLISTED` | Revert — allow retry |
| `"error"` | `SHORTLISTED` | Revert — allow retry |

---

## Complete Candidate Status Flow

```
APPLIED
  │
  ▼  (screening batch picks up)
SCREENING
  │
  ├─ score >= threshold ──► SHORTLISTED ──► email candidate
  │
  └─ score < threshold  ──► REJECTED    ──► email candidate
        │
        ▼  (interview batch picks up)
    HR_REVIEW  (interview call in progress)
        │
        ├─ callback status=completed ──► INTERVIEWED ──► email HR
        │
        └─ callback status=timeout/error ──► SHORTLISTED (retryable)
```

---

## Testing the Integration from the UI

The `POST /api/pipeline/run-screening` and `POST /api/pipeline/trigger-interview/{candidateId}` endpoints can be called from the admin UI to manually trigger the pipeline without waiting for the scheduler.

**Steps to test end-to-end:**

1. Register a company + create a job with a `screeningThreshold` (e.g. 5)
2. Submit a candidate application with a valid resume URL
3. Hit `POST /api/pipeline/run-screening` from the admin panel
4. Watch candidate status change: `APPLIED → SCREENING → SHORTLISTED/REJECTED`
5. Check `screening_results` table for the stored AI output
6. If shortlisted, hit `POST /api/pipeline/trigger-interview/{candidateId}`
7. Watch candidate status change to `HR_REVIEW`
8. Python will call back `POST /api/callbacks/interview-complete` when the call ends
9. Candidate moves to `INTERVIEWED`; `interview_reports` row is created

**Health check before testing:**
```
GET /api/pipeline/health
```
Returns whether the Python service at `PYTHON_AI_URL` is reachable.

---

## Key File Reference

| File | Responsibility |
|------|---------------|
| `pipeline/AiServiceClient.java` | WebClient calls to Python (`/screen`, `/interview`, `/health`) |
| `pipeline/PipelineOrchestrator.java` | Business logic — screening + interview trigger flows |
| `pipeline/PipelineScheduler.java` | Automatic scheduling (every 30min / 15min) |
| `pipeline/PipelineController.java` | Manual admin trigger endpoints |
| `pipeline/CallbackController.java` | Receives async interview-complete callback from Python |
| `candidates/ScreeningResult.java` | Entity — `screening_results` table |
| `candidates/InterviewReport.java` | Entity — `interview_reports` table |
| `candidates/Candidate.java` | Entity — holds status, phone, resume URL |
| `resources/application.yml` | `app.python-ai-service.url` config |
