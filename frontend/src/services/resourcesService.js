import { api } from './apiClient';

const SERVICE = 'resources-service';

export const EMPLOYMENT_TYPES = ['employee', 'contractor'];

export const listMembers = (query) => api(SERVICE, '/members', { query });
export const getMember = (id) => api(SERVICE, `/members/${id}`);
export const createMember = (body) => api(SERVICE, '/members', { method: 'POST', body });
export const updateMember = (id, body) => api(SERVICE, `/members/${id}`, { method: 'PUT', body });
export const deleteMember = (id) => api(SERVICE, `/members/${id}`, { method: 'DELETE' });

export const listAllocations = (query) => api(SERVICE, '/allocations', { query });
export const createAllocation = (body) => api(SERVICE, '/allocations', { method: 'POST', body });
export const updateAllocation = (id, body) => api(SERVICE, `/allocations/${id}`, { method: 'PUT', body });
export const deleteAllocation = (id) => api(SERVICE, `/allocations/${id}`, { method: 'DELETE' });
