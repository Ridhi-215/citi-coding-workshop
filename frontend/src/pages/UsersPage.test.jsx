import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import UsersPage from './UsersPage';
import { useAuth } from '../context/AuthContext';
import { deleteUser, listUsers, updateUser } from '../services/usersService';
import { authFor, renderAt } from '../test/renderWithAuth';
import { ApiError } from '../services/apiClient';

jest.mock('../context/AuthContext', () => ({ useAuth: jest.fn() }));
const mockNotify = jest.fn();
jest.mock('../context/NotifyContext', () => ({ useNotify: () => mockNotify }));
jest.mock('../services/usersService', () => ({
  USER_ROLES: ['admin', 'manager', 'contributor', 'viewer'],
  listUsers: jest.fn(),
  updateUser: jest.fn(),
  deleteUser: jest.fn(),
}));

const USERS = [
  { id: 1, email: 'admin@acme.com', full_name: 'Alice Admin', role: 'admin', is_active: true, created_at: '2026-11-01T10:00:00Z' },
  { id: 2, email: 'viewer@acme.com', full_name: 'Victor Viewer', role: 'viewer', is_active: true, created_at: '2026-11-01T10:00:00Z' },
];

beforeEach(() => {
  jest.clearAllMocks();
  useAuth.mockReturnValue(authFor('admin'));
  listUsers.mockResolvedValue({ items: USERS, total: 2, limit: 10, offset: 0 });
});

const row = (email) => screen.getByText(email).closest('tr');

test('lists users', async () => {
  renderAt(<UsersPage />);
  expect(await screen.findByText('Victor Viewer')).toBeInTheDocument();
  expect(listUsers).toHaveBeenCalledWith(expect.objectContaining({ limit: 10, offset: 0 }));
});

test('admin cannot change, deactivate or delete their own account', async () => {
  renderAt(<UsersPage />);
  await screen.findByText('admin@acme.com');
  const self = within(row('admin@acme.com'));
  expect(self.getByLabelText('Active admin@acme.com')).toBeDisabled();
  expect(self.getByRole('button', { name: 'Delete admin@acme.com' })).toBeDisabled();
  expect(self.getByRole('combobox')).toHaveAttribute('aria-disabled', 'true');
  expect(self.getByText('You')).toBeInTheDocument();
});

test('deactivating another user calls the API and shows success', async () => {
  updateUser.mockResolvedValue({ ...USERS[1], is_active: false });
  renderAt(<UsersPage />);
  await screen.findByText('viewer@acme.com');
  fireEvent.click(within(row('viewer@acme.com')).getByLabelText('Active viewer@acme.com'));
  await waitFor(() => expect(updateUser).toHaveBeenCalledWith(2, { is_active: false }));
  expect(mockNotify).toHaveBeenCalledWith('Victor Viewer deactivated');
});

test('server rejection is shown as an error', async () => {
  updateUser.mockRejectedValue(new ApiError(400, 'BAD_REQUEST', 'At least one active admin must remain'));
  renderAt(<UsersPage />);
  await screen.findByText('viewer@acme.com');
  fireEvent.click(within(row('viewer@acme.com')).getByLabelText('Active viewer@acme.com'));
  await waitFor(() => expect(mockNotify).toHaveBeenCalledWith('At least one active admin must remain', 'error'));
});

test('changing a role and deleting a user', async () => {
  updateUser.mockResolvedValue({ ...USERS[1], role: 'manager' });
  deleteUser.mockResolvedValue(null);
  renderAt(<UsersPage />);
  await screen.findByText('viewer@acme.com');
  fireEvent.mouseDown(within(row('viewer@acme.com')).getByRole('combobox'));
  fireEvent.click(await screen.findByRole('option', { name: 'Manager' }));
  await waitFor(() => expect(updateUser).toHaveBeenCalledWith(2, { role: 'manager' }));

  fireEvent.click(within(row('viewer@acme.com')).getByRole('button', { name: 'Delete viewer@acme.com' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));
  await waitFor(() => expect(deleteUser).toHaveBeenCalledWith(2));
});

test('shows a load error with retry', async () => {
  listUsers.mockRejectedValueOnce(new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server'));
  renderAt(<UsersPage />);
  expect(await screen.findByText('Cannot reach the server')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
  expect(await screen.findByText('Victor Viewer')).toBeInTheDocument();
});
