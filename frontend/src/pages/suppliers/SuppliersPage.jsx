import { useState } from 'react';
import { Button, Card, Chip, Rating, Stack, TextField, Tooltip } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import UploadFileOutlined from '@mui/icons-material/UploadFileOutlined';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import SupplierDialog from './SupplierDialog.jsx';
import ImportDialog from './ImportDialog.jsx';
import { useSuppliers } from '../../api/suppliers.js';
import { useSupplierPerformance } from '../../api/contracts.js';

const STATUS_CHIP = {
  ACTIVE: { label: 'Active', color: 'success' },
  SUSPENDED: { label: 'Suspended', color: 'warning' },
  DEBARRED: { label: 'Debarred', color: 'error' },
};
const AGPO = { YOUTH: 'Youth', WOMEN: 'Women', PWD: 'PWD' };

const columns = [
  { field: 'name', headerName: 'Supplier', flex: 1, minWidth: 220 },
  { field: 'kraPin', headerName: 'KRA PIN', width: 140 },
  { field: 'categories', headerName: 'Categories', flex: 1, minWidth: 180 },
  {
    field: 'agpoCategory',
    headerName: 'AGPO',
    width: 140,
    renderCell: ({ value, row }) =>
      value ? (
        <Chip
          size="small"
          label={AGPO[value]}
          color={row.agpoValid ? 'secondary' : 'default'}
          variant="outlined"
          title={row.agpoValid ? undefined : 'Certificate expired'}
        />
      ) : null,
  },
  { field: 'email', headerName: 'Email', width: 220 },
  { field: 'phone', headerName: 'Phone', width: 150 },
  {
    field: 'status',
    headerName: 'Status',
    width: 120,
    renderCell: ({ value }) => <Chip size="small" variant="outlined" {...STATUS_CHIP[value]} />,
  },
  {
    field: 'performance',
    headerName: 'Past performance',
    width: 170,
    valueGetter: (value) => value?.averageRating ?? null,
    renderCell: ({ row }) =>
      row.performance ? (
        <Tooltip title={`${row.performance.contracts} contract(s), ${row.performance.completed} completed`}>
          <span>
            {row.performance.averageRating != null ? (
              <Rating size="small" value={row.performance.averageRating} precision={0.1} readOnly />
            ) : (
              `${row.performance.contracts} contract(s)`
            )}
          </span>
        </Tooltip>
      ) : (
        '—'
      ),
  },
];

export default function SuppliersPage() {
  const { data, isLoading, error } = useSuppliers();
  const { data: performance } = useSupplierPerformance();
  const perfById = Object.fromEntries((performance ?? []).map((p) => [p.supplierId, p]));
  const [editing, setEditing] = useState(null); // null | 'new' | supplier
  const [importing, setImporting] = useState(false);
  const [search, setSearch] = useState('');

  const q = search.trim().toLowerCase();
  const rows = (data ?? []).map((s) => ({ ...s, performance: perfById[s.id] })).filter(
    (s) =>
      !q ||
      s.name.toLowerCase().includes(q) ||
      s.kraPin.toLowerCase().includes(q) ||
      (s.categories ?? '').toLowerCase().includes(q),
  );

  return (
    <>
      <PageHeader
        title="Suppliers"
        subtitle="The supplier register used for invitations and bids"
        actions={
          <Stack direction="row" spacing={1}>
            <Button startIcon={<UploadFileOutlined />} onClick={() => setImporting(true)}>
              Import CSV
            </Button>
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>
              Add supplier
            </Button>
          </Stack>
        }
      />
      <ErrorAlert error={error} title="Could not load suppliers" />
      <TextField
        size="small"
        placeholder="Search by name, KRA PIN or category"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        sx={{ mb: 2, width: { xs: '100%', sm: 360 } }}
      />
      <Card>
        <DataGrid
          rows={rows}
          columns={columns}
          loading={isLoading}
          autoHeight
          disableRowSelectionOnClick
          onRowClick={({ row }) => setEditing(row)}
          initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          pageSizeOptions={[25, 50, 100]}
          sx={{ border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' } }}
        />
      </Card>
      {editing && (
        <SupplierDialog supplier={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />
      )}
      {importing && <ImportDialog onClose={() => setImporting(false)} />}
    </>
  );
}
