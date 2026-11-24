import { api } from './apiClient';

const SERVICE = 'projects-service';

export const PROJECT_STATUSES = ['planned', 'active', 'on_hold', 'completed', 'cancelled'];
export const PROJECT_PRIORITIES = ['low', 'medium', 'high', 'critical'];

export const listProjects = (query) => api(SERVICE, '/projects', { query });
export const getProject = (id) => api(SERVICE, `/projects/${id}`);
export const createProject = (body) => api(SERVICE, '/projects', { method: 'POST', body });
export const updateProject = (id, body) => api(SERVICE, `/projects/${id}`, { method: 'PUT', body });
export const deleteProject = (id) => api(SERVICE, `/projects/${id}`, { method: 'DELETE' });

export const EXPENSE_CATEGORIES = ['labor', 'software', 'hardware', 'travel', 'services', 'other'];

export const listExpenses = (projectId, query) => api(SERVICE, `/projects/${projectId}/expenses`, { query });
export const createExpense = (projectId, body) =>
  api(SERVICE, `/projects/${projectId}/expenses`, { method: 'POST', body });
export const updateExpense = (id, body) => api(SERVICE, `/expenses/${id}`, { method: 'PUT', body });
export const deleteExpense = (id) => api(SERVICE, `/expenses/${id}`, { method: 'DELETE' });
