import * as dashboard from './dashboardService';
import * as deliverables from './deliverablesService';
import * as projects from './projectsService';
import * as resources from './resourcesService';
import * as users from './usersService';
import * as auth from './authService';
import { tokenStore } from './apiClient';

const ok = (body = {}) => Promise.resolve({ status: 200, ok: true, json: () => Promise.resolve(body) });

beforeEach(() => {
  localStorage.clear();
  global.fetch = jest.fn(() => ok({ id: 1 }));
});

const last = () => {
  const [url, init] = fetch.mock.calls[fetch.mock.calls.length - 1];
  return `${init.method} ${url}${init.body ? ` ${init.body}` : ''}`;
};

test.each([
  [() => dashboard.getSummary(), 'GET /api/dashboard-service/summary'],
  [() => dashboard.getProjectHealth(3), 'GET /api/dashboard-service/projects/3/health'],
  [() => projects.getProject(2), 'GET /api/projects-service/projects/2'],
  [() => projects.createProject({ a: 1 }), 'POST /api/projects-service/projects {"a":1}'],
  [() => projects.updateProject(2, { a: 1 }), 'PUT /api/projects-service/projects/2 {"a":1}'],
  [() => projects.deleteProject(2), 'DELETE /api/projects-service/projects/2'],
  [() => projects.listExpenses(2, { limit: 5 }), 'GET /api/projects-service/projects/2/expenses?limit=5'],
  [() => projects.createExpense(2, { a: 1 }), 'POST /api/projects-service/projects/2/expenses {"a":1}'],
  [() => projects.updateExpense(7, { a: 1 }), 'PUT /api/projects-service/expenses/7 {"a":1}'],
  [() => projects.deleteExpense(7), 'DELETE /api/projects-service/expenses/7'],
  [() => deliverables.getDeliverable(4), 'GET /api/deliverables-service/deliverables/4'],
  [() => deliverables.getChain(4), 'GET /api/deliverables-service/deliverables/4/chain'],
  [() => deliverables.createDeliverable({ a: 1 }), 'POST /api/deliverables-service/deliverables {"a":1}'],
  [() => deliverables.updateDeliverable(4, { a: 1 }), 'PUT /api/deliverables-service/deliverables/4 {"a":1}'],
  [() => deliverables.deleteDeliverable(4), 'DELETE /api/deliverables-service/deliverables/4'],
  [() => deliverables.addDependency(4, 2), 'POST /api/deliverables-service/deliverables/4/dependencies {"depends_on_id":2}'],
  [() => deliverables.removeDependency(4, 2), 'DELETE /api/deliverables-service/deliverables/4/dependencies/2'],
  [() => resources.getMember(5), 'GET /api/resources-service/members/5'],
  [() => resources.createMember({ a: 1 }), 'POST /api/resources-service/members {"a":1}'],
  [() => resources.updateMember(5, { a: 1 }), 'PUT /api/resources-service/members/5 {"a":1}'],
  [() => resources.deleteMember(5), 'DELETE /api/resources-service/members/5'],
  [() => resources.listAllocations({ member_id: 5 }), 'GET /api/resources-service/allocations?member_id=5'],
  [() => resources.createAllocation({ a: 1 }), 'POST /api/resources-service/allocations {"a":1}'],
  [() => resources.updateAllocation(8, { a: 1 }), 'PUT /api/resources-service/allocations/8 {"a":1}'],
  [() => resources.deleteAllocation(8), 'DELETE /api/resources-service/allocations/8'],
  [() => users.updateUser(3, { role: 'viewer' }), 'PUT /api/auth-service/users/3 {"role":"viewer"}'],
  [() => users.deleteUser(3), 'DELETE /api/auth-service/users/3'],
])('%#: calls %s', async (call, expected) => {
  await call();
  expect(last()).toBe(expected);
});

test('register and login store the session; logout clears it', async () => {
  fetch.mockReturnValue(ok({ access_token: 'A', refresh_token: 'R', user: { role: 'viewer' } }));
  await expect(auth.register({ full_name: 'N', email: 'n@acme.com', password: 'Secret123', role: 'admin' }))
    .resolves.toEqual({ role: 'viewer' });
  expect(last()).toBe('POST /api/auth-service/register {"full_name":"N","email":"n@acme.com","password":"Secret123"}');
  expect(fetch.mock.calls[0][1].headers.Authorization).toBeUndefined();
  expect(tokenStore.get().access_token).toBe('A');
  await auth.login('n@acme.com', 'Secret123');
  auth.logout();
  expect(tokenStore.get()).toBeNull();
});
