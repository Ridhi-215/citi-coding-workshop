import { Box, Button, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <Box sx={{ textAlign: 'center', py: 8, px: 2 }}>
      <Typography variant="h3" fontWeight={700} color="text.secondary">404</Typography>
      <Typography variant="h6" sx={{ mt: 1 }}>Page not found</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        The page you are looking for does not exist or was moved.
      </Typography>
      <Button variant="contained" component={RouterLink} to="/dashboard">Go to the dashboard</Button>
    </Box>
  );
}
