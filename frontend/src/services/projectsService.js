import { api } from './apiClient';

const SERVICE = 'projects-service';

export const PROJECT_STATUSES = ['planned', 'active', 'on_hold', 'completed', 'cancelled'];
export const PROJECT_PRIORITIES = ['low', 'medium', 'high', 'critical'];

export const listProjects = (query) => api(SERVICE, '/projects', { query });
export const getProject = (id) => api(SERVICE, `/projects/${id}`);
export const createProject = (body) => api(SERVICE, '/projects', { method: 'POST', body });
export const updateProject = (id, body) => api(SERVICE, `/projects/${id}`, { method: 'PUT', body });
export const deleteProject = (id) => api(SERVICE, `/projects/${id}`, { method: 'DELETE' });
