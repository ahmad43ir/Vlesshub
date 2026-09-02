// ============================================================
// 📁 _state.ts — VLESSHUB BOT PER-CHAT STATE (vlesshub_bot_state)
// ============================================================
// The Python bot kept multi-select sets + pending-input mode in module
// memory. Edge functions are stateless, so these live in Postgres
// (migration 20260831000001). service_role only.
// ============================================================

export interface Selections {
  servers: number[];
  files: number[];
  proxies: number[];
}

export interface VlesshubChatState {
  chatId: number;
  selected: Selections;
  pending: string | null;
}

const EMPTY: Selections = { servers: [], files: [], proxies: [] };

export async function getState(supabase: any, chatId: number): Promise<VlesshubChatState> {
  try {
    const { data, error } = await supabase
      .from('vlesshub_bot_state')
      .select('selected, pending')
      .eq('chat_id', chatId)
      .maybeSingle();
    if (error || !data) {
      return { chatId, selected: { servers: [], files: [], proxies: [] }, pending: null };
    }
    return {
      chatId,
      selected: {
        servers: Array.isArray(data.selected?.servers)
          ? data.selected.servers.map(Number)
          : [],
        files: Array.isArray(data.selected?.files) ? data.selected.files.map(Number) : [],
        proxies: Array.isArray(data.selected?.proxies)
          ? data.selected.proxies.map(Number)
          : [],
      },
      pending: typeof data.pending === 'string' && data.pending ? data.pending : null,
    };
  } catch (e) {
    console.warn('[state] getState failed:', (e as Error).message);
    return { chatId, selected: { servers: [], files: [], proxies: [] }, pending: null };
  }
}

export async function saveState(supabase: any, state: VlesshubChatState): Promise<void> {
  try {
    await supabase.from('vlesshub_bot_state').upsert(
      {
        chat_id: state.chatId,
        selected: state.selected,
        pending: state.pending,
        updated_at: new Date().toISOString(),
      },
      { onConflict: 'chat_id' },
    );
  } catch (e) {
    console.warn('[state] saveState failed:', (e as Error).message);
  }
}

export function emptyState(chatId: number): VlesshubChatState {
  return { chatId, selected: { servers: [], files: [], proxies: [] }, pending: null };
}

const WEBHOOK_SECRET_KEY = 'vlesshub_webhook_secret';

export async function getWebhookSecret(supabase: any): Promise<string | null> {
  try {
    const { data, error } = await supabase
      .from('bot_config')
      .select('value')
      .eq('key', WEBHOOK_SECRET_KEY)
      .maybeSingle();
    return error || !data ? null : (data.value ?? null);
  } catch (e) {
    console.warn('[state] getWebhookSecret failed:', (e as Error).message);
    return null;
  }
}

export async function saveWebhookSecret(supabase: any, secret: string): Promise<void> {
  try {
    await supabase.from('bot_config').upsert({
      key: WEBHOOK_SECRET_KEY,
      value: secret,
      updated_at: new Date().toISOString(),
    });
  } catch (e) {
    console.warn('[state] saveWebhookSecret failed:', (e as Error).message);
  }
}
