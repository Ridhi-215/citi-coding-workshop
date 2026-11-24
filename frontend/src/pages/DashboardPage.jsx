import {
  Alert, Box, Button, Chip, Grid, LinearProgress, Link, List, ListItem, ListItemText, Stack, Table, TableBody,
  TableCell, TableHead, TableRow, Typography,
} from '@mui/material';
import { BarChart } from '@mui/x-charts/BarChart';
import { Link as RouterLink } from 'react-router-dom';
import { useMediaQuery } from 'react-responsive';
import Panel from '../components/Panel';
import StatTile from '../components/StatTile';
import { useQuery } from '../hooks';
import { getSummary } from '../services/dashboardService';
import { CHART, DELIVERABLE_STATUS_CHART_COLORS } from '../utils/chartColors';
import { formatDate, formatMoney, labelize, RISK_LABELS } from '../utils/format';

const compactMoney = (v) => new Intl.NumberFormat('en-US', {
  style: 'currency', currency: 'USD', notation: 'compact', maximumFractionDigits: 1,
}).format(v);

function Empty({ children }) {
  return <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>{children}</Typography>;
}

export default function DashboardPage() {
  const isMobile = useMediaQuery({ maxWidth: 599 });
  const { data, loading, error, reload } = useQuery(getSummary, {});

  if (error) {
    return <Alert severity="error" action={<Button color="inherit" size="small" onClick={reload}>Retry</Button>}>{error.message}</Alert>;
  }
  if (!data) return <LinearProgress aria-label="Loading dashboard" />;

  const k = data.kpis;
  const budgetPct = Number(k.budget_planned) > 0 ? Math.round((Number(k.budget_spent) * 100) / Number(k.budget_planned)) : 0;
  const completionPct = k.deliverables_total ? Math.round((k.deliverables_completed * 100) / k.deliverables_total) : 0;
  const budgetRows = data.budget_by_project.map((p) => ({
    code: p.code, planned: Number(p.budget_planned), spent: Number(p.budget_spent),
  }));
  const allocationRows = data.allocation_by_member.filter((m) => m.current_allocation_pct > 0);

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="h5" fontWeight={700}>Portfolio dashboard</Typography>
        {loading && <Typography variant="caption" color="text.secondary">Refreshing…</Typography>}
      </Stack>

      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 6, md: 2 }}><StatTile label="Active projects" value={k.active_projects} /></Grid>
        <Grid size={{ xs: 6, md: 2 }}>
          <StatTile label="At risk" value={k.at_risk_projects} tone={k.at_risk_projects ? 'critical' : undefined} detail="active or on hold" />
        </Grid>
        <Grid size={{ xs: 6, md: 2 }}>
          <StatTile label="Budget used" value={`${budgetPct}%`} detail={`${compactMoney(k.budget_spent)} of ${compactMoney(k.budget_planned)}`} />
        </Grid>
        <Grid size={{ xs: 6, md: 2 }}>
          <StatTile label="Deliverables done" value={`${completionPct}%`} detail={`${k.deliverables_completed} of ${k.deliverables_total}`} />
        </Grid>
        <Grid size={{ xs: 6, md: 2 }}>
          <StatTile label="Overdue deliverables" value={k.deliverables_overdue} tone={k.deliverables_overdue ? 'critical' : undefined} />
        </Grid>
        <Grid size={{ xs: 6, md: 2 }}>
          <StatTile label="Over-allocated people" value={k.over_allocated_members} tone={k.over_allocated_members ? 'critical' : undefined} detail={`of ${k.team_size} team members`} />
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 7 }}>
          <Panel title="Budget: spent vs. planned" subtitle="Active and on-hold projects (spent = recorded expenses)">
            <BarChart
              height={Math.max(220, budgetRows.length * 44 + 60)}
              layout="horizontal"
              dataset={budgetRows}
              yAxis={[{ scaleType: 'band', dataKey: 'code', width: 70 }]}
              xAxis={[{ valueFormatter: (v) => compactMoney(v) }]}
              series={[
                { dataKey: 'planned', label: 'Planned', color: CHART.reference, valueFormatter: (v) => formatMoney(v) },
                { dataKey: 'spent', label: 'Spent', color: CHART.series, valueFormatter: (v) => formatMoney(v) },
              ]}
              borderRadius={4}
              grid={{ vertical: true }}
              margin={{ left: 0, right: 16 }}
            />
          </Panel>
        </Grid>

        <Grid size={{ xs: 12, lg: 5 }}>
          <Panel title="At-risk projects" subtitle="Active/on-hold projects with at least one risk signal">
            {data.at_risk_projects.length === 0 ? <Empty>No projects are at risk.</Empty> : (
              <List dense disablePadding>
                {data.at_risk_projects.map((p) => (
                  <ListItem key={p.id} disableGutters divider>
                    <ListItemText
                      primary={<Link component={RouterLink} to={`/projects/${p.id}`} fontWeight={600}>{p.code} · {p.name}</Link>}
                      secondary={
                        <Stack direction="row" gap={0.5} flexWrap="wrap" component="span" sx={{ mt: 0.5 }}>
                          {p.risk_reasons.map((r) => (
                            <Chip key={r} size="small" color="error" variant="outlined" label={RISK_LABELS[r] || r} component="span" />
                          ))}
                          <Typography variant="caption" component="span" sx={{ ml: 0.5 }}>
                            Budget {p.budget_used_pct}% · progress {p.progress_pct}%
                          </Typography>
                        </Stack>
                      }
                      secondaryTypographyProps={{ component: 'span' }}
                    />
                  </ListItem>
                ))}
              </List>
            )}
          </Panel>
        </Grid>

        <Grid size={{ xs: 12, md: 6, lg: 4 }}>
          <Panel title="Projects by status">
            <BarChart
              height={220}
              layout="horizontal"
              dataset={data.projects_by_status.map((s) => ({ status: labelize(s.status), count: s.count }))}
              yAxis={[{ scaleType: 'band', dataKey: 'status', width: 90 }]}
              xAxis={[{ tickMinStep: 1 }]}
              series={[{ dataKey: 'count', label: 'Projects', color: CHART.series }]}
              hideLegend
              borderRadius={4}
              margin={{ left: 0, right: 16 }}
            />
          </Panel>
        </Grid>

        <Grid size={{ xs: 12, md: 6, lg: 4 }}>
          <Panel title="Deliverables by status" subtitle={`${completionPct}% completed across live projects`}>
            <BarChart
              height={220}
              layout="horizontal"
              dataset={data.deliverables_by_status.map((s) => ({ status: labelize(s.status), key: s.status, count: s.count }))}
              yAxis={[{
                scaleType: 'band', dataKey: 'status', width: 90,
                colorMap: {
                  type: 'ordinal',
                  values: data.deliverables_by_status.map((s) => labelize(s.status)),
                  colors: data.deliverables_by_status.map((s) => DELIVERABLE_STATUS_CHART_COLORS[s.status] || CHART.neutral),
                },
              }]}
              xAxis={[{ tickMinStep: 1 }]}
              series={[{ dataKey: 'count', label: 'Deliverables' }]}
              hideLegend
              borderRadius={4}
              margin={{ left: 0, right: 16 }}
            />
          </Panel>
        </Grid>

        <Grid size={{ xs: 12, lg: 4 }}>
          <Panel title="Over-allocated people" subtitle="Total allocation today above 100%">
            {data.over_allocated_members.length === 0 ? <Empty>Nobody is over-allocated.</Empty> : (
              <List dense disablePadding>
                {data.over_allocated_members.map((m) => (
                  <ListItem key={m.id} disableGutters divider>
                    <ListItemText
                      primary={<><Link component={RouterLink} to={`/team/${m.id}`} fontWeight={600}>{m.full_name}</Link> · <Typography component="span" color="error.main" fontWeight={700}>{m.current_allocation_pct}%</Typography></>}
                      secondary={`${m.job_title || ''} — ${m.allocations}`}
                    />
                  </ListItem>
                ))}
              </List>
            )}
          </Panel>
        </Grid>

        <Grid size={{ xs: 12, lg: 6 }}>
          <Panel title="Resource allocation today" subtitle="Per person; the red bars are above 100%">
            <BarChart
              height={Math.max(240, allocationRows.length * 28 + 60)}
              layout="horizontal"
              dataset={allocationRows.map((m) => ({ name: m.full_name, pct: m.current_allocation_pct }))}
              yAxis={[{ scaleType: 'band', dataKey: 'name', width: isMobile ? 100 : 130 }]}
              xAxis={[{
                min: 0, max: Math.max(140, ...allocationRows.map((m) => m.current_allocation_pct)),
                valueFormatter: (v) => `${v}%`,
                colorMap: { type: 'piecewise', thresholds: [100.5], colors: [CHART.series, CHART.critical] },
              }]}
              series={[{ dataKey: 'pct', label: 'Allocation', valueFormatter: (v) => `${v}%` }]}
              hideLegend
              borderRadius={4}
              grid={{ vertical: true }}
              margin={{ left: 0, right: 16 }}
            />
          </Panel>
        </Grid>

        <Grid size={{ xs: 12, lg: 6 }}>
          <Panel title="Blocking work in dependency chains" subtitle="Blocked or overdue deliverables that others depend on">
            {data.blocking_deliverables.length === 0 ? <Empty>No dependency chain is blocked.</Empty> : (
              <Table size="small">
                <TableHead>
                  <TableRow><TableCell>Deliverable</TableCell><TableCell>Status</TableCell><TableCell align="right">Waiting on it</TableCell></TableRow>
                </TableHead>
                <TableBody>
                  {data.blocking_deliverables.map((d) => (
                    <TableRow key={d.id}>
                      <TableCell>
                        <Link component={RouterLink} to={`/deliverables?focus=${d.id}`}>{d.name}</Link>
                        <Typography variant="caption" color="text.secondary" display="block">{d.project_code} · due {formatDate(d.due_date)}</Typography>
                      </TableCell>
                      <TableCell><Chip size="small" color={d.status === 'blocked' ? 'error' : 'warning'} label={d.status === 'blocked' ? 'Blocked' : 'Overdue'} /></TableCell>
                      <TableCell align="right">{d.dependent_count}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
            <Typography variant="subtitle2" sx={{ mt: 2 }}>Overdue deliverables</Typography>
            {data.overdue_deliverables.length === 0 ? <Empty>Nothing is overdue.</Empty> : (
              <List dense disablePadding>
                {data.overdue_deliverables.map((d) => (
                  <ListItem key={d.id} disableGutters>
                    <ListItemText primary={`${d.project_code} · ${d.name}`} secondary={`${d.days_overdue} days overdue · ${d.owner_name || 'unassigned'}`} />
                  </ListItem>
                ))}
              </List>
            )}
          </Panel>
        </Grid>
      </Grid>
    </Box>
  );
}
