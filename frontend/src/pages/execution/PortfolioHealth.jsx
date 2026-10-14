import { useNavigate } from 'react-router';
import { Card, CardContent, Typography } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import RagChip from './RagChip.jsx';
import { usePortfolioHealth } from '../../api/execution.js';
import { formatDate } from '../../domain/contracts.js';
import { pct } from '../../domain/planning.js';
import { formatMoney } from '../../utils/money.js';

const columns = [
  { field: 'code', headerName: 'Project', width: 130 },
  { field: 'name', headerName: 'Name', flex: 1, minWidth: 200 },
  { field: 'phase', headerName: 'Phase', width: 130, valueFormatter: (v) => (v ? v.charAt(0) + v.slice(1).toLowerCase() : '') },
  {
    field: 'lastOverall',
    headerName: 'Reported',
    width: 120,
    renderCell: ({ row }) => <RagChip rag={row.lastOverall} />,
  },
  {
    field: 'suggestedOverall',
    headerName: 'From figures',
    width: 120,
    renderCell: ({ row }) => <RagChip rag={row.suggestedOverall} />,
  },
  { field: 'earnedPct', headerName: 'Progress', width: 150, valueGetter: (v, row) => v, valueFormatter: (v, row) => (v == null ? '—' : `${pct(v)} of ${pct(row.plannedPct)}`) },
  { field: 'spi', headerName: 'SPI', width: 70 },
  { field: 'cpi', headerName: 'CPI', width: 70 },
  {
    field: 'slipDays',
    headerName: 'Finish',
    width: 170,
    valueFormatter: (v, row) =>
      row.forecastFinish ? `${formatDate(row.forecastFinish)}${v > 0 ? ` (+${v} d)` : ''}` : '—',
  },
  {
    field: 'committedMinor',
    headerName: 'Committed / budget',
    width: 190,
    valueFormatter: (v, row) =>
      `${formatMoney(v, row.currency, { compact: true })} / ${formatMoney(row.budgetMinor, row.currency, { compact: true })}`,
  },
  { field: 'highRisks', headerName: 'High risks', type: 'number', width: 100 },
  { field: 'openIssues', headerName: 'Open issues', type: 'number', width: 100 },
];

/** One row per project: reported and calculated status, progress, finish, money, risks and issues. */
export default function PortfolioHealth() {
  const navigate = useNavigate();
  const { data, isLoading } = usePortfolioHealth();
  return (
    <Card sx={{ mt: 3 }}>
      <CardContent>
        <Typography variant="h6">Portfolio health</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          &quot;Reported&quot; is the last submitted status report; &quot;From figures&quot; is worked out from the schedule,
          contracts and risks today.
        </Typography>
        <DataGrid
          rows={data ?? []}
          getRowId={(r) => r.projectId}
          columns={columns}
          loading={isLoading}
          autoHeight
          disableRowSelectionOnClick
          hideFooter={(data ?? []).length <= 25}
          onRowClick={({ id }) => navigate(`/projects/${id}?tab=schedule`)}
          localeText={{ noRowsLabel: 'No projects yet' }}
          sx={{ border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' } }}
        />
      </CardContent>
    </Card>
  );
}
