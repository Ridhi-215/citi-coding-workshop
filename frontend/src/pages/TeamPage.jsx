import { useState } from 'react';
import {
  Alert, Box, Button, Card, CardContent, Chip, FormControlLabel, IconButton, InputAdornment, LinearProgress, Link,
  Paper, Stack, Switch, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField,
  Tooltip, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/DeleteOutline';
import EditIcon from '@mui/icons-material/EditOutlined';
import SearchIcon from '@mui/icons-material/Search';
import { Link as RouterLink } from 'react-router-dom';
import { useMediaQuery } from 'react-responsive';
import AllocationBar from '../components/AllocationBar';
import ConfirmDialog from '../components/ConfirmDialog';
import FormDialog from '../components/FormDialog';
import { useAuth } from '../context/AuthContext';
import { useNotify } from '../context/NotifyContext';
import { useDebounced, useQuery } from '../hooks';
import { createMember, deleteMember, EMPLOYMENT_TYPES, listMembers, updateMember } from '../services/resourcesService';
import { labelize } from '../utils/format';
import { validateMember } from '../utils/validation';

const MEMBER_FIELDS = [
  { name: 'full_name', label: 'Full name', required: true },
  { name: 'email', label: 'Email', type: 'email', required: true },
  { name: 'job_title', label: 'Job title', width: 6 },
  { name: 'department', label: 'Department', width: 6 },
  { name: 'location', label: 'Location', width: 6 },
  { name: 'employment_type', label: 'Employment', type: 'select', width: 6,
    options: EMPLOYMENT_TYPES.map((t) => ({ value: t, label: labelize(t) })) },
];

const memberForm = (m) => ({
  full_name: m?.full_name || '', email: m?.email || '', job_title: m?.job_title || '', department: m?.department || '',
  location: m?.location || '', employment_type: m?.employment_type || 'employee',
});

export default function TeamPage() {
  const isMobile = useMediaQuery({ maxWidth: 899 });
  const { can } = useAuth();
  const notify = useNotify();
  const [search, setSearch] = useState('');
  const [over, setOver] = useState(false);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);
  const [editing, setEditing] = useState(undefined);
  const [deleting, setDeleting] = useState(null);
  const debouncedSearch = useDebounced(search);
  const query = useQuery(listMembers, {
    search: debouncedSearch, over_allocated: over ? 'true' : '', limit: rowsPerPage, offset: page * rowsPerPage,
    sort: over ? 'current_allocation_pct' : 'full_name', order: over ? 'desc' : 'asc',
  });
  const data = query.data || { items: [], total: 0 };

  const save = async (body) => {
    if (editing) await updateMember(editing.id, body);
    else await createMember(body);
    notify(`Team member ${editing ? 'updated' : 'added'}`);
    setEditing(undefined);
    query.reload();
  };

  const remove = async () => {
    try {
      await deleteMember(deleting.id);
      notify(`${deleting.full_name} removed`);
      setDeleting(null);
      query.reload();
    } catch (err) {
      notify(err.message, 'error');
    }
  };

  const actions = (m) => (
    <Stack direction="row" spacing={0.5} justifyContent="flex-end">
      {can('UPDATE') && <Tooltip title="Edit"><IconButton size="small" aria-label={`Edit ${m.full_name}`} onClick={() => setEditing(m)}><EditIcon /></IconButton></Tooltip>}
      {can('DELETE') && <Tooltip title="Delete"><IconButton size="small" aria-label={`Delete ${m.full_name}`} onClick={() => setDeleting(m)}><DeleteIcon /></IconButton></Tooltip>}
    </Stack>
  );
  const name = (m) => <Link component={RouterLink} to={`/team/${m.id}`} fontWeight={600}>{m.full_name}</Link>;

  return (
    <Box>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }} gap={2} flexWrap="wrap">
        <Typography variant="h5" fontWeight={700}>Team &amp; allocation</Typography>
        {can('CREATE') && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing(null)}>Add member</Button>}
      </Stack>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }} alignItems={{ sm: 'center' }}>
        <TextField size="small" placeholder="Search name, title, department" value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0); }} sx={{ flex: 1, maxWidth: { sm: 360 } }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> }, htmlInput: { 'aria-label': 'Search team' } }} />
        <FormControlLabel control={<Switch checked={over} onChange={(e) => { setOver(e.target.checked); setPage(0); }} />} label="Over-allocated only (>100%)" />
      </Stack>

      {query.error && <Alert severity="error" sx={{ mb: 2 }} action={<Button color="inherit" size="small" onClick={query.reload}>Retry</Button>}>{query.error.message}</Alert>}

      <Paper variant="outlined">
        {query.loading && <LinearProgress aria-label="Loading team" />}
        {!query.loading && !query.error && data.items.length === 0 && (
          <Typography color="text.secondary" sx={{ p: 4, textAlign: 'center' }}>No team members match your filters.</Typography>
        )}
        {isMobile ? (
          <Stack spacing={1.5} sx={{ p: 1.5 }}>
            {data.items.map((m) => (
              <Card key={m.id} variant="outlined">
                <CardContent>
                  <Stack direction="row" justifyContent="space-between">
                    <Box>{name(m)}<Typography variant="body2" color="text.secondary">{m.job_title} · {m.department}</Typography></Box>
                    {actions(m)}
                  </Stack>
                  <Box sx={{ mt: 1 }}><AllocationBar pct={m.current_allocation_pct} /></Box>
                </CardContent>
              </Card>
            ))}
          </Stack>
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell><TableCell>Role</TableCell><TableCell>Department</TableCell>
                  <TableCell>Projects now</TableCell><TableCell>Allocation today</TableCell><TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.items.map((m) => (
                  <TableRow key={m.id} hover>
                    <TableCell>{name(m)}<Typography variant="caption" color="text.secondary" display="block">{m.email}</Typography></TableCell>
                    <TableCell>{m.job_title || '—'} {m.employment_type === 'contractor' && <Chip size="small" label="Contractor" variant="outlined" />}</TableCell>
                    <TableCell>{m.department || '—'}</TableCell>
                    <TableCell>{m.current_project_count}</TableCell>
                    <TableCell><AllocationBar pct={m.current_allocation_pct} /></TableCell>
                    <TableCell align="right">{actions(m)}</TableCell>
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
        <FormDialog open title={editing ? `Edit ${editing.full_name}` : 'Add team member'} fields={MEMBER_FIELDS}
          initial={memberForm(editing)} validate={validateMember} submitLabel={editing ? 'Save changes' : 'Add member'}
          onClose={() => setEditing(undefined)} onSave={save} />
      )}
      <ConfirmDialog open={Boolean(deleting)} title={`Remove ${deleting?.full_name}?`}
        message="Their allocations are deleted and deliverables they own become unassigned."
        onConfirm={remove} onClose={() => setDeleting(null)} />
    </Box>
  );
}
