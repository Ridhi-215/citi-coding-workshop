import { Box, Stack, Typography } from '@mui/material';
import InsightsIcon from '@mui/icons-material/InsightsOutlined';
import GroupsIcon from '@mui/icons-material/GroupsOutlined';
import AccountTreeIcon from '@mui/icons-material/AccountTreeOutlined';
import Logo from './Logo';

const HIGHLIGHTS = [
  { icon: <InsightsIcon />, text: 'See which projects are at risk, and why' },
  { icon: <GroupsIcon />, text: 'Spot over-allocated people before they burn out' },
  { icon: <AccountTreeIcon />, text: 'Follow dependency chains across deliverables' },
];

/** Split layout for the sign-in and register pages: brand panel + form (stacked on mobile). */
export default function AuthCard({ title, subtitle, children }) {
  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', gridTemplateColumns: { xs: '1fr', md: '5fr 6fr' }, gridTemplateRows: { xs: 'auto 1fr', md: '1fr' }, bgcolor: 'background.default' }}>
      <Box
        sx={{
          position: 'relative',
          overflow: 'hidden',
          color: '#ffffff',
          px: { xs: 3, sm: 5, md: 6 },
          py: { xs: 2.5, md: 6 },
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          gap: 4,
          background: 'linear-gradient(150deg, #0a4642 0%, #0f5f5a 55%, #2b7a6f 100%)',
        }}
      >
        <Box aria-hidden="true" sx={{ display: { xs: 'none', md: 'block' }, position: 'absolute', width: 520, height: 520, borderRadius: '50%', right: -220, top: -240, background: 'radial-gradient(circle, rgba(240,150,125,0.55) 0%, rgba(240,150,125,0.18) 45%, rgba(240,150,125,0) 70%)' }} />
        <Box aria-hidden="true" sx={{ display: { xs: 'none', md: 'block' }, position: 'absolute', width: 300, height: 300, borderRadius: '50%', left: -110, bottom: -130, border: '1px solid rgba(255,255,255,0.14)', boxShadow: '0 0 0 40px rgba(255,255,255,0.04)' }} />
        <Box sx={{ position: 'relative' }}><Logo inverse tagline /></Box>
        <Box sx={{ position: 'relative', display: { xs: 'none', md: 'block' } }}>
          <Typography variant="h4" component="p" sx={{ color: '#ffffff', mb: 1.5, maxWidth: 420 }}>
            Every project, person and budget in one calm view.
          </Typography>
          <Stack spacing={1.5} sx={{ mt: 3 }}>
            {HIGHLIGHTS.map((h) => (
              <Stack key={h.text} direction="row" spacing={1.5} alignItems="center">
                <Box sx={{ display: 'grid', placeItems: 'center', width: 36, height: 36, borderRadius: 2, bgcolor: 'rgba(255,255,255,0.12)', flexShrink: 0 }}>
                  {h.icon}
                </Box>
                <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.92)' }}>{h.text}</Typography>
              </Stack>
            ))}
          </Stack>
        </Box>
        <Typography variant="caption" sx={{ position: 'relative', display: { xs: 'none', md: 'block' }, color: 'rgba(255,255,255,0.75)' }}>
          ACME Inc. · Project Management &amp; Tracking
        </Typography>
      </Box>
      <Box sx={{ display: 'grid', justifyItems: 'center', alignItems: { xs: 'start', md: 'center' }, px: 2, py: { xs: 4, md: 6 } }}>
        <Box sx={{ width: '100%', maxWidth: 420 }}>
          <Typography variant="overline" color="primary">ACME Project Tracker</Typography>
          <Typography variant="h5" component="h1">{title}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, mb: 3 }}>{subtitle}</Typography>
          {children}
        </Box>
      </Box>
    </Box>
  );
}
