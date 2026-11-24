import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import RequirePermission from './RequirePermission';
import { useAuth } from '../context/AuthContext';
import { authFor, renderAt } from '../test/renderWithAuth';

jest.mock('../context/AuthContext', () => ({ useAuth: jest.fn() }));

const app = (
  <Routes>
    <Route path="/" element={<p>home</p>} />
    <Route path="/users" element={<RequirePermission permission="MANAGE_USERS"><p>users page</p></RequirePermission>} />
  </Routes>
);

test('admin can open /users', () => {
  useAuth.mockReturnValue(authFor('admin'));
  renderAt(app, '/users');
  expect(screen.getByText('users page')).toBeInTheDocument();
});

test('manager typing /users is sent home', () => {
  useAuth.mockReturnValue(authFor('manager'));
  renderAt(app, '/users');
  expect(screen.queryByText('users page')).not.toBeInTheDocument();
  expect(screen.getByText('home')).toBeInTheDocument();
});
