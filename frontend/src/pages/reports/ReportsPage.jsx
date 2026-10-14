import { Link as RouterLink } from 'react-router';
import { Card, CardActionArea, CardContent, Grid, Skeleton, Typography } from '@mui/material';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import { useReportCatalogue } from '../../api/reports.js';

export default function ReportsPage() {
  const { data, isLoading, error } = useReportCatalogue();
  const groups = [];
  (data ?? []).forEach((r) => {
    const g = groups.find((x) => x.name === r.category);
    if (g) g.reports.push(r);
    else groups.push({ name: r.category, reports: [r] });
  });
  return (
    <>
      <PageHeader title="Reports" subtitle="Portfolio, procurement, contract and benefits reports, on screen or in Excel" />
      <ErrorAlert error={error} title="Could not load the reports" />
      {isLoading && <Skeleton variant="rounded" height={240} />}
      {groups.map((g) => (
        <section key={g.name}>
          <Typography variant="h6" sx={{ mt: 3, mb: 1.5 }}>
            {g.name}
          </Typography>
          <Grid container spacing={2}>
            {g.reports.map((r) => (
              <Grid key={r.id} size={{ xs: 12, sm: 6, lg: 4 }}>
                <Card sx={{ height: '100%' }}>
                  <CardActionArea component={RouterLink} to={`/reports/${r.id}`} sx={{ height: '100%', alignItems: 'flex-start' }}>
                    <CardContent>
                      <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 0.5 }}>
                        {r.title}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {r.description}
                      </Typography>
                    </CardContent>
                  </CardActionArea>
                </Card>
              </Grid>
            ))}
          </Grid>
        </section>
      ))}
    </>
  );
}
