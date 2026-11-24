import { Card, CardContent, Typography } from '@mui/material';

/** Headline number with a label and an optional context line (no chart: a single value). */
export default function StatTile({ label, value, detail, tone }) {
  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="body2" color="text.secondary">{label}</Typography>
        <Typography variant="h4" fontWeight={700} color={tone === 'critical' ? 'error.main' : 'text.primary'} sx={{ my: 0.5 }}>
          {value}
        </Typography>
        {detail && <Typography variant="caption" color="text.secondary">{detail}</Typography>}
      </CardContent>
    </Card>
  );
}
