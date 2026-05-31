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
- ALL database changes go through Flyway migration scripts
- ALWAYS use ResponseEntity in controllers

## Key ports
- Spring Boot: 4040
- Python AI service: 8000
- React frontend: 5173
- PostgreSQL: 5434

## Environment
All config in application.yml — never hardcode values.
Local dev uses application-local.yml for overrides.
