import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router';
import { Badge, Box, Button, Card, Tab, Tabs } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import RequisitionStatusChip from './RequisitionStatusChip.jsx';
import { useRequisitions } from '../../api/requisitions.js';
import { formatMoney } from '../../utils/money.js';
import { roleLabel } from '../../domain/procurement.js';

const TABS = [
  { scope: 'AWAITING_ME', label: 'Awaiting my approval' },
  { scope: 'MINE', label: 'My requisitions' },
  { scope: 'ALL', label: 'All' },
];

const columns = [
  { field: 'number', headerName: 'Number', width: 140 },
  { field: 'title', headerName: 'Title', flex: 1, minWidth: 200 },
  {
    field: 'project',
    headerName: 'Project',
    width: 150,
    valueGetter: (value) => value?.code ?? '',
  },
  {
    field: 'estimatedTotalMinor',
    headerName: 'Estimated value',
    type: 'number',
    width: 160,
    valueFormatter: (value, row) => formatMoney(value, row.currency),
  },
  {
    field: 'status',
    headerName: 'Status',
    width: 190,
    renderCell: ({ value }) => <RequisitionStatusChip status={value} />,
  },
  {
    field: 'pendingRole',
    headerName: 'With',
    width: 190,
    valueGetter: (value) => (value ? roleLabel(value) : ''),
  },
  {
    field: 'requestedBy',
    headerName: 'Requested by',
    width: 200,
    valueGetter: (value) => value?.displayName ?? '',
  },
  {
    field: 'createdAt',
    headerName: 'Created',
    type: 'date',
    width: 120,
    valueGetter: (value) => (value ? new Date(value) : null),
  },
];

export default function RequisitionsPage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState(0);
  const scope = TABS[tab].scope;
  const { data, isLoading, error } = useRequisitions(scope);
  const awaiting = useRequisitions('AWAITING_ME');

  return (
    <>
      <PageHeader
        title="Requisitions"
        subtitle="Purchase requests for projects, and their approvals"
        actions={
          <Button variant="contained" startIcon={<AddIcon />} component={RouterLink} to="/requisitions/new">
            New requisition
          </Button>
        }
      />
      <ErrorAlert error={error} title="Could not load requisitions" />
      <Card>
        <Box sx={{ borderBottom: 1, borderColor: 'divider', px: 2 }}>
          <Tabs value={tab} onChange={(_e, v) => setTab(v)} variant="scrollable" allowScrollButtonsMobile>
            {TABS.map((t) => (
              <Tab
                key={t.scope}
                label={
                  t.scope === 'AWAITING_ME' && awaiting.data?.length ? (
                    <Badge badgeContent={awaiting.data.length} color="error" sx={{ pr: 1.5 }}>
                      {t.label}
                    </Badge>
                  ) : (
                    t.label
                  )
                }
              />
            ))}
          </Tabs>
        </Box>
        <DataGrid
          rows={data ?? []}
          columns={columns}
          loading={isLoading}
          autoHeight
          disableRowSelectionOnClick
          onRowClick={({ id }) => navigate(`/requisitions/${id}`)}
          initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          pageSizeOptions={[25, 50, 100]}
          localeText={{
            noRowsLabel:
              scope === 'AWAITING_ME' ? 'Nothing is waiting for your approval' : 'No requisitions yet',
          }}
          sx={{ border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' } }}
        />
      </Card>
    </>
  );
}
