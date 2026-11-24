import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import AppLayout from './AppLayout';
import { useAuth } from '../context/AuthContext';
import { authFor, renderAt } from '../test/renderWithAuth';

jest.mock('../context/AuthContext', () => ({ useAuth: jest.fn() }));

const renderLayout = () => renderAt(
  <Routes><Route element={<AppLayout />}><Route path="*" element={<p>content</p>} /></Route></Routes>,
  '/projects',
);

describe('AppLayout sidebar', () => {
  test('shows Users only for admins', () => {
    useAuth.mockReturnValue(authFor('admin'));
    renderLayout();
    expect(screen.getByRole('link', { name: /users/i })).toHaveAttribute('href', '/users');
    expect(screen.getByRole('link', { name: /projects/i })).toBeInTheDocument();
  });

  test.each(['manager', 'contributor', 'viewer'])('hides Users for %s', (role) => {
    useAuth.mockReturnValue(authFor(role));
    renderLayout();
    expect(screen.queryByRole('link', { name: /users/i })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /projects/i })).toBeInTheDocument();
  });

  test('shows the role and signs out', () => {
    const auth = authFor('manager');
    useAuth.mockReturnValue(auth);
    renderLayout();
    expect(screen.getByText('Manager')).toBeInTheDocument();
    screen.getByRole('button', { name: /sign out/i }).click();
    expect(auth.logout).toHaveBeenCalled();
  });
});
