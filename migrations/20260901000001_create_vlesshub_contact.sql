-- vlesshub_contact — moderated user→admin contact for the VlessHub bot.
-- Non-admin users request contact (one row per user, unique user_id); the
-- admin approves from the bot's "✉️ Messages" menu; then both sides exchange
-- short (≤50 word) messages stored on the row. The bot writes via the
-- service_role key (bypasses RLS) — no anon/authenticated access.
create table if not exists vlesshub_contact (
  id bigserial primary key,
  user_id bigint not null unique,
  username text,
  first_name text,
  status text not null default 'requested',  -- requested | approved
  last_message text,
  admin_reply text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table vlesshub_contact enable row level security;
