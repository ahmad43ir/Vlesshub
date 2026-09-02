-- ============================================================
-- 📁 20260831000001_create_vlesshub_bot_state.sql
-- ============================================================
-- VlessHub Telegram bot (edge function, webhook mode) — persistence.
--
-- The Python long-poll bot (vlesshub/telegram-bot/bot.py) kept per-chat
-- UI state (multi-select sets for servers/files/proxies + the pending
-- "add/remove" input mode) in module memory. Edge functions are
-- stateless (cold starts, multiple isolates), so the TypeScript rewrite
-- persists that state here.
--
--   vlesshub_bot_state — per-chat UI state:
--     selected    jsonb  { "servers": [...], "files": [...], "proxies": [...] }
--     pending     text   non-null when waiting for an input value
--                        ("addproxy"|"delproxy"|"addchannel"|"delchannel")
--
-- SECURITY: RLS enabled with NO policies. Only service_role (used by the
-- edge function) can read/write — anon/authenticated cannot.
-- ============================================================

CREATE TABLE IF NOT EXISTS public.vlesshub_bot_state (
  chat_id    BIGINT PRIMARY KEY,
  selected   JSONB NOT NULL DEFAULT '{"servers":[],"files":[],"proxies":[]}'::jsonb,
  pending    TEXT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.vlesshub_bot_state ENABLE ROW LEVEL SECURITY;

COMMENT ON TABLE public.vlesshub_bot_state IS 'VlessHub bot per-chat UI state (single admin) — edge functions are stateless, so multi-select + pending-input state lives here. service_role only.';
