import { useCallback } from 'react';
import { Alert, Box, Breadcrumbs, Button, Chip, Grid, LinearProgress, Link, Stack, Typography } from '@mui/material';
import { Link as RouterLink, useParams } from 'react-router-dom';
import AllocationBar from '../components/AllocationBar';
import AllocationsTable from '../components/AllocationsTable';
import Panel from '../components/Panel';
import NotFoundPage from './NotFoundPage';
import { useQuery } from '../hooks';
import { getMember } from '../services/resourcesService';
import { labelize } from '../utils/format';

export default function MemberPage() {
  const { id } = useParams();
  const fetcher = useCallback(({ memberId }) => getMember(memberId), []);
  const { data: m, error, loading, reload } = useQuery(fetcher, { memberId: id });

  if ([400, 404].includes(error?.status)) return <NotFoundPage />;
  if (error) {
    return <Alert severity="error" action={<Button color="inherit" size="small" onClick={reload}>Retry</Button>}>{error.message}</Alert>;
  }
  if (!m) return <LinearProgress aria-label="Loading team member" />;

  return (
    <Box>
      <Breadcrumbs sx={{ mb: 1 }}><Link component={RouterLink} to="/team">Team</Link><Typography>{m.full_name}</Typography></Breadcrumbs>
      {loading && <LinearProgress sx={{ mb: 1 }} />}
      <Stack direction="row" alignItems="center" gap={1} flexWrap="wrap" sx={{ mb: 2 }}>
        <Typography variant="h5" fontWeight={700}>{m.full_name}</Typography>
        {m.over_allocated && <Chip color="error" label={`Over-allocated: ${m.current_allocation_pct}%`} />}
      </Stack>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Panel title="Profile">
            <Typography variant="body2">{m.job_title || '—'} · {m.department || '—'}</Typography>
            <Typography variant="body2" color="text.secondary">{m.email}</Typography>
            <Typography variant="body2" color="text.secondary">{m.location || '—'} · {labelize(m.employment_type)}</Typography>
            <Box sx={{ mt: 2 }}>
              <Typography variant="subtitle2">Allocation today</Typography>
              <AllocationBar pct={m.current_allocation_pct} />
            </Box>
          </Panel>
        </Grid>
        <Grid size={{ xs: 12, md: 8 }}>
          <Panel title="Allocations" subtitle="Current allocations first">
            <AllocationsTable rows={m.allocations} fixed={{ team_member_id: m.id }} onChanged={reload} />
          </Panel>
        </Grid>
      </Grid>
    </Box>
  );
}
