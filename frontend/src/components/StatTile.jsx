import { Box, Card, CardContent, Stack, Typography } from '@mui/material';
import { SOFT } from '../theme';

/** Headline number with a label, an optional icon and context line (a single value, no chart). */
export default function StatTile({ label, value, detail, tone, icon }) {
  const critical = tone === 'critical';
  const soft = critical ? SOFT.error : SOFT.primary;
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        {/* two-line label slot keeps the numbers of a row aligned when a label wraps */}
        <Typography variant="body2" color="text.secondary" fontWeight={600} sx={{ lineHeight: 1.3, minHeight: '2.6em' }}>
          {label}
        </Typography>
        <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1} sx={{ mt: 1 }}>
          <Typography variant="h4" component="p" sx={{ color: critical ? soft.fg : 'text.primary', lineHeight: 1.1 }}>
            {value}
          </Typography>
          {icon && (
            <Box aria-hidden="true" sx={{ display: 'grid', placeItems: 'center', width: 40, height: 40, borderRadius: 3, bgcolor: soft.bg, color: soft.fg, flexShrink: 0, '& svg': { fontSize: 22 } }}>
              {icon}
            </Box>
          )}
        </Stack>
        {detail && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.75 }}>{detail}</Typography>}
      </CardContent>
    </Card>
  );
}
