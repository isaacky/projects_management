import PropTypes from 'prop-types';
import { useQuery } from '@tanstack/react-query';
import {
  Box,
  Card,
  CardContent,
  Grid,
  LinearProgress,
  Skeleton,
  Stack,
  Typography,
} from '@mui/material';
import PageHeader from '../components/PageHeader.jsx';
import ErrorAlert from '../components/ErrorAlert.jsx';
import PhaseChip from '../components/PhaseChip.jsx';
import { apiFetch } from '../api/client.js';
import { PHASES } from '../domain/lifecycle.js';
import { formatMoney } from '../utils/money.js';
import PortfolioHealth from './execution/PortfolioHealth.jsx';
import BenefitsSummaryCard from './benefits/BenefitsSummaryCard.jsx';

function StatCard({ label, value, hint, loading }) {
  return (
    <Card>
      <CardContent>
        <Typography variant="body2" color="text.secondary">
          {label}
        </Typography>
        <Typography variant="h4" component="p" sx={{ mt: 1 }}>
          {loading ? <Skeleton width="60%" /> : value}
        </Typography>
        {hint && (
          <Typography variant="caption" color="text.secondary">
            {hint}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

StatCard.propTypes = {
  label: PropTypes.string.isRequired,
  value: PropTypes.node,
  hint: PropTypes.string,
  loading: PropTypes.bool,
};

export default function DashboardPage() {
  // Portfolio figures come from the Python analytics service.
  const { data, isLoading, error } = useQuery({
    queryKey: ['analytics', 'portfolio-summary'],
    queryFn: ({ signal }) => apiFetch('/analytics/portfolio/summary', { signal }),
  });

  const byPhase = new Map((data?.by_phase ?? []).map((row) => [row.phase, row]));
  const maxCount = Math.max(1, ...(data?.by_phase ?? []).map((r) => r.count));
  const inExecution = byPhase.get('EXECUTION')?.count ?? 0;

  return (
    <>
      <PageHeader title="Portfolio dashboard" subtitle="All active projects across the lifecycle" />
      <ErrorAlert error={error} title="Analytics unavailable" />

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, md: 4 }}>
          <StatCard label="Active projects" value={data?.active_count} loading={isLoading} />
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <StatCard
            label="Total approved budget"
            value={data && formatMoney(data.total_budget_minor, data.currency, { compact: true })}
            hint={data?.mixed_currency ? 'Some projects use other currencies' : undefined}
            loading={isLoading}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <StatCard label="In execution" value={inExecution} loading={isLoading} />
        </Grid>
      </Grid>

      <Card>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 2 }}>
            Projects by phase
          </Typography>
          <Stack spacing={2}>
            {PHASES.map((phase) => {
              const row = byPhase.get(phase.key);
              const count = row?.count ?? 0;
              return (
                <Box key={phase.key}>
                  <Stack direction="row" sx={{ justifyContent: 'space-between', mb: 0.5 }}>
                    <PhaseChip phase={phase.key} />
                    <Typography variant="body2" color="text.secondary">
                      {count} {count === 1 ? 'project' : 'projects'}
                      {row ? ` · ${formatMoney(row.budget_minor, data.currency, { compact: true })}` : ''}
                    </Typography>
                  </Stack>
                  <LinearProgress
                    variant={isLoading ? 'indeterminate' : 'determinate'}
                    value={(count / maxCount) * 100}
                    sx={{ height: 8, borderRadius: 4 }}
                  />
                </Box>
              );
            })}
          </Stack>
        </CardContent>
      </Card>
      <PortfolioHealth />
      <BenefitsSummaryCard />
    </>
  );
}
