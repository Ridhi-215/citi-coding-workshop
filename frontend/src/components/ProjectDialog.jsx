import { useState } from 'react';
import {
  Alert, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Grid, MenuItem, TextField,
} from '@mui/material';
import { useMediaQuery } from 'react-responsive';
import { PROJECT_PRIORITIES, PROJECT_STATUSES } from '../services/projectsService';
import { labelize } from '../utils/format';
import { validateProject } from '../utils/validation';

const EMPTY = {
  code: '', name: '', description: '', status: 'planned', priority: 'medium',
  start_date: '', end_date: '', budget_planned: '',
};

function toForm(project) {
  if (!project) return EMPTY;
  return {
    code: project.code, name: project.name, description: project.description || '', status: project.status,
    priority: project.priority, start_date: project.start_date, end_date: project.end_date,
    budget_planned: String(project.budget_planned ?? ''), manager_id: project.manager_id ?? null,
  };
}

/** Create/edit dialog. Server-side field errors (400 VALIDATION_FAILED) are shown on the fields. */
export default function ProjectDialog({ open, project, onClose, onSave }) {
  const fullScreen = useMediaQuery({ maxWidth: 599 });
  const [form, setForm] = useState(() => toForm(project));
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  const submit = async (event) => {
    event.preventDefault();
    const fieldErrors = validateProject(form);
    setErrors(fieldErrors);
    setError('');
    if (Object.keys(fieldErrors).length) return;
    setSaving(true);
    try {
      await onSave({
        ...form,
        code: form.code.trim().toUpperCase(),
        name: form.name.trim(),
        description: form.description.trim() || null,
        budget_planned: String(form.budget_planned).trim(),
      });
    } catch (err) {
      setSaving(false);
      if (err.code === 'VALIDATION_FAILED' && err.details) setErrors(err.details);
      setError(err.message);
    }
  };

  const field = (name, label, props = {}) => (
    <TextField
      name={name} label={label} fullWidth value={form[name]} onChange={set(name)}
      error={Boolean(errors[name])} helperText={errors[name]} {...props}
    />
  );

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} fullScreen={fullScreen} maxWidth="sm" fullWidth>
      <form noValidate onSubmit={submit}>
        <DialogTitle>{project ? `Edit ${project.code}` : 'New project'}</DialogTitle>
        <DialogContent dividers>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 4 }}>{field('code', 'Code', { required: true, autoFocus: !project })}</Grid>
            <Grid size={{ xs: 12, sm: 8 }}>{field('name', 'Name', { required: true })}</Grid>
            <Grid size={12}>{field('description', 'Description', { multiline: true, minRows: 2 })}</Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              {field('status', 'Status', {
                select: true,
                children: PROJECT_STATUSES.map((s) => <MenuItem key={s} value={s}>{labelize(s)}</MenuItem>),
              })}
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              {field('priority', 'Priority', {
                select: true,
                children: PROJECT_PRIORITIES.map((p) => <MenuItem key={p} value={p}>{labelize(p)}</MenuItem>),
              })}
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              {field('start_date', 'Start date', { type: 'date', required: true, slotProps: { inputLabel: { shrink: true } } })}
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              {field('end_date', 'End date', { type: 'date', required: true, slotProps: { inputLabel: { shrink: true } } })}
            </Grid>
            <Grid size={12}>
              {field('budget_planned', 'Planned budget (USD)', { required: true, inputMode: 'decimal' })}
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={saving}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={saving}
            startIcon={saving ? <CircularProgress size={18} color="inherit" /> : null}>
            {project ? 'Save changes' : 'Create project'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
