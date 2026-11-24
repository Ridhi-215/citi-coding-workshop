import { useState } from 'react';
import {
  Alert, Box, Button, Card, CardContent, Chip, FormControlLabel, IconButton, InputAdornment, LinearProgress,
  MenuItem, Paper, Stack, Switch, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow,
  TextField, Tooltip, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import AccountTreeIcon from '@mui/icons-material/AccountTreeOutlined';
import DeleteIcon from '@mui/icons-material/DeleteOutline';
import EditIcon from '@mui/icons-material/EditOutlined';
import SearchIcon from '@mui/icons-material/Search';
import { useSearchParams } from 'react-router-dom';
import { useMediaQuery } from 'react-responsive';
import ConfirmDialog from '../components/ConfirmDialog';
import DependencyDialog from '../components/DependencyDialog';
import FormDialog from '../components/FormDialog';
import { useAuth } from '../context/AuthContext';
import { useNotify } from '../context/NotifyContext';
import { useDebounced, useOptions, useQuery } from '../hooks';
import {
  createDeliverable, deleteDeliverable, DELIVERABLE_STATUSES, listDeliverables, updateDeliverable,
} from '../services/deliverablesService';
import { loadMemberOptions, loadProjectOptions } from '../services/options';
import { DELIVERABLE_STATUS_COLORS, formatDate, labelize } from '../utils/format';
import { validateDeliverable } from '../utils/validation';

const STATUS_OPTIONS = DELIVERABLE_STATUSES.map((s) => ({ value: s, label: labelize(s) }));

function Actions({ d, can, onDeps, onEdit, onDelete }) {
  return (
    <Stack direction="row" spacing={0.5} justifyContent="flex-end">
      <Tooltip title="Dependencies">
        <IconButton size="small" aria-label={`Dependencies of ${d.name}`} onClick={() => onDeps(d)}><AccountTreeIcon /></IconButton>
      </Tooltip>
      {can('UPDATE') && (
        <Tooltip title="Edit"><IconButton size="small" aria-label={`Edit ${d.name}`} onClick={() => onEdit(d)}><EditIcon /></IconButton></Tooltip>
      )}
      {can('DELETE') && (
        <Tooltip title="Delete"><IconButton size="small" aria-label={`Delete ${d.name}`} onClick={() => onDelete(d)}><DeleteIcon /></IconButton></Tooltip>
      )}
    </Stack>
  );
}

export default function DeliverablesPage() {
  const isMobile = useMediaQuery({ maxWidth: 899 });
  const { can } = useAuth();
  const notify = useNotify();
  const [params, setParams] = useSearchParams();
  const [search, setSearch] = useState('');
  const [projectId, setProjectId] = useState(params.get('project_id') || '');
  const [status, setStatus] = useState('');
  const [overdue, setOverdue] = useState(false);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [editing, setEditing] = useState(undefined);
  const [deleting, setDeleting] = useState(null);
  const projects = useOptions('projects', loadProjectOptions);
  const members = useOptions('members', loadMemberOptions);
  const debouncedSearch = useDebounced(search);
  const focus = Number(params.get('focus')) || null;

  const query = useQuery(listDeliverables, {
    search: debouncedSearch, project_id: projectId, status, overdue: overdue ? 'true' : '',
    limit: rowsPerPage, offset: page * rowsPerPage, sort: 'due_date',
  });
  const data = query.data || { items: [], total: 0 };
  const reset = (fn) => (e) => { fn(e.target.type === 'checkbox' ? e.target.checked : e.target.value); setPage(0); };

  const openDeps = (d) => setParams((p) => { p.set('focus', d.id); return p; });
  const closeDeps = () => setParams((p) => { p.delete('focus'); return p; });

  const fields = [
    { name: 'project_id', label: 'Project', type: 'select', options: projects, required: true, disabled: Boolean(editing) },
    { name: 'name', label: 'Name', required: true },
    { name: 'description', label: 'Description', type: 'multiline' },
    { name: 'owner_id', label: 'Owner', type: 'select', options: [{ value: '', label: 'Unassigned' }, ...members], width: 6 },
    { name: 'status', label: 'Status', type: 'select', options: STATUS_OPTIONS, width: 6 },
    { name: 'due_date', label: 'Due date', type: 'date', required: true, width: 6 },
  ];
  const initial = editing
    ? { project_id: editing.project_id, name: editing.name, description: editing.description || '',
      owner_id: editing.owner_id ?? '', status: editing.status, due_date: editing.due_date }
    : { project_id: projectId || '', name: '', description: '', owner_id: '', status: 'not_started', due_date: '' };

  const save = async (body) => {
    if (editing) await updateDeliverable(editing.id, body);
    else await createDeliverable(body);
    notify(`Deliverable ${editing ? 'updated' : 'created'}`);
    setEditing(undefined);
    query.reload();
  };

  const remove = async () => {
    try {
      await deleteDeliverable(deleting.id);
      notify('Deliverable deleted');
      setDeleting(null);
      query.reload();
    } catch (err) {
      notify(err.message, 'error');
    }
  };

  const statusChip = (d) => (
    <Stack direction="row" gap={0.5} flexWrap="wrap">
      <Chip size="small" label={labelize(d.status)} color={DELIVERABLE_STATUS_COLORS[d.status]} />
      {d.is_overdue && <Chip size="small" variant="outlined" color="error" label="Overdue" />}
      {d.open_dependency_count > 0 && d.status !== 'completed' && (
        <Chip size="small" variant="outlined" label={`Waiting on ${d.open_dependency_count}`} />
      )}
    </Stack>
  );

  return (
    <Box>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }} gap={2} flexWrap="wrap">
        <Typography variant="h5" fontWeight={700}>Deliverables</Typography>
        {can('CREATE') && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing(null)}>New deliverable</Button>}
      </Stack>

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2 }} alignItems={{ md: 'center' }}>
        <TextField size="small" placeholder="Search name, project, owner" value={search} onChange={reset(setSearch)}
          sx={{ flex: 1, maxWidth: { md: 320 } }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> }, htmlInput: { 'aria-label': 'Search deliverables' } }} />
        <TextField select size="small" label="Project" value={projectId} onChange={reset(setProjectId)} sx={{ minWidth: 200 }}>
          <MenuItem value="">All projects</MenuItem>
          {projects.map((p) => <MenuItem key={p.value} value={String(p.value)}>{p.label}</MenuItem>)}
        </TextField>
        <TextField select size="small" label="Status" value={status} onChange={reset(setStatus)} sx={{ minWidth: 150 }}>
          <MenuItem value="">All statuses</MenuItem>
          {STATUS_OPTIONS.map((s) => <MenuItem key={s.value} value={s.value}>{s.label}</MenuItem>)}
        </TextField>
        <FormControlLabel control={<Switch checked={overdue} onChange={reset(setOverdue)} />} label="Overdue only" />
      </Stack>

      {query.error && <Alert severity="error" sx={{ mb: 2 }} action={<Button color="inherit" size="small" onClick={query.reload}>Retry</Button>}>{query.error.message}</Alert>}

      <Paper variant="outlined">
        {query.loading && <LinearProgress aria-label="Loading deliverables" />}
        {!query.loading && !query.error && data.items.length === 0 && (
          <Typography color="text.secondary" sx={{ p: 4, textAlign: 'center' }}>No deliverables match your filters.</Typography>
        )}
        {isMobile ? (
          <Stack spacing={1.5} sx={{ p: 1.5 }}>
            {data.items.map((d) => (
              <Card key={d.id} variant="outlined">
                <CardContent>
                  <Stack direction="row" justifyContent="space-between" gap={1}>
                    <Box sx={{ minWidth: 0 }}>
                      <Typography variant="overline" color="text.secondary">{d.project_code}</Typography>
                      <Typography fontWeight={600}>{d.name}</Typography>
                    </Box>
                    <Actions d={d} can={can} onDeps={openDeps} onEdit={setEditing} onDelete={setDeleting} />
                  </Stack>
                  <Box sx={{ my: 1 }}>{statusChip(d)}</Box>
                  <Typography variant="caption" color="text.secondary">Due {formatDate(d.due_date)} · {d.owner_name || 'Unassigned'}</Typography>
                </CardContent>
              </Card>
            ))}
          </Stack>
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Deliverable</TableCell><TableCell>Project</TableCell><TableCell>Owner</TableCell>
                  <TableCell>Status</TableCell><TableCell>Due</TableCell><TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.items.map((d) => (
                  <TableRow key={d.id} hover>
                    <TableCell><Typography variant="body2" fontWeight={600}>{d.name}</Typography></TableCell>
                    <TableCell>{d.project_code}</TableCell>
                    <TableCell>{d.owner_name || '—'}</TableCell>
                    <TableCell>{statusChip(d)}</TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDate(d.due_date)}</TableCell>
                    <TableCell align="right"><Actions d={d} can={can} onDeps={openDeps} onEdit={setEditing} onDelete={setDeleting} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
        <TablePagination component="div" count={data.total} page={page} rowsPerPage={rowsPerPage} rowsPerPageOptions={[10, 25, 50]}
          onPageChange={(_, p) => setPage(p)} onRowsPerPageChange={(e) => { setRowsPerPage(Number(e.target.value)); setPage(0); }} />
      </Paper>

      {editing !== undefined && (
        <FormDialog open title={editing ? `Edit ${editing.name}` : 'New deliverable'} fields={fields} initial={initial}
          validate={validateDeliverable} submitLabel={editing ? 'Save changes' : 'Create deliverable'}
          onClose={() => setEditing(undefined)} onSave={save} />
      )}
      {focus && <DependencyDialog deliverableId={focus} onClose={closeDeps} onChanged={query.reload} />}
      <ConfirmDialog open={Boolean(deleting)} title={`Delete ${deleting?.name}?`}
        message="Its dependency links are removed too. This cannot be undone."
        onConfirm={remove} onClose={() => setDeleting(null)} />
    </Box>
  );
}
