import { api, tokenStore } from './apiClient';

const SERVICE = 'auth-service';

function startSession(session) {
  tokenStore.set({ access_token: session.access_token, refresh_token: session.refresh_token });
  return session.user;
}

export async function login(email, password) {
  return startSession(await api(SERVICE, '/login', { method: 'POST', body: { email, password }, auth: false }));
}

/** Self sign-up; the server always creates a Viewer and returns a session. */
export async function register({ full_name, email, password }) {
  return startSession(await api(SERVICE, '/register', {
    method: 'POST', body: { full_name, email, password }, auth: false,
  }));
}

export function fetchMe() {
  return api(SERVICE, '/me');
}

export function logout() {
  tokenStore.clear();
}
