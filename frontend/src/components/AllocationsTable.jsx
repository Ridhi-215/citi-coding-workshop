import { useState } from 'react';
import {
  Button, Chip, IconButton, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tooltip, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/DeleteOutline';
import EditIcon from '@mui/icons-material/EditOutlined';
import ConfirmDialog from './ConfirmDialog';
import FormDialog from './FormDialog';
import { useAuth } from '../context/AuthContext';
import { useNotify } from '../context/NotifyContext';
import { useOptions } from '../hooks';
import { loadMemberOptions, loadProjectOptions } from '../services/options';
import { createAllocation, deleteAllocation, updateAllocation } from '../services/resourcesService';
import { formatDate } from '../utils/format';
import { validateAllocation } from '../utils/validation';

/**
 * Allocations of one member (fixed: { team_member_id }) or one project (fixed: { project_id }),
 * with add/edit/delete. Warns when a save leaves the member above 100%.
 */
export default function AllocationsTable({ rows: rawRows, fixed, onChanged }) {
  const { can } = useAuth();
  const notify = useNotify();
  const projects = useOptions('projects', loadProjectOptions);
  const members = useOptions('members', loadMemberOptions);
  const [editing, setEditing] = useState(undefined);
  const [deleting, setDeleting] = useState(null);
  const byMember = 'team_member_id' in fixed;
  // Member view rows are allocations (id = allocation); project view rows are people (id = member).
  const rows = rawRows.map((a) => (byMember
    ? { ...a, allocationId: a.id, memberId: fixed.team_member_id, projectId: a.project_id }
    : { ...a, allocationId: a.allocation_id, memberId: a.id, projectId: fixed.project_id }));

  const fields = [
    byMember
      ? { name: 'project_id', label: 'Project', type: 'select', options: projects, required: true }
      : { name: 'team_member_id', label: 'Team member', type: 'select', options: members, required: true },
    { name: 'role_on_project', label: 'Role on project', width: 6 },
    { name: 'allocation_percent', label: 'Allocation %', type: 'number', required: true, width: 6 },
    { name: 'start_date', label: 'Start date', type: 'date', required: true, width: 6 },
    { name: 'end_date', label: 'End date', type: 'date', width: 6, helperText: 'Leave empty if open-ended' },
  ];
  const initial = editing
    ? { team_member_id: editing.memberId, project_id: editing.projectId,
      role_on_project: editing.role_on_project || '', allocation_percent: String(editing.allocation_percent),
      start_date: editing.start_date, end_date: editing.end_date || '' }
    : { ...fixed, team_member_id: fixed.team_member_id ?? '', project_id: fixed.project_id ?? '', role_on_project: '',
      allocation_percent: '', start_date: new Date().toISOString().slice(0, 10), end_date: '' };

  const save = async (body) => {
    const payload = { ...fixed, ...body, allocation_percent: Number(body.allocation_percent) };
    const saved = editing ? await updateAllocation(editing.allocationId, payload) : await createAllocation(payload);
    if (saved.member_over_allocated) {
      notify(`Saved — ${saved.member_name} is now allocated ${saved.member_peak_allocation_pct}% (over 100%)`, 'warning');
    } else {
      notify('Allocation saved');
    }
    setEditing(undefined);
    onChanged();
  };

  const remove = async () => {
    try {
      await deleteAllocation(deleting.id);
      notify('Allocation removed');
      setDeleting(null);
      onChanged();
    } catch (err) {
      notify(err.message, 'error');
    }
  };

  return (
    <>
      {can('CREATE') && (
        <Stack direction="row" justifyContent="flex-end" sx={{ mb: 1 }}>
          <Button size="small" startIcon={<AddIcon />} onClick={() => setEditing(null)}>Add allocation</Button>
        </Stack>
      )}
      {rows.length === 0 ? (
        <Typography variant="body2" color="text.secondary">No allocations.</Typography>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{byMember ? 'Project' : 'Person'}</TableCell><TableCell>Role</TableCell>
                <TableCell align="right">%</TableCell><TableCell>Period</TableCell><TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((a) => (
                <TableRow key={a.allocationId}>
                  <TableCell>
                    {byMember ? `${a.project_code} · ${a.project_name}` : a.full_name}
                    {!byMember && a.over_allocated && <Chip size="small" color="error" variant="outlined" sx={{ ml: 1 }} label={`${a.current_allocation_pct}% total`} />}
                  </TableCell>
                  <TableCell>{a.role_on_project || '—'}</TableCell>
                  <TableCell align="right">{a.allocation_percent}%</TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    {formatDate(a.start_date)} – {a.end_date ? formatDate(a.end_date) : 'open'}
                    {a.is_current === false && <Chip size="small" sx={{ ml: 1 }} label="Not current" />}
                  </TableCell>
                  <TableCell align="right">
                    {can('UPDATE') && (
                      <Tooltip title="Edit"><IconButton size="small" aria-label="Edit allocation"
                        onClick={() => setEditing(a)}>
                        <EditIcon /></IconButton></Tooltip>
                    )}
                    {can('DELETE') && (
                      <Tooltip title="Delete"><IconButton size="small" aria-label="Delete allocation"
                        onClick={() => setDeleting({ id: a.allocationId })}><DeleteIcon /></IconButton></Tooltip>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {editing !== undefined && (
        <FormDialog open title={editing ? 'Edit allocation' : 'Add allocation'} fields={fields} initial={initial}
          validate={validateAllocation} onClose={() => setEditing(undefined)} onSave={save} />
      )}
      <ConfirmDialog open={Boolean(deleting)} title="Remove allocation?" message="This cannot be undone."
        onConfirm={remove} onClose={() => setDeleting(null)} />
    </>
  );
}
