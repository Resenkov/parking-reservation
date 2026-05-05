import type { Session } from '../types/models'

const STORAGE_KEY = 'parking.frontend.token'

function normalizeRoles(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return ['USER']
  }

  const roles = value
    .map((entry) => String(entry ?? '').replace(/^ROLE_/, '').trim())
    .filter(Boolean)

  return roles.length > 0 ? roles : ['USER']
}

export function decodeSession(token: string): Session | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) {
      return null
    }

    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as {
      sub?: string
      userId?: number | string
      roles?: unknown
    }

    return {
      token,
      email: String(json.sub ?? ''),
      userId:
        typeof json.userId === 'number'
          ? json.userId
          : typeof json.userId === 'string' && json.userId.trim() !== ''
            ? Number.parseInt(json.userId, 10)
            : null,
      roles: normalizeRoles(json.roles),
    }
  } catch {
    return null
  }
}

export function loadSession(): Session | null {
  const token = window.localStorage.getItem(STORAGE_KEY)
  return token ? decodeSession(token) : null
}

export function persistSession(token: string): Session | null {
  window.localStorage.setItem(STORAGE_KEY, token)
  return decodeSession(token)
}

export function clearPersistedSession(): void {
  window.localStorage.removeItem(STORAGE_KEY)
}
