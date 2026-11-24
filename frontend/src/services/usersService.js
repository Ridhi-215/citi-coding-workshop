import { api } from './apiClient';

const SERVICE = 'auth-service';

export const USER_ROLES = ['admin', 'manager', 'contributor', 'viewer'];

export const listUsers = (query) => api(SERVICE, '/users', { query });
export const updateUser = (id, body) => api(SERVICE, `/users/${id}`, { method: 'PUT', body });
export const deleteUser = (id) => api(SERVICE, `/users/${id}`, { method: 'DELETE' });
