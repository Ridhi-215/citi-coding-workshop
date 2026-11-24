import { screen } from '@testing-library/react';
import DashboardPage from './DashboardPage';
import { getSummary } from '../services/dashboardService';
import { renderAt } from '../test/renderWithAuth';

jest.mock('../services/dashboardService', () => ({ getSummary: jest.fn() }));
// Charts need SVG layout that jsdom lacks; the data shown in them is covered by the backend ITs.
jest.mock('@mui/x-charts/BarChart', () => ({ BarChart: () => <div data-testid="chart" /> }));

const SUMMARY = {
  kpis: {
    active_projects: 4, at_risk_projects: 2, budget_planned: 1000, budget_spent: 500, deliverables_total: 10,
    deliverables_completed: 4, deliverables_overdue: 3, team_size: 12, over_allocated_members: 1,
  },
  projects_by_status: [{ status: 'active', count: 4 }],
  at_risk_projects: [{ id: 2, code: 'ORION', name: 'Orion', risk_reasons: ['over_budget', 'blocked_work'], budget_used_pct: 107.7, progress_pct: 50 }],
  budget_by_project: [{ id: 2, code: 'ORION', budget_planned: 300000, budget_spent: 323000 }],
  deliverables_by_status: [{ status: 'completed', count: 4 }],
  overdue_deliverables: [{ id: 9, name: 'ETL pipelines', project_code: 'ORION', days_overdue: 12, owner_name: 'Aisha' }],
  upcoming_deliverables: [],
  blocking_deliverables: [{ id: 9, name: 'ETL pipelines', project_code: 'ORION', status: 'blocked', due_date: '2026-11-12', dependent_count: 1 }],
  over_allocated_members: [{ id: 5, full_name: 'Sofia Martinez', job_title: 'Dev', current_allocation_pct: 130, allocations: 'APOLLO 80%, HERMES 50%' }],
  allocation_by_member: [{ id: 5, full_name: 'Sofia Martinez', current_allocation_pct: 130 }],
  allocation_by_project: [],
};

test('shows KPIs and answers the business questions', async () => {
  getSummary.mockResolvedValue(SUMMARY);
  renderAt(<DashboardPage />);
  expect(await screen.findByText('Portfolio dashboard')).toBeInTheDocument();
  expect(screen.getByText('50%')).toBeInTheDocument(); // budget used
  expect(screen.getByText('40%')).toBeInTheDocument(); // deliverables done
  expect(screen.getByRole('link', { name: /ORION · Orion/ })).toHaveAttribute('href', '/projects/2');
  expect(screen.getByText('Over budget')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Sofia Martinez' })).toHaveAttribute('href', '/team/5');
  expect(screen.getByText('130%')).toBeInTheDocument();
  expect(screen.getAllByText('ETL pipelines').length).toBeGreaterThan(0);
  expect(screen.getAllByTestId('chart').length).toBe(4);
});

test('shows an error with retry', async () => {
  getSummary.mockRejectedValueOnce(new Error('Server unavailable')).mockResolvedValue(SUMMARY);
  renderAt(<DashboardPage />);
  (await screen.findByRole('button', { name: 'Retry' })).click();
  expect(await screen.findByText('Portfolio dashboard')).toBeInTheDocument();
});
