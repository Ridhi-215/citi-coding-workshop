import { useState } from 'react';
import {
  Alert, Box, Button, Card, CardContent, Chip, IconButton, InputAdornment, LinearProgress, MenuItem, Paper, Stack,
  Switch, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField, Tooltip,
  Typography,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/DeleteOutline';
import SearchIcon from '@mui/icons-material/Search';
import { useMediaQuery } from 'react-responsive';
import ConfirmDialog from '../components/ConfirmDialog';
import { useAuth } from '../context/AuthContext';
import { useNotify } from '../context/NotifyContext';
import { useDebounced, useQuery } from '../hooks';
import { deleteUser, listUsers, updateUser, USER_ROLES } from '../services/usersService';
import { formatDate, labelize } from '../utils/format';

const SELF_HINT = 'You cannot change your own role, status or account';

function userControls({ user, isSelf, busy, onRole, onActive, onDelete }) {
  const disabled = isSelf || busy;
  return {
    role: (
      <TextField
        select size="small" value={user.role} disabled={disabled} onChange={(e) => onRole(user, e.target.value)}
        sx={{ minWidth: 140 }} slotProps={{ htmlInput: { 'aria-label': `Role of ${user.email}` } }}
      >
        {USER_ROLES.map((r) => <MenuItem key={r} value={r}>{labelize(r)}</MenuItem>)}
      </TextField>
    ),
    active: (
      <Tooltip title={isSelf ? SELF_HINT : user.is_active ? 'Deactivate' : 'Activate'}>
        <span>
          <Switch
            checked={user.is_active} disabled={disabled} onChange={(e) => onActive(user, e.target.checked)}
            slotProps={{ input: { 'aria-label': `Active ${user.email}` } }}
          />
        </span>
      </Tooltip>
    ),
    remove: (
      <Tooltip title={isSelf ? SELF_HINT : 'Delete'}>
        <span>
          <IconButton size="small" disabled={disabled} aria-label={`Delete ${user.email}`} onClick={() => onDelete(user)}>
            <DeleteIcon />
          </IconButton>
        </span>
      </Tooltip>
    ),
  };
}

export default function UsersPage() {
  const isMobile = useMediaQuery({ maxWidth: 899 });
  const { user: me } = useAuth();
  const notify = useNotify();
  const [search, setSearch] = useState('');
  const [role, setRole] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [busyId, setBusyId] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const debouncedSearch = useDebounced(search);
  const query = useQuery(listUsers, { search: debouncedSearch, role, limit: rowsPerPage, offset: page * rowsPerPage });
  const data = query.data || { items: [], total: 0 };
  const { loading, reload: load } = query;
  const error = query.error?.message;

  const change = async (user, body, message) => {
    setBusyId(user.id);
    try {
      const updated = await updateUser(user.id, body);
      query.mutate((d) => ({ ...d, items: d.items.map((u) => (u.id === updated.id ? updated : u)) }));
      notify(message);
    } catch (err) {
      notify(err.details ? Object.values(err.details).join('. ') : err.message, 'error');
    } finally {
      setBusyId(null);
    }
  };

  const onRole = (user, newRole) => change(user, { role: newRole }, `${user.full_name} is now ${labelize(newRole)}`);
  const onActive = (user, active) => change(user, { is_active: active },
    `${user.full_name} ${active ? 'activated' : 'deactivated'}`);

  const remove = async () => {
    try {
      await deleteUser(deleting.id);
      notify(`${deleting.full_name} deleted`);
      setDeleting(null);
      load();
    } catch (err) {
      notify(err.message, 'error');
    }
  };

  const controls = (u) => userControls({
    user: u, isSelf: u.id === me?.id, busy: busyId === u.id, onRole, onActive, onDelete: setDeleting,
  });

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} sx={{ mb: 2 }}>Users</Typography>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          size="small" placeholder="Search name or email" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          sx={{ flex: 1, maxWidth: { sm: 360 } }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> }, htmlInput: { 'aria-label': 'Search users' } }}
        />
        <TextField select size="small" label="Role" value={role} onChange={(e) => { setRole(e.target.value); setPage(0); }} sx={{ minWidth: 160 }}>
          <MenuItem value="">All roles</MenuItem>
          {USER_ROLES.map((r) => <MenuItem key={r} value={r}>{labelize(r)}</MenuItem>)}
        </TextField>
      </Stack>

      {error && <Alert severity="error" sx={{ mb: 2 }} action={<Button color="inherit" size="small" onClick={load}>Retry</Button>}>{error}</Alert>}

      <Paper variant="outlined">
        {loading && <LinearProgress aria-label="Loading users" />}
        {!loading && !error && data.items.length === 0 && (
          <Typography color="text.secondary" sx={{ p: 4, textAlign: 'center' }}>No users match your filters.</Typography>
        )}
        {isMobile ? (
          <Stack spacing={1.5} sx={{ p: 1.5 }}>
            {data.items.map((u) => {
              const c = controls(u);
              return (
                <Card key={u.id} variant="outlined">
                  <CardContent>
                    <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                      <Box sx={{ minWidth: 0 }}>
                        <Typography fontWeight={600}>
                          {u.full_name} {u.id === me?.id && <Chip size="small" label="You" sx={{ ml: 0.5 }} />}
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ wordBreak: 'break-all' }}>{u.email}</Typography>
                      </Box>
                      {c.remove}
                    </Stack>
                    <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mt: 1.5 }}>
                      {c.role}
                      <Stack direction="row" alignItems="center">{c.active}<Typography variant="body2">Active</Typography></Stack>
                    </Stack>
                  </CardContent>
                </Card>
              );
            })}
          </Stack>
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Email</TableCell>
                  <TableCell>Role</TableCell>
                  <TableCell>Active</TableCell>
                  <TableCell>Created</TableCell>
                  <TableCell align="right">Delete</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.items.map((u) => {
                  const c = controls(u);
                  return (
                    <TableRow key={u.id} hover>
                      <TableCell>
                        {u.full_name} {u.id === me?.id && <Chip size="small" label="You" sx={{ ml: 0.5 }} />}
                      </TableCell>
                      <TableCell>{u.email}</TableCell>
                      <TableCell>{c.role}</TableCell>
                      <TableCell>{c.active}</TableCell>
                      <TableCell>{formatDate(u.created_at)}</TableCell>
                      <TableCell align="right">{c.remove}</TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
        <TablePagination
          component="div" count={data.total} page={page} rowsPerPage={rowsPerPage} rowsPerPageOptions={[10, 25, 50]}
          onPageChange={(_, p) => setPage(p)}
          onRowsPerPageChange={(e) => { setRowsPerPage(Number(e.target.value)); setPage(0); }}
        />
      </Paper>

      <ConfirmDialog
        open={Boolean(deleting)}
        title={`Delete ${deleting?.full_name}?`}
        message={`${deleting?.email} will no longer be able to sign in. This cannot be undone.`}
        onConfirm={remove}
        onClose={() => setDeleting(null)}
      />
    </Box>
  );
}
