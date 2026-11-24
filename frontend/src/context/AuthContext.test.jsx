import { act, render, screen, waitFor } from '@testing-library/react';
import { AuthProvider, useAuth } from './AuthContext';
import { api, tokenStore } from '../services/apiClient';

const json = (status, body) => Promise.resolve({ status, ok: status < 400, json: () => Promise.resolve(body) });

function Probe() {
  const { user, sessionExpired } = useAuth();
  return <p>{user ? `signed in as ${user.role}` : 'signed out'}{sessionExpired ? ' (expired)' : ''}</p>;
}

beforeEach(() => {
  localStorage.clear();
  global.fetch = jest.fn();
});

test('restores the session from stored tokens', async () => {
  tokenStore.set({ access_token: 'A', refresh_token: 'R' });
  fetch.mockReturnValue(json(200, { id: 1, role: 'viewer', permissions: ['READ'] }));
  render(<AuthProvider><Probe /></AuthProvider>);
  expect(await screen.findByText('signed in as viewer')).toBeInTheDocument();
});

test('an expired session signs the user out and flags it for the login page', async () => {
  tokenStore.set({ access_token: 'A', refresh_token: 'R' });
  fetch.mockReturnValueOnce(json(200, { id: 1, role: 'viewer', permissions: ['READ'] }));
  render(<AuthProvider><Probe /></AuthProvider>);
  await screen.findByText('signed in as viewer');

  fetch
    .mockReturnValueOnce(json(401, { error: { code: 'TOKEN_EXPIRED', message: 'expired' } }))
    .mockReturnValueOnce(json(401, { error: { code: 'INVALID_TOKEN', message: 'refresh expired' } }));
  await act(async () => {
    await api('projects-service', '/projects').catch(() => {});
  });
  await waitFor(() => expect(screen.getByText('signed out (expired)')).toBeInTheDocument());
  expect(tokenStore.get()).toBeNull();
});
