import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import ProjectDetailPage from './ProjectDetailPage';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../services/apiClient';
import { getProjectHealth } from '../services/dashboardService';
import { createExpense, deleteExpense, listExpenses } from '../services/projectsService';
import { authFor, renderAt } from '../test/renderWithAuth';

jest.mock('../context/AuthContext', () => ({ useAuth: jest.fn() }));
const mockNotify = jest.fn();
jest.mock('../context/NotifyContext', () => ({ useNotify: () => mockNotify }));
jest.mock('../services/dashboardService', () => ({ getProjectHealth: jest.fn() }));
jest.mock('../services/options', () => ({
  loadProjectOptions: jest.fn().mockResolvedValue([]), loadMemberOptions: jest.fn().mockResolvedValue([]),
}));
jest.mock('../services/projectsService', () => ({
  EXPENSE_CATEGORIES: ['labor', 'software'],
  listExpenses: jest.fn(), createExpense: jest.fn(), updateExpense: jest.fn(), deleteExpense: jest.fn(),
}));

const HEALTH = {
  project: {
    id: 2, code: 'ORION', name: 'Orion Data Platform', description: 'Warehouse', status: 'active', priority: 'high',
    start_date: '2026-08-01', end_date: '2026-11-19', manager_name: 'Mark', budget_planned: 300000, budget_spent: 323000,
    budget_remaining: -23000, budget_used_pct: 107.7, progress_pct: 50, deliverables_total: 2, deliverables_completed: 1,
    deliverables_overdue: 1, deliverables_blocked: 1, risk_reasons: ['over_budget'],
  },
  deliverables: [
    { id: 10, name: 'Landing zone', status: 'completed', due_date: '2026-10-01', owner_name: 'Rahul' },
    { id: 11, name: 'ETL pipelines', status: 'blocked', due_date: '2026-11-12', is_overdue: true, owner_name: 'Aisha' },
  ],
  dependencies: [{ source: 10, target: 11 }],
  team: [{ allocation_id: 9, id: 7, full_name: 'Aisha Rahman', allocation_percent: 80, start_date: '2026-08-01',
    end_date: null, current_allocation_pct: 120, over_allocated: true }],
  expenses_by_category: [],
};

const renderPage = () => renderAt(<Routes><Route path="/projects/:id" element={<ProjectDetailPage />} /></Routes>, '/projects/2');

beforeEach(() => {
  jest.clearAllMocks();
  useAuth.mockReturnValue(authFor('manager'));
  getProjectHealth.mockResolvedValue(HEALTH);
  listExpenses.mockResolvedValue({ items: [{ id: 5, category: 'software', description: 'Licences', amount: 95000, incurred_on: '2026-09-01' }] });
});

test('shows budget, risk, deliverable dependencies and team', async () => {
  renderPage();
  expect(await screen.findByText('Orion Data Platform')).toBeInTheDocument();
  expect(screen.getByText('Over budget')).toBeInTheDocument();
  expect(screen.getByText(/107.7%/)).toBeInTheDocument();
  const etl = screen.getByRole('link', { name: 'ETL pipelines' }).closest('tr');
  expect(within(etl).getByText('Landing zone')).toBeInTheDocument();
  expect(screen.getByText('120% total')).toBeInTheDocument();
  expect(await screen.findByText('Licences')).toBeInTheDocument();
});

test('adds and deletes an expense', async () => {
  createExpense.mockResolvedValue({});
  deleteExpense.mockResolvedValue(null);
  renderPage();
  await screen.findByText('Licences');
  fireEvent.click(screen.getByRole('button', { name: 'Add expense' }));
  fireEvent.change(screen.getByLabelText(/Amount/), { target: { value: '1200.50' } });
  fireEvent.change(screen.getByLabelText(/Description/), { target: { value: 'Laptops' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save' }));
  await waitFor(() => expect(createExpense).toHaveBeenCalledWith('2', expect.objectContaining({ amount: '1200.50', description: 'Laptops' })));

  fireEvent.click(await screen.findByRole('button', { name: 'Delete expense Licences' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));
  await waitFor(() => expect(deleteExpense).toHaveBeenCalledWith(5));
});

test('viewer gets no edit controls', async () => {
  useAuth.mockReturnValue(authFor('viewer'));
  renderPage();
  await screen.findByText('Licences');
  expect(screen.queryByRole('button', { name: 'Add expense' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /Delete expense/ })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Add allocation' })).not.toBeInTheDocument();
});

test('unknown project shows the not-found page', async () => {
  getProjectHealth.mockRejectedValue(new ApiError(404, 'NOT_FOUND', 'Project 2 was not found'));
  renderPage();
  expect(await screen.findByText('Page not found')).toBeInTheDocument();
});
