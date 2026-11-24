import { screen } from '@testing-library/react';
import DeliverablesPage from './DeliverablesPage';
import { useAuth } from '../context/AuthContext';
import { listDeliverables } from '../services/deliverablesService';
import { authFor, renderAt } from '../test/renderWithAuth';

jest.mock('../context/AuthContext', () => ({ useAuth: jest.fn() }));
jest.mock('../context/NotifyContext', () => ({ useNotify: () => jest.fn() }));
jest.mock('../services/options', () => ({
  loadProjectOptions: jest.fn().mockResolvedValue([]), loadMemberOptions: jest.fn().mockResolvedValue([]),
}));
jest.mock('../services/deliverablesService', () => ({
  DELIVERABLE_STATUSES: ['not_started', 'in_progress', 'blocked', 'completed'],
  listDeliverables: jest.fn(), createDeliverable: jest.fn(), updateDeliverable: jest.fn(), deleteDeliverable: jest.fn(),
}));

const ROW = {
  id: 9, name: 'ETL pipelines', project_id: 2, project_code: 'ORION', owner_name: 'Aisha', status: 'blocked',
  due_date: '2026-11-12', is_overdue: true, open_dependency_count: 1,
};

beforeEach(() => listDeliverables.mockResolvedValue({ items: [ROW], total: 1 }));

test.each([
  ['manager', true, true, true],
  ['contributor', true, true, false],
  ['viewer', false, false, false],
])('%s: create=%s edit=%s delete=%s; everyone can view dependencies', async (role, create, edit, del) => {
  useAuth.mockReturnValue(authFor(role));
  renderAt(<DeliverablesPage />);
  expect(await screen.findByText('ETL pipelines')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Dependencies of ETL pipelines' })).toBeInTheDocument();
  expect(Boolean(screen.queryByRole('button', { name: /new deliverable/i }))).toBe(create);
  expect(Boolean(screen.queryByRole('button', { name: 'Edit ETL pipelines' }))).toBe(edit);
  expect(Boolean(screen.queryByRole('button', { name: 'Delete ETL pipelines' }))).toBe(del);
});

test('shows overdue and waiting-on badges', async () => {
  useAuth.mockReturnValue(authFor('viewer'));
  renderAt(<DeliverablesPage />);
  expect(await screen.findByText('Overdue')).toBeInTheDocument();
  expect(screen.getByText('Waiting on 1')).toBeInTheDocument();
});
