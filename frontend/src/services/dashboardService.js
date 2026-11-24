import { api } from './apiClient';

export const getSummary = () => api('dashboard-service', '/summary');
export const getProjectHealth = (id) => api('dashboard-service', `/projects/${id}/health`);
