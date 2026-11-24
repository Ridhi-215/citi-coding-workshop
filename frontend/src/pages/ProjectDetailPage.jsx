import { useCallback, useState } from 'react';
import {
  Alert, Box, Breadcrumbs, Button, Chip, Grid, IconButton, LinearProgress, Link, Stack, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Tooltip, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/DeleteOutline';
import EditIcon from '@mui/icons-material/EditOutlined';
import { Link as RouterLink, useParams } from 'react-router-dom';
import AllocationsTable from '../components/AllocationsTable';
import BudgetBar from '../components/BudgetBar';
import ConfirmDialog from '../components/ConfirmDialog';
import FormDialog from '../components/FormDialog';
import Panel from '../components/Panel';
import { useAuth } from '../context/AuthContext';
import { useNotify } from '../context/NotifyContext';
import { useQuery } from '../hooks';
import { getProjectHealth } from '../services/dashboardService';
import { createExpense, deleteExpense, EXPENSE_CATEGORIES, listExpenses, updateExpense } from '../services/projectsService';
import {
  DELIVERABLE_STATUS_COLORS, formatDate, formatMoney, labelize, PRIORITY_COLORS, RISK_LABELS, STATUS_COLORS,
} from '../utils/format';
import { validateExpense } from '../utils/validation';

const EXPENSE_FIELDS = [
  { name: 'category', label: 'Category', type: 'select', required: true, width: 6,
    options: EXPENSE_CATEGORIES.map((c) => ({ value: c, label: labelize(c) })) },
  { name: 'amount', label: 'Amount (USD)', required: true, width: 6 },
  { name: 'description', label: 'Description', required: true },
  { name: 'incurred_on', label: 'Date', type: 'date', required: true, width: 6 },
];

export default function ProjectDetailPage() {
  const { id } = useParams();
  const { can } = useAuth();
  const notify = useNotify();
  const fetchHealth = useCallback(({ pid }) => getProjectHealth(pid), []);
  const fetchExpenses = useCallback(({ pid }) => listExpenses(pid, { limit: 200, sort: 'incurred_on', order: 'desc' }), []);
  const health = useQuery(fetchHealth, { pid: id });
  const expenses = useQuery(fetchExpenses, { pid: id });
  const [editing, setEditing] = useState(undefined);
  const [deleting, setDeleting] = useState(null);

  const refresh = () => {
    health.reload();
    expenses.reload();
  };

  const saveExpense = async (body) => {
    if (editing) await updateExpense(editing.id, body);
    else await createExpense(id, body);
    notify(`Expense ${editing ? 'updated' : 'added'}`);
    setEditing(undefined);
    refresh();
  };

  const removeExpense = async () => {
    try {
      await deleteExpense(deleting.id);
      notify('Expense deleted');
      setDeleting(null);
      refresh();
    } catch (err) {
      notify(err.message, 'error');
    }
  };

  if (health.error) return <Alert severity="error">{health.error.message}</Alert>;
  if (!health.data) return <LinearProgress aria-label="Loading project" />;
  const { project: p, deliverables, team, dependencies } = health.data;
  const names = Object.fromEntries(deliverables.map((d) => [d.id, d.name]));

  return (
    <Box>
      <Breadcrumbs sx={{ mb: 1 }}><Link component={RouterLink} to="/projects">Projects</Link><Typography>{p.code}</Typography></Breadcrumbs>
      {health.loading && <LinearProgress sx={{ mb: 1 }} />}
      <Typography variant="h5" fontWeight={700}>{p.name}</Typography>
      <Stack direction="row" gap={1} flexWrap="wrap" sx={{ my: 1 }}>
        <Chip size="small" label={labelize(p.status)} color={STATUS_COLORS[p.status]} />
        <Chip size="small" variant="outlined" label={`${labelize(p.priority)} priority`} color={PRIORITY_COLORS[p.priority]} />
        {p.risk_reasons.map((r) => <Chip key={r} size="small" color="error" variant="outlined" label={RISK_LABELS[r] || r} />)}
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {p.description} · {formatDate(p.start_date)} – {formatDate(p.end_date)} · Manager: {p.manager_name || '—'}
      </Typography>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Panel title="Budget" subtitle={`${formatMoney(p.budget_remaining)} remaining`}>
            <BudgetBar spent={p.budget_spent} planned={p.budget_planned} pct={p.budget_used_pct} />
          </Panel>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Panel title="Deliverables" subtitle={`${p.deliverables_completed} of ${p.deliverables_total} completed`}>
            <LinearProgress variant="determinate" value={Number(p.progress_pct)} color="success"
              aria-label={`Progress ${p.progress_pct}%`} sx={{ height: 6, borderRadius: 3 }} />
            <Typography variant="caption" color="text.secondary">
              {p.progress_pct}% · {p.deliverables_overdue} overdue · {p.deliverables_blocked} blocked
            </Typography>
          </Panel>
        </Grid>

        <Grid size={12}>
          <Panel title="Expenses" subtitle="Budget consumed = sum of expenses"
            action={can('CREATE') && <Button size="small" startIcon={<AddIcon />} onClick={() => setEditing(null)}>Add expense</Button>}>
            {expenses.error && <Alert severity="error">{expenses.error.message}</Alert>}
            {(expenses.data?.items || []).length === 0 ? (
              <Typography variant="body2" color="text.secondary">No expenses recorded.</Typography>
            ) : (
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow><TableCell>Date</TableCell><TableCell>Category</TableCell><TableCell>Description</TableCell>
                      <TableCell align="right">Amount</TableCell><TableCell align="right">Actions</TableCell></TableRow>
                  </TableHead>
                  <TableBody>
                    {expenses.data.items.map((e) => (
                      <TableRow key={e.id}>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDate(e.incurred_on)}</TableCell>
                        <TableCell>{labelize(e.category)}</TableCell>
                        <TableCell>{e.description}</TableCell>
                        <TableCell align="right">{formatMoney(e.amount)}</TableCell>
                        <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                          {can('UPDATE') && <Tooltip title="Edit"><IconButton size="small" aria-label={`Edit expense ${e.description}`} onClick={() => setEditing(e)}><EditIcon /></IconButton></Tooltip>}
                          {can('DELETE') && <Tooltip title="Delete"><IconButton size="small" aria-label={`Delete expense ${e.description}`} onClick={() => setDeleting(e)}><DeleteIcon /></IconButton></Tooltip>}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Panel>
        </Grid>

        <Grid size={{ xs: 12, lg: 7 }}>
          <Panel title="Deliverables & dependencies"
            action={<Button size="small" component={RouterLink} to={`/deliverables?project_id=${p.id}`}>Manage</Button>}>
            {deliverables.length === 0 ? <Typography variant="body2" color="text.secondary">No deliverables yet.</Typography> : (
              <TableContainer>
                <Table size="small">
                  <TableHead><TableRow><TableCell>Deliverable</TableCell><TableCell>Status</TableCell><TableCell>Due</TableCell><TableCell>Depends on</TableCell></TableRow></TableHead>
                  <TableBody>
                    {deliverables.map((d) => (
                      <TableRow key={d.id}>
                        <TableCell>
                          <Link component={RouterLink} to={`/deliverables?project_id=${p.id}&focus=${d.id}`}>{d.name}</Link>
                          <Typography variant="caption" color="text.secondary" display="block">{d.owner_name || 'Unassigned'}</Typography>
                        </TableCell>
                        <TableCell>
                          <Chip size="small" label={labelize(d.status)} color={DELIVERABLE_STATUS_COLORS[d.status]} />
                          {d.is_overdue && <Chip size="small" variant="outlined" color="error" label="Overdue" sx={{ ml: 0.5 }} />}
                        </TableCell>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDate(d.due_date)}</TableCell>
                        <TableCell>
                          {dependencies.filter((e) => e.target === d.id).map((e) => names[e.source]).join(', ') || '—'}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Panel>
        </Grid>

        <Grid size={{ xs: 12, lg: 5 }}>
          <Panel title="Team" subtitle="Current and upcoming allocations">
            <AllocationsTable rows={team} fixed={{ project_id: p.id }} onChanged={health.reload} />
          </Panel>
        </Grid>
      </Grid>

      {editing !== undefined && (
        <FormDialog open title={editing ? 'Edit expense' : 'Add expense'} fields={EXPENSE_FIELDS}
          initial={editing
            ? { category: editing.category, amount: String(editing.amount), description: editing.description, incurred_on: editing.incurred_on }
            : { category: 'labor', amount: '', description: '', incurred_on: new Date().toISOString().slice(0, 10) }}
          validate={validateExpense} onClose={() => setEditing(undefined)} onSave={saveExpense} />
      )}
      <ConfirmDialog open={Boolean(deleting)} title="Delete expense?" message={deleting ? `${deleting.description} (${formatMoney(deleting.amount)})` : ''}
        onConfirm={removeExpense} onClose={() => setDeleting(null)} />
    </Box>
  );
}
