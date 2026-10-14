import { useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import {
  Button,
  Card,
  Chip,
  FormControlLabel,
  IconButton,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import EditOutlined from '@mui/icons-material/EditOutlined';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import RemoveCircleOutline from '@mui/icons-material/RemoveCircleOutline';
import { usePlanAction } from '../../api/procurementPlans.js';
import { CATEGORY, RESERVED, STAGE, filterLines } from '../../domain/procurementPlan.js';
import { formatMoney } from '../../utils/money.js';
import { LineDialog, NoteDialog } from './PlanDialogs.jsx';

export default function PlanLinesTab({ plan }) {
  const action = usePlanAction(plan.id);
  const [filter, setFilter] = useState({ departmentId: '', quarter: '', category: '', stage: '', q: '' });
  const [showDropped, setShowDropped] = useState(false);
  const [onlyBehind, setOnlyBehind] = useState(false);
  const [onlyChanged, setOnlyChanged] = useState(false);
  const [editing, setEditing] = useState(null); // line, or {} for a new one
  const [dropping, setDropping] = useState(null);

  const editableDepts = plan.departments.filter((d) => d.canEditLines);
  const revising = plan.status === 'DRAFT' && plan.revision > 0;
  const rows = useMemo(
    () => filterLines(plan.lines, { ...filter, showDropped, onlyBehind, onlyChanged }),
    [plan.lines, filter, showDropped, onlyBehind, onlyChanged],
  );
  const shownTotal = rows.filter((l) => !l.cancelled).reduce((s, l) => s + l.estimateMinor, 0);
  const set = (k) => (e) => setFilter((f) => ({ ...f, [k]: e.target.value }));

  const run = (vars, onDone) => action.mutate(vars, { onSuccess: onDone });
  const closeEditor = () => {
    action.reset();
    setEditing(null);
  };

  const columns = [
    {
      field: 'reference',
      headerName: 'Ref',
      width: 140,
      renderCell: ({ row }) => (
        <span style={{ textDecoration: row.cancelled ? 'line-through' : undefined }}>{row.reference}</span>
      ),
    },
    { field: 'department', headerName: 'Department', width: 160 },
    {
      field: 'description',
      headerName: 'Description',
      flex: 1,
      minWidth: 240,
      renderCell: ({ row }) => (
        <Tooltip title={row.cancelled ? `Dropped: ${row.cancelNote}` : row.description}>
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', height: '100%', overflow: 'hidden' }}>
            <span style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{row.description}</span>
            {row.change === 'NEW' && revising && <Chip size="small" label="New" color="info" />}
            {row.change === 'CHANGED' && <Chip size="small" label="Changed" color="info" />}
            {row.cancelled && <Chip size="small" label="Dropped" />}
          </Stack>
        </Tooltip>
      ),
    },
    { field: 'category', headerName: 'Category', width: 150, valueGetter: (v) => CATEGORY[v] ?? v },
    { field: 'estimateMinor', headerName: 'Estimate', type: 'number', width: 160, valueFormatter: (v) => formatMoney(v) },
    { field: 'methodCode', headerName: 'Method', width: 150 },
    { field: 'quarter', headerName: 'Qtr', width: 70, valueFormatter: (v) => `Q${v}` },
    {
      field: 'reservedGroup',
      headerName: 'Reserved',
      width: 130,
      valueGetter: (v) => (v ? RESERVED[v] : ''),
    },
    {
      field: 'committedMinor',
      headerName: 'Requisitioned',
      type: 'number',
      width: 150,
      valueFormatter: (v) => (v ? formatMoney(v) : '—'),
    },
    {
      field: 'balanceMinor',
      headerName: 'Balance',
      type: 'number',
      width: 150,
      valueGetter: (v, row) => (row.approvedEstimateMinor === null ? null : v),
      valueFormatter: (v) => (v === null ? 'Not approved' : formatMoney(v)),
    },
    {
      field: 'stage',
      headerName: 'Progress',
      width: 170,
      renderCell: ({ row }) => (
        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', height: '100%' }}>
          <Chip size="small" label={STAGE[row.stage].label} color={STAGE[row.stage].color} variant="outlined" />
          {row.behind && (
            <Tooltip title={`Planned for Q${row.quarter}, which has ended, and no requisition yet`}>
              <Chip size="small" label="Behind" color="error" />
            </Tooltip>
          )}
        </Stack>
      ),
    },
    {
      field: 'actions',
      headerName: '',
      width: 120,
      sortable: false,
      renderCell: ({ row }) => (
        <Stack direction="row" sx={{ height: '100%', alignItems: 'center' }}>
          {row.canEdit && (
            <Tooltip title="Edit">
              <IconButton size="small" onClick={() => setEditing(row)} aria-label={`Edit ${row.reference}`}>
                <EditOutlined fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
          {row.canDelete && (
            <Tooltip title="Delete">
              <IconButton
                size="small"
                onClick={() => run({ path: `/lines/${row.id}`, method: 'DELETE' })}
                aria-label={`Delete ${row.reference}`}
              >
                <DeleteOutline fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
          {row.canCancel && (
            <Tooltip title="Drop from the plan">
              <IconButton size="small" onClick={() => setDropping(row)} aria-label={`Drop ${row.reference}`}>
                <RemoveCircleOutline fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Stack>
      ),
    },
  ];

  return (
    <>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2, alignItems: { md: 'center' } }}>
        <TextField size="small" label="Search" value={filter.q} onChange={set('q')} sx={{ minWidth: 180 }} />
        <TextField select size="small" label="Department" value={filter.departmentId} onChange={set('departmentId')} sx={{ minWidth: 170 }}>
          <MenuItem value="">All</MenuItem>
          {plan.departments.map((d) => (
            <MenuItem key={d.id} value={d.id}>
              {d.name}
            </MenuItem>
          ))}
        </TextField>
        <TextField select size="small" label="Quarter" value={filter.quarter} onChange={set('quarter')} sx={{ minWidth: 100 }}>
          <MenuItem value="">All</MenuItem>
          {[1, 2, 3, 4].map((q) => (
            <MenuItem key={q} value={q}>
              Q{q}
            </MenuItem>
          ))}
        </TextField>
        <TextField select size="small" label="Category" value={filter.category} onChange={set('category')} sx={{ minWidth: 150 }}>
          <MenuItem value="">All</MenuItem>
          {Object.entries(CATEGORY).map(([k, v]) => (
            <MenuItem key={k} value={k}>
              {v}
            </MenuItem>
          ))}
        </TextField>
        <TextField select size="small" label="Progress" value={filter.stage} onChange={set('stage')} sx={{ minWidth: 140 }}>
          <MenuItem value="">All</MenuItem>
          {Object.entries(STAGE).map(([k, v]) => (
            <MenuItem key={k} value={k}>
              {v.label}
            </MenuItem>
          ))}
        </TextField>
        <Stack direction="row" sx={{ flexWrap: 'wrap' }}>
          <FormControlLabel control={<Switch size="small" checked={onlyBehind} onChange={(e) => setOnlyBehind(e.target.checked)} />} label="Behind" />
          {revising && (
            <FormControlLabel control={<Switch size="small" checked={onlyChanged} onChange={(e) => setOnlyChanged(e.target.checked)} />} label="Changes" />
          )}
          <FormControlLabel control={<Switch size="small" checked={showDropped} onChange={(e) => setShowDropped(e.target.checked)} />} label="Dropped" />
        </Stack>
        <Stack direction="row" sx={{ flexGrow: 1, justifyContent: 'flex-end' }}>
          {editableDepts.length > 0 && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing({})}>
              Add line
            </Button>
          )}
        </Stack>
      </Stack>
      {action.error && !editing && !dropping && (
        <Typography color="error" variant="body2" sx={{ mb: 1 }}>
          {action.error.message}
        </Typography>
      )}
      <Card>
        <DataGrid
          rows={rows}
          columns={columns}
          autoHeight
          density="compact"
          disableRowSelectionOnClick
          initialState={{ pagination: { paginationModel: { pageSize: 50 } } }}
          pageSizeOptions={[25, 50, 100]}
          localeText={{ noRowsLabel: plan.lines.length ? 'No lines match the filters' : 'No lines yet' }}
          sx={{ border: 0 }}
        />
      </Card>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
        {rows.length} line{rows.length === 1 ? '' : 's'} shown · {formatMoney(shownTotal)}
      </Typography>

      {editing && (
        <LineDialog
          plan={plan}
          line={editing.id ? editing : null}
          departments={editing.id ? plan.departments.filter((d) => d.canEditLines || d.id === editing.departmentId) : editableDepts}
          pending={action.isPending}
          error={action.error}
          onClose={closeEditor}
          onSave={(body) =>
            run(
              editing.id ? { path: `/lines/${editing.id}`, method: 'PUT', body } : { path: '/lines', body },
              () => setEditing(null),
            )
          }
        />
      )}
      {dropping && (
        <NoteDialog
          open
          title={`Drop ${dropping.reference}`}
          text={`"${dropping.description}" will stay in the plan's record as dropped, and requisitions can no longer draw on it.`}
          label="Reason"
          required
          confirm="Drop line"
          pending={action.isPending}
          error={action.error}
          onClose={() => {
            action.reset();
            setDropping(null);
          }}
          onConfirm={(note) => run({ path: `/lines/${dropping.id}/cancel`, body: { note } }, () => setDropping(null))}
        />
      )}
    </>
  );
}

PlanLinesTab.propTypes = { plan: PropTypes.object.isRequired };
