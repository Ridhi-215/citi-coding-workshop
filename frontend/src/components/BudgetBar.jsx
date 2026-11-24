import { Box, LinearProgress, Typography } from '@mui/material';
import { formatMoney } from '../utils/format';

export default function BudgetBar({ spent, planned, pct }) {
  const value = Number(pct ?? 0);
  const color = value > 100 ? 'error' : value >= 90 ? 'warning' : 'primary';
  return (
    <Box sx={{ minWidth: 140 }}>
      <LinearProgress variant="determinate" value={Math.min(value, 100)} color={color}
        aria-label={`Budget used ${value}%`} sx={{ height: 6, borderRadius: 3 }} />
      <Typography variant="caption" color={value > 100 ? 'error' : 'text.secondary'}>
        {formatMoney(spent)} of {formatMoney(planned)} ({value}%)
      </Typography>
    </Box>
  );
}
