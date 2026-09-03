// ============================================================
// 📁 _db.ts — VLESSHUB BOT DATA PLANE (Supabase via service_role)
// ============================================================
// Faithful port of vlesshub/telegram-bot/bot.py's Supabase REST calls.
// Runs with the service_role client (bypasses RLS, same trust level as
// the Python bot). Tables: servers, vpn_files, scraper_proxies,
// scraper_config.
// ============================================================

import type { SupabaseClient } from 'jsr:@supabase/supabase-js@2';

export const PAGE_SIZE = 5;
export const CONTACT_PAGE_SIZE = 10;

export interface ServerRow {
  id: number;
  name: string;
  flag: string;
  config: string;
}

export interface FileRow {
  id: number;
  filename: string;
  size_bytes: number | null;
  source_channel: string | null;
  is_encrypted: boolean | null;
}

export interface ProxyRow {
  id: number;
  host: string;
  port: number;
  secret?: string | null;
  source?: string | null;
  is_active?: boolean | null;
  last_ok?: boolean | null;
}

export async function countRows(supabase: any, table: string): Promise<number> {
  try {
    const { count, error } = await supabase
      .from(table)
      .select('id', { count: 'exact', head: true });
    if (error) {
      console.warn('[db] count', table, 'failed:', error.message);
      return 0;
    }
    return count ?? 0;
  } catch (e) {
    console.warn('[db] count', table, 'threw:', (e as Error).message);
    return 0;
  }
}

export async function listServers(
  supabase: any,
  page: number,
  fields = 'id,name,flag',
): Promise<ServerRow[]> {
  try {
    const { data, error } = await supabase
      .from('servers')
      .select(fields)
      .order('id', { ascending: false })
      .range(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE - 1);
    if (error) {
      console.warn('[db] listServers failed:', error.message);
      return [];
    }
    return (data ?? []) as ServerRow[];
  } catch (e) {
    console.warn('[db] listServers threw:', (e as Error).message);
    return [];
  }
}

export async function getServersByIds(
  supabase: any,
  ids: number[],
  fields = 'id,name,config',
): Promise<ServerRow[]> {
  if (ids.length === 0) return [];
  try {
    const { data, error } = await supabase
      .from('servers')
      .select(fields)
      .in('id', ids);
    if (error) {
      console.warn('[db] getServersByIds failed:', error.message);
      return [];
    }
    return (data ?? []) as ServerRow[];
  } catch (e) {
    console.warn('[db] getServersByIds threw:', (e as Error).message);
    return [];
  }
}

export async function listFiles(
  supabase: any,
  page: number,
  fields = 'id,filename,size_bytes,source_channel,is_encrypted',
): Promise<FileRow[]> {
  try {
    const { data, error } = await supabase
      .from('vpn_files')
      .select(fields)
      .order('id', { ascending: false })
      .range(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE - 1);
    if (error) {
      console.warn('[db] listFiles failed:', error.message);
      return [];
    }
    return (data ?? []) as FileRow[];
  } catch (e) {
    console.warn('[db] listFiles threw:', (e as Error).message);
    return [];
  }
}

export async function getFilesByIds(
  supabase: any,
  ids: number[],
  fields = 'id,filename,content',
): Promise<any[]> {
  if (ids.length === 0) return [];
  try {
    const { data, error } = await supabase
      .from('vpn_files')
      .select(fields)
      .in('id', ids);
    if (error) {
      console.warn('[db] getFilesByIds failed:', error.message);
      return [];
    }
    return (data ?? []) as any[];
  } catch (e) {
    console.warn('[db] getFilesByIds threw:', (e as Error).message);
    return [];
  }
}

export async function listProxies(
  supabase: any,
  fields = 'id,host,port,source,is_active,last_ok',
): Promise<ProxyRow[]> {
  try {
    const { data, error } = await supabase
      .from('scraper_proxies')
      .select(fields)
      .order('id', { ascending: false });
    if (error) {
      console.warn('[db] listProxies failed:', error.message);
      return [];
    }
    return (data ?? []) as ProxyRow[];
  } catch (e) {
    console.warn('[db] listProxies threw:', (e as Error).message);
    return [];
  }
}

export async function getProxiesByIds(supabase: any, ids: number[]): Promise<ProxyRow[]> {
  if (ids.length === 0) return [];
  try {
    const { data, error } = await supabase
      .from('scraper_proxies')
      .select('id,host,port,secret')
      .in('id', ids);
    if (error) {
      console.warn('[db] getProxiesByIds failed:', error.message);
      return [];
    }
    return (data ?? []) as ProxyRow[];
  } catch (e) {
    console.warn('[db] getProxiesByIds threw:', (e as Error).message);
    return [];
  }
}

export async function bulkDeleteByIds(supabase: any, table: string, ids: number[]): Promise<number> {
  // Single batched DELETE (IN) instead of one round-trip per id.
  try {
    const { error, count } = await supabase
      .from(table)
      .delete({ count: 'exact' })
      .in('id', ids);
    if (error) {
      console.warn('[db] bulk delete from', table, 'failed:', error.message);
      return 0;
    }
    // count may be null when the header isn't echoed — fall back to the
    // number of ids we asked to delete.
    return count ?? ids.length;
  } catch (e) {
    console.warn('[db] bulk delete from', table, 'threw:', (e as Error).message);
    return 0;
  }
}

export async function deleteAllServers(supabase: any): Promise<boolean> {
  try {
    const { error } = await supabase.from('servers').delete().gte('id', 0);
    return !error;
  } catch (e) {
    console.warn('[db] deleteAllServers threw:', (e as Error).message);
    return false;
  }
}

export async function getConfig(supabase: any, key: string): Promise<string | null> {
  try {
    const { data, error } = await supabase
      .from('scraper_config')
      .select('value')
      .eq('key', key)
      .maybeSingle();
    if (error || !data) return null;
    return data.value ?? null;
  } catch (e) {
    console.warn('[db] getConfig threw:', (e as Error).message);
    return null;
  }
}

/** Upsert a scraper_config key. Returns true on success. */
export async function setConfig(supabase: any, key: string, value: string): Promise<boolean> {
  try {
    const { error } = await supabase
      .from('scraper_config')
      .upsert({ key, value, updated_at: new Date().toISOString() });
    return !error;
  } catch (e) {
    console.warn('[db] setConfig threw:', (e as Error).message);
    return false;
  }
}

export async function getChannels(supabase: any): Promise<string[]> {
  const value = await getConfig(supabase, 'vless_channels');
  if (!value) return [];
  return value
    .split(',')
    .map((c: string) => c.trim().replace(/^@/, ''))
    .filter((c: string) => c.length > 0);
}

export async function setChannels(supabase: any, channels: string[]): Promise<boolean> {
  try {
    const { error } = await supabase
      .from('scraper_config')
      .upsert({
        key: 'vless_channels',
        value: channels.join(','),
        updated_at: new Date().toISOString(),
      });
    return !error;
  } catch (e) {
    console.warn('[db] setChannels threw:', (e as Error).message);
    return false;
  }
}

export async function addChannel(supabase: any, raw: string): Promise<boolean> {
  const name = raw.trim().replace(/^@/, '');
  if (!name) return false;
  const current = await getChannels(supabase);
  if (current.includes(name)) return true;
  return await setChannels(supabase, [...current, name]);
}

export async function deleteChannel(supabase: any, raw: string): Promise<boolean> {
  const name = raw.trim().replace(/^@/, '');
  if (!name) return false;
  const current = await getChannels(supabase);
  const next = current.filter((c) => c !== name);
  return await setChannels(supabase, next);
}

/** Add or re-activate an MTProto proxy (host:port:secret). Returns true
 *  if the proxy was added/reactivated. */
export async function addProxy(
  supabase: any,
  host: string,
  port: number,
  secret: string | null,
  source = 'bot',
): Promise<boolean> {
  try {
    const { data, error } = await supabase
      .from('scraper_proxies')
      .select('id')
      .eq('host', host)
      .eq('port', port)
      .maybeSingle();
    if (error) {
      console.warn('[db] addProxy lookup failed:', error.message);
      return false;
    }
    const row: Record<string, unknown> = {
      host,
      port,
      source,
      is_active: true,
      deactivated_at: null,
    };
    if (secret !== null) row.secret = secret;
    if (data) {
      const { error: upErr } = await supabase
        .from('scraper_proxies')
        .update(row)
        .eq('id', data.id);
      return !upErr;
    }
    const { error: insErr } = await supabase.from('scraper_proxies').insert(row);
    return !insErr;
  } catch (e) {
    console.warn('[db] addProxy threw:', (e as Error).message);
    return false;
  }
}

/** Remove proxies by host, id, or "all". Returns count deleted. */
export interface InsertEntry {
  config: string;
  configFormat: string;
  type: string;
  host?: string;
  port?: number;
  name?: string;
}

export async function insertServer(
  supabase: any,
  entry: InsertEntry,
  nameOverride?: string,
): Promise<boolean> {
  const host = entry.host || '';
  const port = entry.port || 443;    const name = nameOverride ?? entry.name ?? (host || entry.type);
  const type = entry.type || 'vless';

  try {
    const { error } = await supabase.from('servers').insert({
      name,
      flag: '🌐',
      country: 'Community',
      config: entry.config,
      host,
      port,
      is_active: true,
      type,
      config_format: entry.configFormat,
    });
    if (error) {
      console.warn('[db] insertServer failed:', error.message);
      return false;
    }
    return true;
  } catch (e) {
    console.warn('[db] insertServer threw:', (e as Error).message);
    return false;
  }
}

export async function checkDuplicate(supabase: any, config: string): Promise<boolean> {
  try {
    const { data, error } = await supabase
      .from('servers')
      .select('id')
      .eq('config', config)
      .limit(1);
    if (error) return false;
    return (data ?? []).length > 0;
  } catch {
    return false;
  }
}

export async function saveVpnFile(
  supabase: any,
  file: { filename: string; mime_type: string | null; size_bytes: number; contentBase64: string; uploaded_by: number | null },
): Promise<{ saved: boolean; duplicate: boolean }> {
  try {
    const { data: existing } = await supabase
      .from('vpn_files')
      .select('id')
      .eq('filename', file.filename)
      .limit(1);
    if (existing && existing.length > 0) return { saved: false, duplicate: true };
    const { error } = await supabase.from('vpn_files').insert({
      filename: file.filename,
      mime_type: file.mime_type,
      size_bytes: file.size_bytes,
      content: file.contentBase64,
      uploaded_by: file.uploaded_by,
    });
    return { saved: !error, duplicate: false };
  } catch {
    return { saved: false, duplicate: false };
  }
}

// ─── Contact requests (vlesshub_contact) ────────────────────────────

export interface ContactRow {
  id: number;
  user_id: number;
  username: string | null;
  first_name: string | null;
  status: string;
  last_message: string | null;
  admin_reply: string | null;
  created_at: string;
  updated_at: string;
}

export async function getContactByUserId(supabase: any, userId: number): Promise<ContactRow | null> {
  try {
    const { data, error } = await supabase
      .from('vlesshub_contact')
      .select('*')
      .eq('user_id', userId)
      .maybeSingle();
    return error || !data ? null : (data as ContactRow);
  } catch (e) {
    console.warn('[db] getContactByUserId threw:', (e as Error).message);
    return null;
  }
}

/** One contact request per user — returns 'created', 'exists' or 'failed'. */
export async function createContactRequest(
  supabase: any,
  userId: number,
  username: string | null,
  firstName: string | null,
): Promise<'created' | 'exists' | 'failed'> {
  try {
    const existing = await getContactByUserId(supabase, userId);
    if (existing) return 'exists';
    const { error } = await supabase.from('vlesshub_contact').insert({
      user_id: userId,
      username: username ?? null,
      first_name: firstName ?? null,
      status: 'requested',
    });
    return error ? 'failed' : 'created';
  } catch (e) {
    console.warn('[db] createContactRequest threw:', (e as Error).message);
    return 'failed';
  }
}

export async function setContactStatus(supabase: any, userId: number, status: string): Promise<boolean> {
  try {
    const { error } = await supabase
      .from('vlesshub_contact')
      .update({ status, updated_at: new Date().toISOString() })
      .eq('user_id', userId);
    return !error;
  } catch (e) {
    console.warn('[db] setContactStatus threw:', (e as Error).message);
    return false;
  }
}

export async function setContactMessage(supabase: any, userId: number, message: string): Promise<boolean> {
  try {
    const { error } = await supabase
      .from('vlesshub_contact')
      .update({ last_message: message, updated_at: new Date().toISOString() })
      .eq('user_id', userId);
    return !error;
  } catch (e) {
    console.warn('[db] setContactMessage threw:', (e as Error).message);
    return false;
  }
}

export async function setContactAdminReply(supabase: any, userId: number, reply: string): Promise<boolean> {
  try {
    const { error } = await supabase
      .from('vlesshub_contact')
      .update({ admin_reply: reply, updated_at: new Date().toISOString() })
      .eq('user_id', userId);
    return !error;
  } catch (e) {
    console.warn('[db] setContactAdminReply threw:', (e as Error).message);
    return false;
  }
}

export async function listContacts(supabase: any, page: number): Promise<ContactRow[]> {
  try {
    const { data, error } = await supabase
      .from('vlesshub_contact')
      .select('*')
      .order('updated_at', { ascending: false })
      .range(page * CONTACT_PAGE_SIZE, page * CONTACT_PAGE_SIZE + CONTACT_PAGE_SIZE - 1);
    return error ? [] : ((data ?? []) as ContactRow[]);
  } catch (e) {
    console.warn('[db] listContacts threw:', (e as Error).message);
    return [];
  }
}

export async function countContacts(supabase: any): Promise<number> {
  return countRows(supabase, 'vlesshub_contact');
}

export async function deleteContact(supabase: any, userId: number): Promise<boolean> {
  try {
    const { error } = await supabase
      .from('vlesshub_contact')
      .delete()
      .eq('user_id', userId);
    return !error;
  } catch (e) {
    console.warn('[db] deleteContact threw:', (e as Error).message);
    return false;
  }
}

export async function deleteProxies(supabase: any, target: string): Promise<number | null> {
  try {
    if (target.trim().toLowerCase() === 'all') {
      const { error } = await supabase.from('scraper_proxies').delete().gte('id', 0);
      return error ? null : -1;
    }
    const asId = Number(target);
    if (!Number.isNaN(asId)) {
      const { error, count } = await supabase
        .from('scraper_proxies')
        .delete({ count: 'exact' })
        .eq('id', asId);
      return error ? null : (count ?? 0);
    }
    const { error, count } = await supabase
      .from('scraper_proxies')
      .delete({ count: 'exact' })
      .eq('host', target.trim());
    return error ? null : (count ?? 0);
  } catch (e) {
    console.warn('[db] deleteProxies threw:', (e as Error).message);
    return null;
  }
}
