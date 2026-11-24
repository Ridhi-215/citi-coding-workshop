// Small fetch wrapper for the ACME backend services.
// URL: `${VITE_API_URL}/api/<service>/<path>` (CloudFront in AWS, the dev proxy on :3001 locally).
// Stores tokens in localStorage and refreshes an expired access token once, transparently.

const BASE_URL = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '');
const TOKENS_KEY = 'acme.tokens';

export class ApiError extends Error {
  constructor(status, code, message, details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details || null;
  }
}

export const tokenStore = {
  get() {
    try {
      return JSON.parse(localStorage.getItem(TOKENS_KEY)) || null;
    } catch {
      return null;
    }
  },
  set(tokens) {
    try {
      localStorage.setItem(TOKENS_KEY, JSON.stringify(tokens));
    } catch {
      // Private mode: the session just won't survive a reload.
    }
  },
  clear() {
    try {
      localStorage.removeItem(TOKENS_KEY);
    } catch {
      // ignore
    }
  },
};

let onSessionExpired = () => {};
export function setSessionExpiredHandler(handler) {
  onSessionExpired = handler;
}

export function buildUrl(service, path, query) {
  const params = new URLSearchParams();
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.append(key, value);
  });
  const qs = params.toString();
  return `${BASE_URL}/api/${service}${path}${qs ? `?${qs}` : ''}`;
}

async function send(service, path, { method = 'GET', body, query, auth = true } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const tokens = tokenStore.get();
  if (auth && tokens?.access_token) headers.Authorization = `Bearer ${tokens.access_token}`;

  let response;
  try {
    response = await fetch(buildUrl(service, path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server. Check your connection and try again.');
  }
  if (response.status === 204) return null;
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const err = data?.error || {};
    throw new ApiError(response.status, err.code || 'HTTP_' + response.status,
      err.message || 'Something went wrong', err.details);
  }
  return data;
}

let refreshing = null;
async function refreshTokens() {
  const tokens = tokenStore.get();
  if (!tokens?.refresh_token) throw new ApiError(401, 'UNAUTHORIZED', 'Please sign in');
  if (!refreshing) {
    refreshing = send('auth-service', '/refresh', {
      method: 'POST',
      body: { refresh_token: tokens.refresh_token },
      auth: false,
    }).then((session) => {
      tokenStore.set({ access_token: session.access_token, refresh_token: session.refresh_token });
      return session;
    }).finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

export async function api(service, path, options = {}) {
  try {
    return await send(service, path, options);
  } catch (err) {
    if (err.status === 401 && options.auth !== false && err.code === 'TOKEN_EXPIRED') {
      try {
        await refreshTokens();
      } catch {
        tokenStore.clear();
        onSessionExpired();
        throw new ApiError(401, 'SESSION_EXPIRED', 'Your session has expired. Please sign in again.');
      }
      return send(service, path, options);
    }
    if (err.status === 401 && options.auth !== false) {
      tokenStore.clear();
      onSessionExpired();
    }
    throw err;
  }
}
