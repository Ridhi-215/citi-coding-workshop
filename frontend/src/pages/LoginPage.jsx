import { useState } from 'react';
import { Alert, Box, Button, Card, CardContent, CircularProgress, Stack, TextField, Typography } from '@mui/material';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const { user, login } = useAuth();
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
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) fieldErrors.email = 'Enter a valid email address';
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
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2, bgcolor: 'background.default' }}>
      <Card sx={{ width: '100%', maxWidth: 400 }}>
        <CardContent sx={{ p: 4 }}>
          <Typography variant="h5" fontWeight={700} gutterBottom>ACME Project Tracker</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>Sign in to continue</Typography>
          <Box component="form" noValidate onSubmit={submit}>
            <Stack spacing={2}>
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
          <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 3 }}>
            Demo accounts: admin@, manager@, contributor@, viewer@acme.com — password Demo@1234
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
}
