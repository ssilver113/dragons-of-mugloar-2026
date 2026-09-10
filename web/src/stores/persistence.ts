/**
 * One JSON value in Web Storage, with every operation guarded. Storage is not reliably there:
 * Safari's private mode throws on the property access itself, an origin can be storage-blocked,
 * and a long solver run can fill the quota. None of that is worth interrupting a game for.
 *
 * Values carry a version, so a payload from an older build is discarded rather than cast to a
 * shape it no longer has. Bump `VERSION` whenever a stored shape changes.
 */
const VERSION = 1

interface Envelope<T> {
  version: number
  value: T
}

export interface Persisted<T> {
  read(): T | null
  write(value: T): void
  clear(): void
}

export type StorageArea = 'session' | 'local'

export function persisted<T>(area: StorageArea, key: string): Persisted<T> {
  return {
    read: () => {
      const raw = storage(area)?.getItem(key)
      if (!raw) {
        return null
      }
      try {
        const envelope = JSON.parse(raw) as Envelope<T>
        return envelope?.version === VERSION ? envelope.value : null
      } catch {
        return null
      }
    },

    write: (value: T) => {
      try {
        storage(area)?.setItem(
          key,
          JSON.stringify({ version: VERSION, value } satisfies Envelope<T>),
        )
      } catch {
        // Quota, or a serialiser that met something it could not describe. Either way the game
        // is unaffected; only the memory of it is.
      }
    },

    clear: () => {
      try {
        storage(area)?.removeItem(key)
      } catch {
        // As above.
      }
    },
  }
}

function storage(area: StorageArea): Storage | null {
  try {
    return area === 'session' ? window.sessionStorage : window.localStorage
  } catch {
    return null
  }
}
