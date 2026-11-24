import { api } from './apiClient';

const SERVICE = 'deliverables-service';

export const DELIVERABLE_STATUSES = ['not_started', 'in_progress', 'blocked', 'completed'];

export const listDeliverables = (query) => api(SERVICE, '/deliverables', { query });
export const getDeliverable = (id) => api(SERVICE, `/deliverables/${id}`);
export const getChain = (id) => api(SERVICE, `/deliverables/${id}/chain`);
export const createDeliverable = (body) => api(SERVICE, '/deliverables', { method: 'POST', body });
export const updateDeliverable = (id, body) => api(SERVICE, `/deliverables/${id}`, { method: 'PUT', body });
export const deleteDeliverable = (id) => api(SERVICE, `/deliverables/${id}`, { method: 'DELETE' });
export const addDependency = (id, dependsOnId) =>
  api(SERVICE, `/deliverables/${id}/dependencies`, { method: 'POST', body: { depends_on_id: dependsOnId } });
export const removeDependency = (id, dependsOnId) =>
  api(SERVICE, `/deliverables/${id}/dependencies/${dependsOnId}`, { method: 'DELETE' });
