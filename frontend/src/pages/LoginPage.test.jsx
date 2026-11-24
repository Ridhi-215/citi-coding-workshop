import { fireEvent, screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import LoginPage from './LoginPage';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../services/apiClient';
import { renderAt } from '../test/renderWithAuth';

jest.mock('../context/AuthContext', () => ({ useAuth: jest.fn() }));

const renderPage = () => renderAt(
  <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<p>register page</p>} />
    <Route path="/" element={<p>home page</p>} />
  </Routes>,
  '/login',
);

test('does not show demo credentials and links to register', () => {
  useAuth.mockReturnValue({ user: null, login: jest.fn(), sessionExpired: false });
  renderPage();
  expect(screen.queryByText(/Demo@1234|demo account/i)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('link', { name: 'Register' }));
  expect(screen.getByText('register page')).toBeInTheDocument();
});

test('explains an expired session', () => {
  useAuth.mockReturnValue({ user: null, login: jest.fn(), sessionExpired: true });
  renderPage();
  expect(screen.getByText(/session has expired/i)).toBeInTheDocument();
});

test('validates, signs in and shows server errors', async () => {
  const login = jest.fn()
    .mockRejectedValueOnce(new ApiError(401, 'INVALID_CREDENTIALS', 'Email or password is incorrect'))
    .mockResolvedValueOnce({});
  useAuth.mockReturnValue({ user: null, login, sessionExpired: false });
  renderPage();
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
  expect(screen.getByText('Enter a valid email address')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText(/Email/), { target: { value: 'a@acme.com' } });
  fireEvent.change(screen.getByLabelText(/Password/), { target: { value: 'x' } });
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
  expect(await screen.findByText('Email or password is incorrect')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
  await waitFor(() => expect(screen.getByText('home page')).toBeInTheDocument());
  expect(login).toHaveBeenLastCalledWith('a@acme.com', 'x');
});
