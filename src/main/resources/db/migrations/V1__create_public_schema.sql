-- V1__create_public_schema.sql
-- Description: Initial public schema — SaaS layer tables
-- All tenant data lives in separate schemas (tenant_{slug})
-- See: docs/database.md and docs/multi-tenancy.md

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Workspaces (one per psychology practice) ───────────────────────────────
CREATE TABLE public.workspaces (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(200)     NOT NULL,
    slug             VARCHAR(100)     NOT NULL UNIQUE,  -- used as schema name: tenant_{slug}
    plan             VARCHAR(50)      NOT NULL DEFAULT 'FREE',
    primary_color    VARCHAR(7),                        -- hex e.g. #1976D2
    secondary_color  VARCHAR(7),
    logo_url         TEXT,
    status           VARCHAR(50)      NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

-- ── Users (all users across all workspaces) ────────────────────────────────
CREATE TABLE public.users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID         NOT NULL REFERENCES public.workspaces(id),
    email           VARCHAR(255) NOT NULL UNIQUE,
    hashed_password VARCHAR(255) NOT NULL,
    role            VARCHAR(50)  NOT NULL,   -- OWNER | THERAPIST | ADMIN_STAFF
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_workspace_id ON public.users (workspace_id);
CREATE INDEX idx_users_email        ON public.users (email);

-- ── Refresh tokens ────────────────────────────────────────────────────────
CREATE TABLE public.refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES public.users(id),
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_user_id    ON public.refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON public.refresh_tokens (token_hash);

-- ── Feature flags per workspace ───────────────────────────────────────────
CREATE TABLE public.workspace_features (
    workspace_id  UUID         NOT NULL REFERENCES public.workspaces(id),
    feature_key   VARCHAR(100) NOT NULL,
    is_enabled    BOOLEAN      NOT NULL,
    enabled_at    TIMESTAMPTZ,
    enabled_by    UUID REFERENCES public.users(id),
    PRIMARY KEY (workspace_id, feature_key)
);

-- ── Google OAuth tokens per workspace ─────────────────────────────────────
CREATE TABLE public.workspace_google_tokens (
    workspace_id             UUID PRIMARY KEY REFERENCES public.workspaces(id),
    access_token_encrypted   TEXT        NOT NULL,
    refresh_token_encrypted  TEXT        NOT NULL,
    expires_at               TIMESTAMPTZ NOT NULL,
    calendar_ids             TEXT[]      NOT NULL DEFAULT '{}',
    last_sync_at             TIMESTAMPTZ,
    last_sync_status         VARCHAR(50),   -- SUCCESS | PARTIAL | FAILED
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
