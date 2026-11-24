import { validateProject } from './validation';

const valid = {
  code: 'APOLLO', name: 'Apollo', start_date: '2026-01-01', end_date: '2026-06-30', budget_planned: '1000.50',
};

test('accepts a valid project', () => {
  expect(validateProject(valid)).toEqual({});
});

test('reports each invalid field', () => {
  const errors = validateProject({ code: 'a b', name: ' ', start_date: '2026-06-01', end_date: '2026-01-01', budget_planned: '-1' });
  expect(Object.keys(errors).sort()).toEqual(['budget_planned', 'code', 'end_date', 'name']);
});

test('rejects more than two decimals and missing dates', () => {
  const errors = validateProject({ ...valid, budget_planned: '1.234', start_date: '', end_date: '' });
  expect(errors.budget_planned).toMatch(/2 decimals/);
  expect(errors.start_date).toBeDefined();
  expect(errors.end_date).toBeDefined();
});

import { validateAllocation, validateDeliverable, validateExpense, validateMember } from './validation';

test('allocation: percent range and date order', () => {
  const ok = { team_member_id: 1, project_id: 2, allocation_percent: '50', start_date: '2026-01-01', end_date: '' };
  expect(validateAllocation(ok)).toEqual({});
  const bad = validateAllocation({ ...ok, allocation_percent: '150', end_date: '2025-01-01' });
  expect(Object.keys(bad).sort()).toEqual(['allocation_percent', 'end_date']);
  expect(validateAllocation({ ...ok, allocation_percent: '2.5' }).allocation_percent).toBeDefined();
});

test('member, deliverable and expense rules', () => {
  expect(Object.keys(validateMember({ full_name: '', email: 'x' })).sort()).toEqual(['email', 'full_name']);
  expect(Object.keys(validateDeliverable({ project_id: '', name: '', due_date: '' })).sort())
    .toEqual(['due_date', 'name', 'project_id']);
  expect(validateExpense({ category: 'labor', description: 'x', amount: '0', incurred_on: '2026-01-01' }).amount).toBeDefined();
  expect(validateExpense({ category: 'labor', description: 'x', amount: '10.5', incurred_on: '2026-01-01' })).toEqual({});
});

import { isEmail, passwordProblem, validateRegistration } from './validation';

test('registration rules match the server', () => {
  expect(isEmail(' a@b.co ')).toBe(true);
  expect(isEmail('a@b')).toBe(false);
  expect(passwordProblem('Secret123')).toBeNull();
  expect(passwordProblem('x'.repeat(73) + '1')).toMatch(/72/);
  expect(validateRegistration({ full_name: 'A', email: 'a@b.co', password: 'Secret123', confirm_password: 'Secret123' }))
    .toEqual({});
});
