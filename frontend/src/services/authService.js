import { api, tokenStore } from './apiClient';

const SERVICE = 'auth-service';

export async function login(email, password) {
  const session = await api(SERVICE, '/login', { method: 'POST', body: { email, password }, auth: false });
  tokenStore.set({ access_token: session.access_token, refresh_token: session.refresh_token });
  return session.user;
}

export function fetchMe() {
  return api(SERVICE, '/me');
}

export function logout() {
  tokenStore.clear();
}
