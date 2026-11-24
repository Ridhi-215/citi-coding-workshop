import { api, ApiError, buildUrl, setSessionExpiredHandler, tokenStore } from './apiClient';

const json = (status, body) => Promise.resolve({ status, ok: status < 400, json: () => Promise.resolve(body) });

beforeEach(() => {
  global.fetch = jest.fn();
  localStorage.clear();
});

test('builds service URLs and skips empty query values', () => {
  expect(buildUrl('projects-service', '/projects', { search: 'a b', status: '', at_risk: null }))
    .toBe('/api/projects-service/projects?search=a+b');
});

test('sends the bearer token and returns JSON', async () => {
  tokenStore.set({ access_token: 'A', refresh_token: 'R' });
  fetch.mockReturnValue(json(200, { ok: 1 }));
  await expect(api('projects-service', '/projects')).resolves.toEqual({ ok: 1 });
  expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer A');
});

test('maps the error format to ApiError', async () => {
  fetch.mockReturnValue(json(403, { error: { code: 'FORBIDDEN', message: 'No', details: null } }));
  await expect(api('auth-service', '/users')).rejects.toMatchObject({ status: 403, code: 'FORBIDDEN', message: 'No' });
});

test('refreshes once on TOKEN_EXPIRED and retries', async () => {
  tokenStore.set({ access_token: 'old', refresh_token: 'R' });
  fetch
    .mockReturnValueOnce(json(401, { error: { code: 'TOKEN_EXPIRED', message: 'expired' } }))
    .mockReturnValueOnce(json(200, { access_token: 'new', refresh_token: 'R2' }))
    .mockReturnValueOnce(json(200, { items: [] }));
  await expect(api('projects-service', '/projects')).resolves.toEqual({ items: [] });
  expect(fetch.mock.calls[2][1].headers.Authorization).toBe('Bearer new');
  expect(tokenStore.get().refresh_token).toBe('R2');
});

test('signs out when refresh fails', async () => {
  const expired = jest.fn();
  setSessionExpiredHandler(expired);
  tokenStore.set({ access_token: 'old', refresh_token: 'bad' });
  fetch
    .mockReturnValueOnce(json(401, { error: { code: 'TOKEN_EXPIRED', message: 'expired' } }))
    .mockReturnValueOnce(json(401, { error: { code: 'INVALID_TOKEN', message: 'bad' } }));
  await expect(api('projects-service', '/projects')).rejects.toMatchObject({ code: 'SESSION_EXPIRED' });
  expect(expired).toHaveBeenCalled();
  expect(tokenStore.get()).toBeNull();
});

test('network failure becomes a friendly error', async () => {
  fetch.mockRejectedValue(new TypeError('Failed to fetch'));
  const err = await api('projects-service', '/projects').catch((e) => e);
  expect(err).toBeInstanceOf(ApiError);
  expect(err.code).toBe('NETWORK_ERROR');
});
