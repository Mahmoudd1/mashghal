/**
 * Thin wrapper over the browser's localStorage, with an in-memory fallback.
 *
 * Two reasons not to touch `localStorage` directly:
 *  - the bare global resolves to Node's own `localStorage` under the test
 *    runner, not the browser one, and jsdom's `window.localStorage` is not
 *    usable there either;
 *  - private-browsing modes throw on read or write instead of returning null.
 *
 * When real storage is unavailable the fallback keeps the app behaving
 * correctly for the lifetime of the tab; only persistence across reloads is
 * lost, which is acceptable for a language choice and a session token.
 */
const memory = new Map<string, string>();

function browserStorage(): Storage | null {
  try {
    const candidate = globalThis.window?.localStorage;
    if (!candidate) {
      return null;
    }
    // Safari in private mode exposes the object but throws on write.
    const probe = '__apparel_probe__';
    candidate.setItem(probe, probe);
    candidate.removeItem(probe);
    return candidate;
  } catch {
    return null;
  }
}

export const localStore = {
  get(key: string): string | null {
    try {
      const storage = browserStorage();
      return storage ? storage.getItem(key) : (memory.get(key) ?? null);
    } catch {
      return memory.get(key) ?? null;
    }
  },

  getJson<T>(key: string): T | null {
    const raw = localStore.get(key);
    if (raw === null) {
      return null;
    }
    try {
      return JSON.parse(raw) as T;
    } catch {
      return null;
    }
  },

  set(key: string, value: string): void {
    memory.set(key, value);
    try {
      browserStorage()?.setItem(key, value);
    } catch {
      // The in-memory copy above is the fallback.
    }
  },

  remove(key: string): void {
    memory.delete(key);
    try {
      browserStorage()?.removeItem(key);
    } catch {
      // Already gone from memory.
    }
  },

  /** Test hook: drops the in-memory copies. */
  clear(): void {
    memory.clear();
  },
};
