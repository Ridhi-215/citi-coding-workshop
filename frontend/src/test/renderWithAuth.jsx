import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

export const PERMISSIONS = {
  admin: ['CREATE', 'DELETE', 'MANAGE_USERS', 'READ', 'UPDATE'],
  manager: ['CREATE', 'DELETE', 'READ', 'UPDATE'],
  contributor: ['CREATE', 'READ', 'UPDATE'],
  viewer: ['READ'],
};

export function authFor(role, overrides = {}) {
  const user = { id: 1, email: `${role}@acme.com`, full_name: `${role} user`, role, permissions: PERMISSIONS[role] };
  return {
    user, loading: false, login: jest.fn(), logout: jest.fn(),
    can: (p) => user.permissions.includes(p), ...overrides,
  };
}

export function renderAt(ui, path = '/') {
  return render(<MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter>);
}
