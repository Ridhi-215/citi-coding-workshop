import { Box, Card, CardContent, Typography } from '@mui/material';

/** Centered card used by the sign-in and register pages. */
export default function AuthCard({ title, subtitle, children }) {
  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2, bgcolor: 'background.default' }}>
      <Card sx={{ width: '100%', maxWidth: 420 }}>
        <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
          <Typography variant="overline" color="primary" fontWeight={700}>ACME Project Tracker</Typography>
          <Typography variant="h5" fontWeight={700} component="h1">{title}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>{subtitle}</Typography>
          {children}
        </CardContent>
      </Card>
    </Box>
  );
}
