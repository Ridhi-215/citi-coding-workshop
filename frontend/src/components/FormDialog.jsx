import { useState } from 'react';
import {
  Alert, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Grid, MenuItem, TextField,
} from '@mui/material';
import { useMediaQuery } from 'react-responsive';

/**
 * Create/edit dialog driven by a field list:
 *   { name, label, type: 'text'|'email'|'date'|'number'|'select'|'multiline', options: [{value,label}],
 *     width: 12|6, required }
 * `validate(form)` returns field errors; server VALIDATION_FAILED details are mapped onto fields too.
 */
export default function FormDialog({ open, title, fields, initial, validate, submitLabel = 'Save', onClose, onSave }) {
  const fullScreen = useMediaQuery({ maxWidth: 599 });
  const [form, setForm] = useState(initial);
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    const fieldErrors = validate ? validate(form) : {};
    setErrors(fieldErrors);
    setError('');
    if (Object.keys(fieldErrors).length) return;
    setSaving(true);
    try {
      const body = {};
      fields.forEach((f) => {
        const v = form[f.name];
        body[f.name] = typeof v === 'string' ? (v.trim() === '' ? null : v.trim()) : v;
      });
      await onSave(body);
    } catch (err) {
      setSaving(false);
      if (err.code === 'VALIDATION_FAILED' && err.details) setErrors(err.details);
      setError(err.message);
    }
  };

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} fullScreen={fullScreen} maxWidth="sm" fullWidth>
      <form noValidate onSubmit={submit}>
        <DialogTitle>{title}</DialogTitle>
        <DialogContent dividers>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
          <Grid container spacing={2}>
            {fields.map((f, i) => (
              <Grid key={f.name} size={{ xs: 12, sm: f.width || 12 }}>
                <TextField
                  name={f.name}
                  label={f.label}
                  fullWidth
                  autoFocus={i === 0}
                  required={f.required}
                  disabled={f.disabled}
                  type={['date', 'number', 'email'].includes(f.type) ? f.type : 'text'}
                  select={f.type === 'select'}
                  multiline={f.type === 'multiline'}
                  minRows={f.type === 'multiline' ? 2 : undefined}
                  value={form[f.name] ?? ''}
                  onChange={(e) => setForm((s) => ({ ...s, [f.name]: e.target.value }))}
                  error={Boolean(errors[f.name])}
                  helperText={errors[f.name] || f.helperText}
                  slotProps={f.type === 'date' ? { inputLabel: { shrink: true } } : undefined}
                >
                  {f.type === 'select' && (f.options || []).map((o) => (
                    <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>
                  ))}
                </TextField>
              </Grid>
            ))}
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={saving}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={saving}
            startIcon={saving ? <CircularProgress size={18} color="inherit" /> : null}>
            {submitLabel}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
