import { Box, LinearProgress, Typography } from '@mui/material';

/** Today's total allocation; above 100% is shown in the error color and labelled. */
export default function AllocationBar({ pct }) {
  const over = pct > 100;
  return (
    <Box sx={{ minWidth: 120 }}>
      <LinearProgress variant="determinate" value={Math.min(pct, 100)} color={over ? 'error' : 'primary'}
        aria-label={`Allocated ${pct}%`} sx={{ height: 6, borderRadius: 3 }} />
      <Typography variant="caption" color={over ? 'error' : 'text.secondary'} fontWeight={over ? 700 : 400}>
        {pct}%{over ? ' · over-allocated' : ''}
      </Typography>
    </Box>
  );
}
