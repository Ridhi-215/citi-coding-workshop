import { useState } from 'react';
import { Alert, Box, Button, CircularProgress, Link, Stack, TextField, Typography } from '@mui/material';
import { Link as RouterLink, Navigate, useNavigate } from 'react-router-dom';
import AuthCard from '../components/AuthCard';
import { useAuth } from '../context/AuthContext';
import { useNotify } from '../context/NotifyContext';
import { validateRegistration } from '../utils/validation';

const EMPTY = { full_name: '', email: '', password: '', confirm_password: '' };

export default function RegisterPage() {
  const { user, register } = useAuth();
  const notify = useNotify();
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (user && !submitting) return <Navigate to="/" replace />;

  const field = (name) => ({
    name,
    value: form[name],
    onChange: (e) => setForm((f) => ({ ...f, [name]: e.target.value })),
    error: Boolean(errors[name]),
    helperText: errors[name],
    fullWidth: true,
  });

  const submit = async (event) => {
    event.preventDefault();
    const fieldErrors = validateRegistration(form);
    setErrors(fieldErrors);
    setError('');
    if (Object.keys(fieldErrors).length) return;
    setSubmitting(true);
    try {
      await register({ full_name: form.full_name.trim(), email: form.email.trim(), password: form.password });
      notify('Welcome! You have Viewer (read-only) access. An Admin can upgrade your role.', 'info');
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setSubmitting(false);
      if (err.details) setErrors(err.details);
      setError(err.message);
    }
  };

  return (
    <AuthCard title="Create an account" subtitle="New accounts start with read-only (Viewer) access.">
      <Box component="form" noValidate onSubmit={submit}>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label="Full name" autoComplete="name" autoFocus {...field('full_name')} />
          <TextField label="Email" type="email" autoComplete="email" {...field('email')} />
          <TextField label="Password" type="password" autoComplete="new-password" {...field('password')}
            helperText={errors.password || 'At least 8 characters, with a letter and a number'} />
          <TextField label="Confirm password" type="password" autoComplete="new-password" {...field('confirm_password')} />
          <Button type="submit" variant="contained" size="large" disabled={submitting}
            startIcon={submitting ? <CircularProgress size={18} color="inherit" /> : null}>
            {submitting ? 'Creating account…' : 'Create account'}
          </Button>
        </Stack>
      </Box>
      <Typography variant="body2" sx={{ mt: 3, textAlign: 'center' }}>
        Already have an account? <Link component={RouterLink} to="/login">Sign in</Link>
      </Typography>
    </AuthCard>
  );
}
