import { fireEvent, screen, waitFor } from '@testing-library/react';
import ProjectsPage from './ProjectsPage';
import { useAuth } from '../context/AuthContext';
import { createProject, deleteProject, listProjects, updateProject } from '../services/projectsService';
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

test('creates and edits a project through the dialog, showing server field errors', async () => {
  useAuth.mockReturnValue(authFor('manager'));
  createProject.mockRejectedValueOnce(Object.assign(new Error('One or more fields are invalid'), {
    code: 'VALIDATION_FAILED', details: { code: 'another project already uses this code' },
  })).mockResolvedValueOnce({});
  updateProject.mockResolvedValue({});
  deleteProject.mockResolvedValue(null);
  renderAt(<ProjectsPage />);
  await screen.findByText('Orion Data Platform');

  fireEvent.click(screen.getByRole('button', { name: /new project/i }));
  fireEvent.click(screen.getByRole('button', { name: 'Create project' }));
  expect(screen.getByText('Name is required')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText(/Code/), { target: { value: 'zeta' } });
  fireEvent.change(screen.getByLabelText(/^Name/), { target: { value: 'Zeta' } });
  fireEvent.change(screen.getByLabelText(/Start date/), { target: { value: '2026-01-01' } });
  fireEvent.change(screen.getByLabelText(/End date/), { target: { value: '2026-06-30' } });
  fireEvent.change(screen.getByLabelText(/Planned budget/), { target: { value: '1000' } });
  fireEvent.click(screen.getByRole('button', { name: 'Create project' }));
  expect(await screen.findByText('another project already uses this code')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Create project' }));
  await waitFor(() => expect(createProject).toHaveBeenLastCalledWith(expect.objectContaining({ code: 'ZETA', name: 'Zeta' })));

  fireEvent.click(await screen.findByRole('button', { name: 'Edit ORION' }));
  fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));
  await waitFor(() => expect(updateProject).toHaveBeenCalledWith(1, expect.objectContaining({ code: 'ORION' })));

  fireEvent.click(await screen.findByRole('button', { name: 'Delete ORION' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));
  await waitFor(() => expect(deleteProject).toHaveBeenCalledWith(1));
});
