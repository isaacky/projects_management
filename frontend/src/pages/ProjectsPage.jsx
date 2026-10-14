import { useState } from 'react';
import { useNavigate } from 'react-router';
import { Button, Card } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import PageHeader from '../components/PageHeader.jsx';
import ErrorAlert from '../components/ErrorAlert.jsx';
import PhaseChip from '../components/PhaseChip.jsx';
import NewProjectDialog from './NewProjectDialog.jsx';
import { useProjects } from '../api/projects.js';
import { formatMoney } from '../utils/money.js';

const columns = [
  { field: 'code', headerName: 'Code', width: 150 },
  { field: 'name', headerName: 'Name', flex: 1, minWidth: 220 },
  {
    field: 'phase',
    headerName: 'Phase',
    width: 150,
    renderCell: ({ value }) => <PhaseChip phase={value} />,
  },
  {
    field: 'nextGate',
    headerName: 'Next gate',
    width: 220,
    valueGetter: (_value, row) => (row.nextGate ? `${row.nextGate} · ${row.nextGateName}` : '—'),
  },
  {
    field: 'budgetMinor',
    headerName: 'Budget',
    width: 170,
    type: 'number',
    valueFormatter: (value, row) => formatMoney(value, row.currency),
  },
  {
    field: 'updatedAt',
    headerName: 'Updated',
    width: 130,
    type: 'date',
    valueGetter: (value) => (value ? new Date(value) : null),
  },
];

export default function ProjectsPage() {
  const navigate = useNavigate();
  const { data, isLoading, error } = useProjects();
  const [dialogOpen, setDialogOpen] = useState(false);

  return (
    <>
      <PageHeader
        title="Projects"
        subtitle="Every project from initiation to benefits"
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
            New project
          </Button>
        }
      />
      <ErrorAlert error={error} title="Could not load projects" />
      <Card>
        <DataGrid
          rows={data ?? []}
          columns={columns}
          loading={isLoading}
          autoHeight
          disableRowSelectionOnClick
          onRowClick={({ id }) => navigate(`/projects/${id}`)}
          initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          pageSizeOptions={[25, 50, 100]}
          sx={{ border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' } }}
        />
      </Card>
      <NewProjectDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onCreated={(project) => navigate(`/projects/${project.id}`)}
      />
    </>
  );
}
