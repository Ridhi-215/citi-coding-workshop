import { listProjects } from './projectsService';
import { listMembers } from './resourcesService';

export const loadProjectOptions = () => listProjects({ limit: 200, sort: 'code' })
  .then((p) => p.items.map((x) => ({ value: x.id, label: `${x.code} — ${x.name}` })));

export const loadMemberOptions = () => listMembers({ limit: 200, sort: 'full_name' })
  .then((p) => p.items.map((x) => ({ value: x.id, label: x.full_name })));
