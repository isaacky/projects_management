import { useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import { Link as RouterLink, useNavigate } from 'react-router';
import { Box, Card, CardContent, Grid, Link, MenuItem, Stack, TextField, Typography } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import { useBenefitsPortfolio } from '../../api/benefits.js';
import { CATEGORY, RATING, STATUS, formatPct, formatValue } from '../../domain/benefits.js';
import { formatDate } from '../../domain/contracts.js';
import { formatMoney } from '../../utils/money.js';
import { RatingChip } from './BenefitsTab.jsx';

export function BenefitStats({ summary: s }) {
  const items = [
    ['Benefits', s.benefits],
    ['On track or achieved', s.onTrack + s.achieved, 'success.main'],
    ['At risk', s.atRisk, s.atRisk ? 'warning.main' : undefined],
    ['Off track', s.offTrack, s.offTrack ? 'error.main' : undefined],
    ['Not measured yet', s.notMeasured],
    ['Measurements overdue', s.overdueMeasurements, s.overdueMeasurements ? 'error.main' : undefined],
    ['Realised a year', formatMoney(s.realisedMinor, 'KES', { compact: true })],
    ['At target a year', formatMoney(s.annualValueMinor, 'KES', { compact: true })],
  ];
  return (
    <Grid container spacing={2}>
      {items.map(([label, value, color]) => (
        <Grid key={label} size={{ xs: 6, sm: 3 }}>
          <Typography variant="overline" color="text.secondary">
            {label}
          </Typography>
          <Typography variant="h6" sx={{ color }}>
            {value}
          </Typography>
        </Grid>
      ))}
    </Grid>
  );
}

BenefitStats.propTypes = { summary: PropTypes.object.isRequired };

const columns = [
  { field: 'project', headerName: 'Project', width: 130, valueGetter: (v) => v?.code },
  {
    field: 'title',
    headerName: 'Benefit',
    flex: 1,
    minWidth: 220,
    valueGetter: (v, row) => `B${row.number} · ${row.title}`,
  },
  { field: 'category', headerName: 'Category', width: 120, valueGetter: (v) => CATEGORY[v] },
  {
    field: 'latestValue',
    headerName: 'Latest / target',
    width: 210,
    valueGetter: (v, row) => `${formatValue(v, row.unit)} / ${formatValue(row.targetValue, row.unit)}`,
  },
  {
    field: 'progressPct',
    headerName: 'Progress',
    width: 110,
    type: 'number',
    valueFormatter: (v, row) => (v === null ? '—' : `${formatPct(v)} of ${formatPct(row.expectedPct)}`),
  },
  {
    field: 'rating',
    headerName: 'Rating',
    width: 150,
    renderCell: ({ row }) =>
      row.status === 'ACTIVE' ? <RatingChip rating={row.rating} /> : <span>{STATUS[row.status].label}</span>,
  },
  {
    field: 'nextDue',
    headerName: 'Next reading',
    width: 130,
    renderCell: ({ row }) => (
      <Box component="span" sx={{ color: row.overdue ? 'error.main' : undefined }}>
        {row.nextDue ? formatDate(row.nextDue) : '—'}
      </Box>
    ),
  },
  { field: 'owner', headerName: 'Owner', width: 180, valueGetter: (v) => v?.name },
  {
    field: 'realisedMinor',
    headerName: 'Realised a year',
    width: 150,
    type: 'number',
    valueFormatter: (v, row) => (row.annualValueMinor === null ? '—' : formatMoney(v, 'KES', { compact: true })),
  },
];

export default function BenefitsPage() {
  const navigate = useNavigate();
  const { data, isLoading, error } = useBenefitsPortfolio();
  const [rating, setRating] = useState('');
  const [status, setStatus] = useState('ACTIVE');
  const rows = useMemo(
    () =>
      (data?.benefits ?? []).filter(
        (b) => (!status || b.status === status) && (!rating || (rating === 'NONE' ? !b.rating : b.rating === rating)),
      ),
    [data, rating, status],
  );
  return (
    <>
      <PageHeader title="Benefits" subtitle="What the portfolio's projects should deliver, measured against their targets" />
      <ErrorAlert error={error} title="Could not load benefits" />
      {data && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <BenefitStats summary={data.summary} />
          </CardContent>
        </Card>
      )}
      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <TextField select size="small" label="Status" value={status} onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 150 }}>
          <MenuItem value="">All</MenuItem>
          {Object.entries(STATUS).map(([k, v]) => (
            <MenuItem key={k} value={k}>
              {v.label}
            </MenuItem>
          ))}
        </TextField>
        <TextField select size="small" label="Rating" value={rating} onChange={(e) => setRating(e.target.value)} sx={{ minWidth: 170 }}>
          <MenuItem value="">All</MenuItem>
          {Object.entries(RATING).map(([k, v]) => (
            <MenuItem key={k} value={k}>
              {v.label}
            </MenuItem>
          ))}
          <MenuItem value="NONE">Not measured yet</MenuItem>
        </TextField>
      </Stack>
      <Card sx={{ mb: 3 }}>
        <DataGrid
          rows={rows}
          columns={columns}
          loading={isLoading}
          autoHeight
          density="compact"
          disableRowSelectionOnClick
          onRowClick={({ row }) => navigate(`/projects/${row.project.id}?tab=benefits`)}
          initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          pageSizeOptions={[25, 50, 100]}
          localeText={{ noRowsLabel: 'No benefits match' }}
          sx={{ border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' } }}
        />
      </Card>
      <Typography variant="h6" sx={{ mb: 1 }}>
        Reviews coming up
      </Typography>
      <Card>
        <CardContent>
          {(data?.upcomingReviews ?? []).length === 0 && (
            <Typography variant="body2" color="text.secondary">
              No reviews planned.
            </Typography>
          )}
          <Stack spacing={1}>
            {(data?.upcomingReviews ?? []).map((r) => (
              <Typography key={r.id} variant="body2" sx={{ color: r.overdue ? 'error.main' : undefined }}>
                {formatDate(r.dueDate)} ·{' '}
                <Link component={RouterLink} to={`/projects/${r.project.id}?tab=benefits`}>
                  {r.project.code}
                </Link>{' '}
                · {r.title} · led by {r.lead?.name}
                {r.overdue ? ' · overdue' : ''}
              </Typography>
            ))}
          </Stack>
        </CardContent>
      </Card>
    </>
  );
}
