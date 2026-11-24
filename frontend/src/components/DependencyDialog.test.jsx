import { fireEvent, screen, waitFor } from '@testing-library/react';
import DependencyDialog from './DependencyDialog';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../services/apiClient';
import { addDependency, getChain, listDeliverables, removeDependency } from '../services/deliverablesService';
import { authFor, renderAt } from '../test/renderWithAuth';

jest.mock('../context/AuthContext', () => ({ useAuth: jest.fn() }));
const mockNotify = jest.fn();
jest.mock('../context/NotifyContext', () => ({ useNotify: () => mockNotify }));
jest.mock('../services/deliverablesService', () => ({
  getChain: jest.fn(), listDeliverables: jest.fn(), addDependency: jest.fn(), removeDependency: jest.fn(),
}));

const node = (id, name, depth, status = 'in_progress') => ({ id, name, depth, status, due_date: '2026-12-01', is_overdue: false });

beforeEach(() => {
  jest.clearAllMocks();
  getChain.mockResolvedValue({
    deliverable: { id: 3, name: 'UAT', project_id: 1, project_code: 'APOLLO', status: 'not_started', due_date: '2026-12-20' },
    upstream: [node(2, 'Billing module', 1), node(1, 'Architecture', 2, 'completed')],
    downstream: [node(4, 'Go-live', 1, 'not_started')],
    blocking: [node(2, 'Billing module', 1)],
  });
  listDeliverables.mockResolvedValue({ items: [node(1, 'Architecture'), node(2, 'Billing module'), node(3, 'UAT'), node(5, 'Docs')] });
});

test('shows both directions of the chain and what blocks it', async () => {
  useAuth.mockReturnValue(authFor('viewer'));
  renderAt(<DependencyDialog deliverableId={3} onClose={jest.fn()} onChanged={jest.fn()} />);
  expect(await screen.findByText('Billing module')).toBeInTheDocument();
  expect(screen.getByText('↳ Architecture')).toBeInTheDocument();
  expect(screen.getByText('Go-live')).toBeInTheDocument();
  expect(screen.getByText(/Waiting on 1 unfinished prerequisite/)).toBeInTheDocument();
  expect(screen.queryByLabelText('Add prerequisite')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /Remove dependency/ })).not.toBeInTheDocument();
});

test('manager adds and removes prerequisites; a cycle error is shown on the field', async () => {
  useAuth.mockReturnValue(authFor('manager'));
  const onChanged = jest.fn();
  addDependency.mockRejectedValueOnce(new ApiError(400, 'VALIDATION_FAILED', 'invalid',
    { depends_on_id: 'would create a circular dependency' })).mockResolvedValueOnce({});
  removeDependency.mockResolvedValue(null);
  renderAt(<DependencyDialog deliverableId={3} onClose={jest.fn()} onChanged={onChanged} />);
  await screen.findByText('Billing module');

  fireEvent.mouseDown(screen.getByRole('combobox'));
  fireEvent.click(await screen.findByRole('option', { name: 'Docs' }));
  fireEvent.click(screen.getByRole('button', { name: 'Add' }));
  expect(await screen.findByText('would create a circular dependency')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Add' }));
  await waitFor(() => expect(addDependency).toHaveBeenLastCalledWith(3, 5));
  await waitFor(() => expect(onChanged).toHaveBeenCalled());

  fireEvent.click(screen.getByRole('button', { name: 'Remove dependency on Billing module' }));
  await waitFor(() => expect(removeDependency).toHaveBeenCalledWith(3, 2));
});
