import { useNavigate } from 'react-router';
import { Alert, Card } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import TenderStatusChip from './TenderStatusChip.jsx';
import { useTenders } from '../../api/tenders.js';
import { formatMoney } from '../../utils/money.js';
import { formatDateTime } from '../../domain/tenders.js';

const columns = [
  { field: 'number', headerName: 'Number', width: 140 },
  { field: 'title', headerName: 'Title', flex: 1, minWidth: 220 },
  { field: 'methodCode', headerName: 'Method', width: 160 },
  {
    field: 'status',
    headerName: 'Status',
    width: 170,
    renderCell: ({ row }) => <TenderStatusChip tender={row} />,
  },
  {
    field: 'closingAt',
    headerName: 'Closing',
    width: 180,
    valueFormatter: (value) => formatDateTime(value),
  },
  { field: 'bidCount', headerName: 'Bids', type: 'number', width: 80 },
  {
    field: 'estimatedValueMinor',
    headerName: 'Estimated value',
    type: 'number',
    width: 160,
    valueFormatter: (value, row) => formatMoney(value, row.currency),
  },
  { field: 'requisitionNumber', headerName: 'Requisition', width: 140 },
  { field: 'projectCode', headerName: 'Project', width: 140 },
];

export default function TendersPage() {
  const navigate = useNavigate();
  const { data, isLoading, error } = useTenders();

  return (
    <>
      <PageHeader title="Tenders" subtitle="Calls for bids, sealed bids and bid openings" />
      <Alert severity="info" sx={{ mb: 2 }}>
        To start a tender, open an approved requisition and choose &quot;Create tender&quot;.
      </Alert>
      <ErrorAlert error={error} title="Could not load tenders" />
      <Card>
        <DataGrid
          rows={data ?? []}
          columns={columns}
          loading={isLoading}
          autoHeight
          disableRowSelectionOnClick
          onRowClick={({ id }) => navigate(`/tenders/${id}`)}
          initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          pageSizeOptions={[25, 50, 100]}
          localeText={{ noRowsLabel: 'No tenders yet' }}
          sx={{ border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' } }}
        />
      </Card>
    </>
  );
}
