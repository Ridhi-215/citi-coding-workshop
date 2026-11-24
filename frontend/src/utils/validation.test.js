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
