import { screen } from '@testing-library/react';
import NotFoundPage from './NotFoundPage';
import { renderAt } from '../test/renderWithAuth';

test('offers a way back', () => {
  renderAt(<NotFoundPage />, '/nope');
  expect(screen.getByText('Page not found')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: /dashboard/i })).toHaveAttribute('href', '/dashboard');
});
