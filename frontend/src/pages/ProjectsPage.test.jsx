import { screen } from '@testing-library/react';
import ProjectsPage from './ProjectsPage';
import { useAuth } from '../context/AuthContext';
import { listProjects } from '../services/projectsService';
import { authFor, renderAt } from '../test/renderWithAuth';

jest.mock('../context/AuthContext', () => ({ useAuth: jest.fn() }));
jest.mock('../context/NotifyContext', () => ({ useNotify: () => jest.fn() }));
jest.mock('../services/projectsService', () => ({
  PROJECT_STATUSES: ['planned', 'active'],
  PROJECT_PRIORITIES: ['low', 'high'],
  listProjects: jest.fn(),
  createProject: jest.fn(),
  updateProject: jest.fn(),
  deleteProject: jest.fn(),
}));

const PROJECT = {
  id: 1, code: 'ORION', name: 'Orion Data Platform', status: 'active', priority: 'high', manager_name: 'Mark',
  start_date: '2026-08-01', end_date: '2026-11-19', budget_planned: 300000, budget_spent: 323000,
  budget_used_pct: 107.7, deliverables_total: 4, deliverables_completed: 2, at_risk: true,
  risk_reasons: ['over_budget'],
};

beforeEach(() => listProjects.mockResolvedValue({ items: [PROJECT], total: 1 }));

test.each([
  ['admin', true, true, true],
  ['manager', true, true, true],
  ['contributor', true, true, false],
  ['viewer', false, false, false],
])('%s sees create=%s edit=%s delete=%s', async (role, create, edit, del) => {
  useAuth.mockReturnValue(authFor(role));
  renderAt(<ProjectsPage />);
  expect(await screen.findByText('Orion Data Platform')).toBeInTheDocument();
  expect(Boolean(screen.queryByRole('button', { name: /new project/i }))).toBe(create);
  expect(Boolean(screen.queryByRole('button', { name: 'Edit ORION' }))).toBe(edit);
  expect(Boolean(screen.queryByRole('button', { name: 'Delete ORION' }))).toBe(del);
});

test('shows at-risk badge and budget overrun', async () => {
  useAuth.mockReturnValue(authFor('viewer'));
  renderAt(<ProjectsPage />);
  expect(await screen.findByText('At risk')).toBeInTheDocument();
  expect(screen.getByText(/107.7%/)).toBeInTheDocument();
});
