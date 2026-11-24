import { useCallback, useEffect, useState } from 'react';
import {
  Alert, Box, Button, Card, CardContent, Chip, FormControlLabel, IconButton, InputAdornment, LinearProgress,
  MenuItem, Paper, Stack, Switch, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination,
  TableRow, TextField, Tooltip, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/DeleteOutline';
import EditIcon from '@mui/icons-material/EditOutlined';
import SearchIcon from '@mui/icons-material/Search';
import WarningIcon from '@mui/icons-material/WarningAmberRounded';
import { useMediaQuery } from 'react-responsive';
import BudgetBar from '../components/BudgetBar';
import ConfirmDialog from '../components/ConfirmDialog';
import ProjectDialog from '../components/ProjectDialog';
import { useAuth } from '../context/AuthContext';
import { useNotify } from '../context/NotifyContext';
import { useDebounced } from '../hooks';
import {
  createProject, deleteProject, listProjects, PROJECT_STATUSES, updateProject,
} from '../services/projectsService';
import { formatDate, labelize, PRIORITY_COLORS, RISK_LABELS, STATUS_COLORS } from '../utils/format';

function RiskChip({ project }) {
  if (!project.at_risk) return null;
  return (
    <Tooltip title={project.risk_reasons.map((r) => RISK_LABELS[r] || r).join(', ')}>
      <Chip size="small" color="error" variant="outlined" icon={<WarningIcon />} label="At risk" />
    </Tooltip>
  );
}

function ProjectActions({ project, can, onEdit, onDelete }) {
  return (
    <Stack direction="row" spacing={0.5} justifyContent="flex-end">
      {can('UPDATE') && (
        <Tooltip title="Edit">
          <IconButton size="small" aria-label={`Edit ${project.code}`} onClick={() => onEdit(project)}><EditIcon /></IconButton>
        </Tooltip>
      )}
      {can('DELETE') && (
        <Tooltip title="Delete">
          <IconButton size="small" aria-label={`Delete ${project.code}`} onClick={() => onDelete(project)}><DeleteIcon /></IconButton>
        </Tooltip>
      )}
    </Stack>
  );
}

export default function ProjectsPage() {
  const isMobile = useMediaQuery({ maxWidth: 899 });
  const { can } = useAuth();
  const notify = useNotify();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [atRisk, setAtRisk] = useState(false);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [data, setData] = useState({ items: [], total: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(undefined); // undefined = closed, null = new, object = edit
  const [deleting, setDeleting] = useState(null);
  const debouncedSearch = useDebounced(search);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setData(await listProjects({
        search: debouncedSearch, status, at_risk: atRisk ? 'true' : '',
        limit: rowsPerPage, offset: page * rowsPerPage, sort: 'code',
      }));
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [debouncedSearch, status, atRisk, page, rowsPerPage]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, status, atRisk]);

  const save = async (body) => {
    if (editing) {
      await updateProject(editing.id, body);
      notify(`Project ${body.code} updated`);
    } else {
      await createProject(body);
      notify(`Project ${body.code} created`);
    }
    setEditing(undefined);
    load();
  };

  const remove = async () => {
    try {
      await deleteProject(deleting.id);
      notify(`Project ${deleting.code} deleted`);
      setDeleting(null);
      load();
    } catch (err) {
      notify(err.message, 'error');
    }
  };

  return (
    <Box>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }} gap={2} flexWrap="wrap">
        <Typography variant="h5" fontWeight={700}>Projects</Typography>
        {can('CREATE') && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing(null)}>New project</Button>
        )}
      </Stack>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }} alignItems={{ sm: 'center' }}>
        <TextField
          size="small" placeholder="Search code, name, manager" value={search} onChange={(e) => setSearch(e.target.value)}
          sx={{ flex: 1, maxWidth: { sm: 360 } }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> }, htmlInput: { 'aria-label': 'Search projects' } }}
        />
        <TextField select size="small" label="Status" value={status} onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 160 }}>
          <MenuItem value="">All statuses</MenuItem>
          {PROJECT_STATUSES.map((s) => <MenuItem key={s} value={s}>{labelize(s)}</MenuItem>)}
        </TextField>
        <FormControlLabel control={<Switch checked={atRisk} onChange={(e) => setAtRisk(e.target.checked)} />} label="At risk only" />
      </Stack>

      {error && <Alert severity="error" sx={{ mb: 2 }} action={<Button color="inherit" size="small" onClick={load}>Retry</Button>}>{error}</Alert>}

      <Paper variant="outlined">
        {loading && <LinearProgress aria-label="Loading projects" />}
        {!loading && !error && data.items.length === 0 && (
          <Typography color="text.secondary" sx={{ p: 4, textAlign: 'center' }}>No projects match your filters.</Typography>
        )}
        {isMobile ? (
          <Stack spacing={1.5} sx={{ p: 1.5 }}>
            {data.items.map((p) => (
              <Card key={p.id} variant="outlined">
                <CardContent>
                  <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={1}>
                    <Box sx={{ minWidth: 0 }}>
                      <Typography variant="overline" color="text.secondary">{p.code}</Typography>
                      <Typography fontWeight={600}>{p.name}</Typography>
                    </Box>
                    <ProjectActions project={p} can={can} onEdit={setEditing} onDelete={setDeleting} />
                  </Stack>
                  <Stack direction="row" spacing={1} sx={{ my: 1 }} flexWrap="wrap" useFlexGap>
                    <Chip size="small" label={labelize(p.status)} color={STATUS_COLORS[p.status]} />
                    <Chip size="small" variant="outlined" label={labelize(p.priority)} color={PRIORITY_COLORS[p.priority]} />
                    <RiskChip project={p} />
                  </Stack>
                  <BudgetBar spent={p.budget_spent} planned={p.budget_planned} pct={p.budget_used_pct} />
                  <Typography variant="caption" color="text.secondary" component="p">
                    {formatDate(p.start_date)} – {formatDate(p.end_date)} · {p.deliverables_completed}/{p.deliverables_total} deliverables
                  </Typography>
                </CardContent>
              </Card>
            ))}
          </Stack>
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Project</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Priority</TableCell>
                  <TableCell>Manager</TableCell>
                  <TableCell>Timeline</TableCell>
                  <TableCell>Budget used</TableCell>
                  <TableCell>Deliverables</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.items.map((p) => (
                  <TableRow key={p.id} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>{p.name}</Typography>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Typography variant="caption" color="text.secondary">{p.code}</Typography>
                        <RiskChip project={p} />
                      </Stack>
                    </TableCell>
                    <TableCell><Chip size="small" label={labelize(p.status)} color={STATUS_COLORS[p.status]} /></TableCell>
                    <TableCell><Chip size="small" variant="outlined" label={labelize(p.priority)} color={PRIORITY_COLORS[p.priority]} /></TableCell>
                    <TableCell>{p.manager_name || '—'}</TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDate(p.start_date)} – {formatDate(p.end_date)}</TableCell>
                    <TableCell><BudgetBar spent={p.budget_spent} planned={p.budget_planned} pct={p.budget_used_pct} /></TableCell>
                    <TableCell>{p.deliverables_completed}/{p.deliverables_total}</TableCell>
                    <TableCell align="right"><ProjectActions project={p} can={can} onEdit={setEditing} onDelete={setDeleting} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
        <TablePagination
          component="div" count={data.total} page={page} rowsPerPage={rowsPerPage}
          rowsPerPageOptions={[10, 25, 50]}
          onPageChange={(_, p) => setPage(p)}
          onRowsPerPageChange={(e) => { setRowsPerPage(Number(e.target.value)); setPage(0); }}
        />
      </Paper>

      {editing !== undefined && (
        <ProjectDialog open project={editing} onClose={() => setEditing(undefined)} onSave={save} />
      )}
      <ConfirmDialog
        open={Boolean(deleting)}
        title={`Delete ${deleting?.code}?`}
        message="This also deletes the project's expenses, deliverables and allocations. This cannot be undone."
        onConfirm={remove}
        onClose={() => setDeleting(null)}
      />
    </Box>
  );
}
