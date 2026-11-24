import { fireEvent, screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import RegisterPage from './RegisterPage';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../services/apiClient';
import { renderAt } from '../test/renderWithAuth';

jest.mock('../context/AuthContext', () => ({ useAuth: jest.fn() }));
const mockNotify = jest.fn();
jest.mock('../context/NotifyContext', () => ({ useNotify: () => mockNotify }));

let register;
beforeEach(() => {
  jest.clearAllMocks();
  register = jest.fn();
  useAuth.mockReturnValue({ user: null, register });
});

const renderPage = () => renderAt(
  <Routes>
    <Route path="/register" element={<RegisterPage />} />
    <Route path="/login" element={<p>login page</p>} />
    <Route path="/dashboard" element={<p>dashboard page</p>} />
  </Routes>,
  '/register',
);

const fill = (values) => Object.entries(values).forEach(([label, value]) => {
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
});
const submit = () => fireEvent.click(screen.getByRole('button', { name: /create account/i }));
const VALID = { 'Full name': 'Nina New', Email: 'nina@acme.com', Password: 'Secret123', 'Confirm password': 'Secret123' };

test('shows inline errors for empty required fields', () => {
  renderPage();
  submit();
  expect(screen.getByText('Full name is required')).toBeInTheDocument();
  expect(screen.getByText('Email is required')).toBeInTheDocument();
  expect(screen.getByText('Password is required')).toBeInTheDocument();
  expect(screen.getByText('Confirm your password')).toBeInTheDocument();
  expect(register).not.toHaveBeenCalled();
});

test.each([
  ['short1', 'Must be at least 8 characters'],
  ['lettersonly', 'Must contain a letter and a number'],
  ['12345678', 'Must contain a letter and a number'],
])('rejects password %s', (password, message) => {
  renderPage();
  fill({ ...VALID, Password: password, 'Confirm password': password });
  submit();
  expect(screen.getByText(message)).toBeInTheDocument();
  expect(register).not.toHaveBeenCalled();
});

test('rejects an invalid email and mismatched passwords', () => {
  renderPage();
  fill({ ...VALID, Email: 'nina@', 'Confirm password': 'Secret124' });
  submit();
  expect(screen.getByText('Enter a valid email address')).toBeInTheDocument();
  expect(screen.getByText('Passwords do not match')).toBeInTheDocument();
});

test('registers, disables submit while saving, then goes to the dashboard with a viewer message', async () => {
  let finish;
  register.mockReturnValue(new Promise((resolve) => { finish = resolve; }));
  renderPage();
  fill(VALID);
  submit();
  expect(screen.getByRole('button', { name: /creating account/i })).toBeDisabled();
  expect(register).toHaveBeenCalledWith({ full_name: 'Nina New', email: 'nina@acme.com', password: 'Secret123' });
  finish({ role: 'viewer' });
  expect(await screen.findByText('dashboard page')).toBeInTheDocument();
  expect(mockNotify).toHaveBeenCalledWith(expect.stringMatching(/Viewer.*Admin can upgrade/), 'info');
});

test('a taken email (409) is shown on the email field', async () => {
  register.mockRejectedValue(new ApiError(409, 'EMAIL_TAKEN', 'An account with this email already exists',
    { email: 'An account with this email already exists' }));
  renderPage();
  fill(VALID);
  submit();
  expect(await screen.findAllByText('An account with this email already exists')).toHaveLength(2);
  expect(screen.getByRole('button', { name: /create account/i })).toBeEnabled();
});

test('links back to sign in', () => {
  renderPage();
  fireEvent.click(screen.getByRole('link', { name: 'Sign in' }));
  expect(screen.getByText('login page')).toBeInTheDocument();
});
