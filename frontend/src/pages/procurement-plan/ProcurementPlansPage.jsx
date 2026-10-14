import { useState } from 'react';
import PropTypes from 'prop-types';
import { useNavigate } from 'react-router';
import {
  Alert,
  Button,
  Card,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Stack,
  Switch,
  TextField,
} from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import { useCreatePlan, useProcurementPlans } from '../../api/procurementPlans.js';
import { isProcurement, useMe } from '../../api/me.js';
import { PLAN_STATUS, fyLabel, nextFyStart } from '../../domain/procurementPlan.js';
import { formatDate } from '../../domain/contracts.js';
import { formatMoney } from '../../utils/money.js';

const columns = [
  {
    field: 'financialYear',
    headerName: 'Financial year',
    width: 170,
    renderCell: ({ row }) => (
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', height: '100%' }}>
        <span>{row.financialYear}</span>
        {row.current && <Chip size="small" label="Current" color="primary" variant="outlined" />}
      </Stack>
    ),
  },
  {
    field: 'status',
    headerName: 'Status',
    width: 230,
    renderCell: ({ row }) => {
      const s = PLAN_STATUS[row.status];
      return <Chip size="small" label={s.label} color={s.color} />;
    },
  },
  { field: 'revision', headerName: 'Approvals', width: 110, type: 'number' },
  { field: 'departmentCount', headerName: 'Departments', width: 120, type: 'number' },
  { field: 'lineCount', headerName: 'Lines', width: 90, type: 'number' },
  { field: 'totalMinor', headerName: 'Planned value', width: 190, type: 'number', valueFormatter: (v) => formatMoney(v) },
  {
    field: 'fyStart',
    headerName: 'Period',
    flex: 1,
    minWidth: 220,
    valueGetter: (v, row) => `${formatDate(row.fyStart)} – ${formatDate(row.fyEnd)}`,
  },
];

function NewPlanDialog({ onClose }) {
  const navigate = useNavigate();
  const create = useCreatePlan();
  const [fyStart, setFyStart] = useState(nextFyStart());
  const [copy, setCopy] = useState(true);
  const firstOfMonth = /^\d{4}-\d{2}-01$/.test(fyStart);
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>New procurement plan</DialogTitle>
      <DialogContent>
        {create.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {create.error.message}
          </Alert>
        )}
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Financial year starts"
            type="date"
            value={fyStart}
            onChange={(e) => setFyStart(e.target.value)}
            error={!firstOfMonth}
            helperText={firstOfMonth ? `Financial year ${fyLabel(fyStart)}` : 'Use the first day of a month'}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <FormControlLabel
            control={<Switch checked={copy} onChange={(e) => setCopy(e.target.checked)} />}
            label="Start with last year's departments and heads"
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={!firstOfMonth}
          loading={create.isPending}
          onClick={() =>
            create.mutate({ fyStart, copyDepartments: copy }, { onSuccess: (p) => navigate(`/procurement-plans/${p.id}`) })
          }
        >
          Create
        </Button>
      </DialogActions>
    </Dialog>
  );
}

NewPlanDialog.propTypes = { onClose: PropTypes.func.isRequired };

export default function ProcurementPlansPage() {
  const navigate = useNavigate();
  const { data: me } = useMe();
  const { data, isLoading, error } = useProcurementPlans();
  const [creating, setCreating] = useState(false);
  return (
    <>
      <PageHeader
        title="Procurement plans"
        subtitle="Each financial year's planned purchases, prepared by departments and approved by the accounting officer"
        actions={
          isProcurement(me) && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>
              New plan
            </Button>
          )
        }
      />
      <ErrorAlert error={error} title="Could not load procurement plans" />
      <Card>
        <DataGrid
          rows={data ?? []}
          columns={columns}
          loading={isLoading}
          autoHeight
          disableRowSelectionOnClick
          onRowClick={({ id }) => navigate(`/procurement-plans/${id}`)}
          hideFooter
          localeText={{ noRowsLabel: 'No procurement plans yet' }}
          sx={{ border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' } }}
        />
      </Card>
      {creating && <NewPlanDialog onClose={() => setCreating(false)} />}
    </>
  );
}
