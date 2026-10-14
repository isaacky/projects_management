import { useNavigate } from 'react-router';
import { Alert, Card } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import ContractStatusChip from './ContractStatusChip.jsx';
import { useContracts } from '../../api/contracts.js';
import { formatDate, share } from '../../domain/contracts.js';
import { formatMoney } from '../../utils/money.js';

const columns = [
  { field: 'number', headerName: 'Number', width: 140 },
  { field: 'title', headerName: 'Title', flex: 1, minWidth: 220 },
  { field: 'supplier', headerName: 'Supplier', width: 220, valueGetter: (value) => value?.name },
  { field: 'status', headerName: 'Status', width: 130, renderCell: ({ row }) => <ContractStatusChip status={row.status} /> },
  {
    field: 'currentValueMinor',
    headerName: 'Value',
    type: 'number',
    width: 160,
    valueFormatter: (value, row) => formatMoney(value, row.currency),
  },
  {
    field: 'paidMinor',
    headerName: 'Paid',
    width: 170,
    valueFormatter: (value, row) =>
      `${formatMoney(value, row.currency, { compact: true })} (${share(value, row.currentValueMinor)}%)`,
  },
  { field: 'endDate', headerName: 'Ends', width: 130, valueFormatter: (value) => formatDate(value) },
  { field: 'projectCode', headerName: 'Project', width: 130 },
  { field: 'contractManager', headerName: 'Contract manager', width: 200, valueGetter: (value) => value?.name ?? '—' },
];

export default function ContractsPage() {
  const navigate = useNavigate();
  const { data, isLoading, error } = useContracts();
  return (
    <>
      <PageHeader title="Contracts" subtitle="Signed contracts, payments, variations and securities" />
      <Alert severity="info" sx={{ mb: 2 }}>
        To prepare a contract, open an awarded tender and choose &quot;Prepare contract&quot;.
      </Alert>
      <ErrorAlert error={error} title="Could not load contracts" />
      <Card>
        <DataGrid
          rows={data ?? []}
          columns={columns}
          loading={isLoading}
          autoHeight
          disableRowSelectionOnClick
          onRowClick={({ id }) => navigate(`/contracts/${id}`)}
          initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          pageSizeOptions={[25, 50, 100]}
          localeText={{ noRowsLabel: 'No contracts yet' }}
          sx={{ border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' } }}
        />
      </Card>
    </>
  );
}
