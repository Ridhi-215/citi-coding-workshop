import { fireEvent, screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import MemberPage from './MemberPage';
import TeamPage from './TeamPage';
import { useAuth } from '../context/AuthContext';
import { createAllocation, createMember, getMember, listMembers } from '../services/resourcesService';
import { authFor, renderAt } from '../test/renderWithAuth';

jest.mock('../context/AuthContext', () => ({ useAuth: jest.fn() }));
const mockNotify = jest.fn();
jest.mock('../context/NotifyContext', () => ({ useNotify: () => mockNotify }));
jest.mock('../services/options', () => ({
  loadProjectOptions: jest.fn().mockResolvedValue([{ value: 2, label: 'HERMES — Hermes' }]),
  loadMemberOptions: jest.fn().mockResolvedValue([]),
}));
jest.mock('../services/resourcesService', () => ({
  EMPLOYMENT_TYPES: ['employee', 'contractor'],
  listMembers: jest.fn(), getMember: jest.fn(), createMember: jest.fn(), updateMember: jest.fn(), deleteMember: jest.fn(),
  createAllocation: jest.fn(), updateAllocation: jest.fn(), deleteAllocation: jest.fn(),
}));

const SOFIA = {
  id: 5, full_name: 'Sofia Martinez', email: 'sofia@acme.com', job_title: 'Backend Developer', department: 'Engineering',
  location: 'Madrid', employment_type: 'employee', current_allocation_pct: 130, current_project_count: 2, over_allocated: true,
};

beforeEach(() => {
  jest.clearAllMocks();
  useAuth.mockReturnValue(authFor('manager'));
});

test('team list flags over-allocation and filters on it', async () => {
  listMembers.mockResolvedValue({ items: [SOFIA], total: 1 });
  renderAt(<TeamPage />);
  expect(await screen.findByText('130% · over-allocated')).toBeInTheDocument();
  fireEvent.click(screen.getByLabelText(/Over-allocated only/));
  await waitFor(() => expect(listMembers).toHaveBeenLastCalledWith(expect.objectContaining({
    over_allocated: 'true', sort: 'current_allocation_pct', order: 'desc',
  })));
});

test('adds a team member with validation', async () => {
  listMembers.mockResolvedValue({ items: [], total: 0 });
  createMember.mockResolvedValue({});
  renderAt(<TeamPage />);
  expect(await screen.findByText('No team members match your filters.')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Add member' }));
  fireEvent.click(screen.getByRole('button', { name: 'Add member' }));
  expect(screen.getByText('Name is required')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText(/Full name/), { target: { value: 'New Person' } });
  fireEvent.change(screen.getByLabelText(/Email/), { target: { value: 'new@acme.com' } });
  fireEvent.click(screen.getAllByRole('button', { name: 'Add member' }).at(-1));
  await waitFor(() => expect(createMember).toHaveBeenCalledWith(expect.objectContaining({ full_name: 'New Person' })));
});

test('member page adds an allocation and warns when it over-allocates', async () => {
  getMember.mockResolvedValue({ ...SOFIA, allocations: [
    { id: 3, project_id: 1, project_code: 'APOLLO', project_name: 'Apollo', allocation_percent: 80,
      start_date: '2026-08-01', end_date: null, is_current: true },
  ] });
  createAllocation.mockResolvedValue({ member_name: 'Sofia Martinez', member_over_allocated: true, member_peak_allocation_pct: 130 });
  renderAt(<Routes><Route path="/team/:id" element={<MemberPage />} /></Routes>, '/team/5');
  expect(await screen.findByText('Over-allocated: 130%')).toBeInTheDocument();
  expect(screen.getByText('APOLLO · Apollo')).toBeInTheDocument();

  fireEvent.click(screen.getByRole('button', { name: 'Add allocation' }));
  fireEvent.mouseDown(screen.getByRole('combobox', { name: /Project/ }));
  fireEvent.click(await screen.findByRole('option', { name: 'HERMES — Hermes' }));
  fireEvent.change(screen.getByLabelText(/Allocation %/), { target: { value: '50' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save' }));
  await waitFor(() => expect(createAllocation).toHaveBeenCalledWith(expect.objectContaining({
    team_member_id: 5, project_id: 2, allocation_percent: 50,
  })));
  expect(mockNotify).toHaveBeenCalledWith(expect.stringContaining('130% (over 100%)'), 'warning');
});
