import { Box, Typography } from '@mui/material';

/** App mark (an "A" with an accent dot) and wordmark; `inverse` for dark backgrounds. */
export default function Logo({ inverse = false, tagline = false }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
      <Box component="svg" viewBox="0 0 32 32" aria-hidden="true" sx={{ width: 32, height: 32, flexShrink: 0 }}>
        <rect width="32" height="32" rx="8" fill={inverse ? '#ffffff' : '#0f5f5a'} />
        <path d="M9 23l7-14 7 14M12 18h8" fill="none" stroke={inverse ? '#0f5f5a' : '#ffffff'} strokeWidth="2.6"
          strokeLinecap="round" strokeLinejoin="round" />
        <circle cx="24.5" cy="8" r="3" fill="#e0795f" />
      </Box>
      <Box sx={{ lineHeight: 1.1 }}>
        <Typography component="span" variant="h6" sx={{ display: 'block', fontSize: '1.05rem', lineHeight: 1.2, color: inverse ? '#ffffff' : 'text.primary' }}>
          ACME Tracker
        </Typography>
        {tagline && (
        <Typography component="span" variant="caption" sx={{ display: 'block', color: inverse ? 'rgba(255,255,255,0.8)' : 'text.secondary' }}>
          Projects · People · Budgets
        </Typography>
        )}
      </Box>
    </Box>
  );
}
