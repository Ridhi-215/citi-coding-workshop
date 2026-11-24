import { useCallback, useState } from 'react';
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, LinearProgress, List,
  ListItem, ListItemText, MenuItem, Stack, TextField, Tooltip, Typography,
} from '@mui/material';
import LinkOffIcon from '@mui/icons-material/LinkOff';
import { useMediaQuery } from 'react-responsive';
import { useAuth } from '../context/AuthContext';
import { useNotify } from '../context/NotifyContext';
import { useQuery } from '../hooks';
import { addDependency, getChain, listDeliverables, removeDependency } from '../services/deliverablesService';
import { DELIVERABLE_STATUS_COLORS, formatDate, labelize } from '../utils/format';

function ChainList({ title, items, empty, onRemove }) {
  return (
    <Box sx={{ mb: 2 }}>
      <Typography variant="subtitle2">{title}</Typography>
      {items.length === 0 ? (
        <Typography variant="body2" color="text.secondary">{empty}</Typography>
      ) : (
        <List dense disablePadding>
          {items.map((d) => (
            <ListItem
              key={d.id}
              disableGutters
              sx={{ pl: (d.depth - 1) * 2 }}
              secondaryAction={onRemove && d.depth === 1 ? (
                <Tooltip title="Remove dependency">
                  <IconButton edge="end" size="small" aria-label={`Remove dependency on ${d.name}`} onClick={() => onRemove(d)}>
                    <LinkOffIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              ) : null}
            >
              <ListItemText
                primary={<>{d.depth > 1 ? '↳ ' : ''}{d.name}</>}
                secondary={
                  <Stack direction="row" gap={1} alignItems="center" component="span">
                    <Chip size="small" component="span" label={labelize(d.status)} color={DELIVERABLE_STATUS_COLORS[d.status]} />
                    <span>due {formatDate(d.due_date)}{d.is_overdue ? ' · overdue' : ''}{d.depth > 1 ? ` · ${d.depth} steps away` : ''}</span>
                  </Stack>
                }
                secondaryTypographyProps={{ component: 'span' }}
              />
            </ListItem>
          ))}
        </List>
      )}
    </Box>
  );
}

/** Shows a deliverable's full dependency chain and lets editors add/remove direct prerequisites. */
export default function DependencyDialog({ deliverableId, onClose, onChanged }) {
  const fullScreen = useMediaQuery({ maxWidth: 599 });
  const { can } = useAuth();
  const notify = useNotify();
  const fetchChain = useCallback(({ id }) => getChain(id), []);
  const chain = useQuery(fetchChain, { id: deliverableId });
  const projectId = chain.data?.deliverable.project_id;
  const fetchCandidates = useCallback(
    ({ pid }) => (pid ? listDeliverables({ project_id: pid, limit: 200 }) : Promise.resolve({ items: [] })), [],
  );
  const candidates = useQuery(fetchCandidates, { pid: projectId });
  const [choice, setChoice] = useState('');
  const [error, setError] = useState('');

  const d = chain.data?.deliverable;
  const direct = new Set((chain.data?.upstream || []).filter((u) => u.depth === 1).map((u) => u.id));
  const options = (candidates.data?.items || []).filter((c) => c.id !== deliverableId && !direct.has(c.id));

  const add = async () => {
    setError('');
    try {
      await addDependency(deliverableId, choice);
      notify('Dependency added');
      setChoice('');
      chain.reload();
      onChanged();
    } catch (err) {
      setError(err.details?.depends_on_id || err.message);
    }
  };

  const remove = async (pre) => {
    try {
      await removeDependency(deliverableId, pre.id);
      notify('Dependency removed');
      chain.reload();
      onChanged();
    } catch (err) {
      notify(err.message, 'error');
    }
  };

  return (
    <Dialog open onClose={onClose} fullScreen={fullScreen} maxWidth="sm" fullWidth>
      <DialogTitle>Dependencies{d ? `: ${d.name}` : ''}</DialogTitle>
      <DialogContent dividers>
        {chain.loading && <LinearProgress aria-label="Loading dependencies" sx={{ mb: 2 }} />}
        {chain.error && <Alert severity="error">{chain.error.message}</Alert>}
        {chain.data && (
          <>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              {d.project_code} · {labelize(d.status)} · due {formatDate(d.due_date)}
            </Typography>
            {chain.data.blocking.length > 0 && d.status !== 'completed' && (
              <Alert severity="warning" sx={{ mb: 2 }}>
                Waiting on {chain.data.blocking.length} unfinished prerequisite{chain.data.blocking.length > 1 ? 's' : ''}.
              </Alert>
            )}
            <ChainList title="Depends on (upstream)" items={chain.data.upstream} empty="No prerequisites."
              onRemove={can('DELETE') ? remove : null} />
            <ChainList title="Needed by (downstream)" items={chain.data.downstream} empty="Nothing depends on this deliverable." />
            {can('UPDATE') && (
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ sm: 'flex-start' }}>
                <TextField select size="small" label="Add prerequisite" value={choice} onChange={(e) => setChoice(e.target.value)}
                  sx={{ flex: 1 }} error={Boolean(error)} helperText={error || 'Must finish before this deliverable'}>
                  {options.length === 0 && <MenuItem disabled value="">No other deliverables in this project</MenuItem>}
                  {options.map((o) => <MenuItem key={o.id} value={o.id}>{o.name}</MenuItem>)}
                </TextField>
                <Button variant="outlined" onClick={add} disabled={!choice}>Add</Button>
              </Stack>
            )}
          </>
        )}
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Close</Button></DialogActions>
    </Dialog>
  );
}
