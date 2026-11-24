import { useState } from 'react';
import { Alert, Box, Button, CircularProgress, Link, Stack, TextField, Typography } from '@mui/material';
import { Link as RouterLink, Navigate, useLocation, useNavigate } from 'react-router-dom';
import AuthCard from '../components/AuthCard';
import { useAuth } from '../context/AuthContext';
import { isEmail } from '../utils/validation';

export default function LoginPage() {
  const { user, login, sessionExpired } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (user) return <Navigate to="/" replace />;

  const submit = async (event) => {
    event.preventDefault();
    const fieldErrors = {};
    if (!isEmail(email)) fieldErrors.email = 'Enter a valid email address';
    if (!password) fieldErrors.password = 'Enter your password';
    setErrors(fieldErrors);
    setError('');
    if (Object.keys(fieldErrors).length) return;
    setSubmitting(true);
    try {
      await login(email.trim(), password);
      navigate(location.state?.from || '/', { replace: true });
    } catch (err) {
      setError(err.message);
      setSubmitting(false);
    }
  };

  return (
    <AuthCard title="Sign in" subtitle="Track projects, deliverables, budgets and team allocation.">
      <Box component="form" noValidate onSubmit={submit}>
        <Stack spacing={2}>
          {sessionExpired && !error && <Alert severity="info">Your session has expired. Please sign in again.</Alert>}
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            label="Email" type="email" autoComplete="email" autoFocus fullWidth
            value={email} onChange={(e) => setEmail(e.target.value)}
            error={Boolean(errors.email)} helperText={errors.email}
          />
          <TextField
            label="Password" type="password" autoComplete="current-password" fullWidth
            value={password} onChange={(e) => setPassword(e.target.value)}
            error={Boolean(errors.password)} helperText={errors.password}
          />
          <Button type="submit" variant="contained" size="large" disabled={submitting}
            startIcon={submitting ? <CircularProgress size={18} color="inherit" /> : null}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </Button>
        </Stack>
      </Box>
      <Typography variant="body2" sx={{ mt: 3, textAlign: 'center' }}>
        Don&apos;t have an account? <Link component={RouterLink} to="/register">Register</Link>
      </Typography>
    </AuthCard>
  );
}
