import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Button,
  Card,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { usePlanAction } from '../../api/procurementPlans.js';
import { DEPT_STATUS } from '../../domain/procurementPlan.js';
import { formatDate } from '../../domain/contracts.js';
import { formatMoney } from '../../utils/money.js';
import { DepartmentDialog, ImportDialog, NoteDialog } from './PlanDialogs.jsx';

export default function PlanDepartmentsTab({ plan }) {
  const action = usePlanAction(plan.id);
  const [editing, setEditing] = useState(null); // department, or {} for a new one
  const [returning, setReturning] = useState(null);
  const [importing, setImporting] = useState(null);

  const run = (vars, onDone) => action.mutate(vars, { onSuccess: onDone });
  const close = (setter) => () => {
    action.reset();
    setter(null);
  };

  return (
    <>
      <Stack direction="row" sx={{ mb: 2, justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          Each head enters their department&apos;s planned purchases and submits them. Procurement can return a
          department&apos;s lines for changes, and edit them while consolidating.
        </Typography>
        {plan.permissions.canAddDepartment && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing({})} sx={{ flexShrink: 0, ml: 2 }}>
            Add department
          </Button>
        )}
      </Stack>
      {action.error && !editing && !returning && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {action.error.message}
        </Alert>
      )}
      <Card>
        <TableContainer>
          <Table size="small" sx={{ minWidth: 900 }}>
            <TableHead>
              <TableRow>
                <TableCell>Department</TableCell>
                <TableCell>Head</TableCell>
                <TableCell align="right">Lines</TableCell>
                <TableCell align="right">Planned</TableCell>
                <TableCell align="right">Ceiling</TableCell>
                <TableCell>Status</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {plan.departments.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7}>
                    <Typography variant="body2" color="text.secondary">
                      No departments yet.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {plan.departments.map((d) => {
                const s = DEPT_STATUS[d.status];
                return (
                  <TableRow key={d.id}>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {d.name}
                      </Typography>
                      {d.returnNote && d.status === 'DRAFT' && (
                        <Typography variant="caption" color="warning.main">
                          Returned: {d.returnNote}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>{d.head?.name}</TableCell>
                    <TableCell align="right">{d.lineCount}</TableCell>
                    <TableCell align="right" sx={{ color: d.overCeiling ? 'error.main' : undefined }}>
                      {formatMoney(d.totalMinor)}
                    </TableCell>
                    <TableCell align="right">{d.ceilingMinor === null ? '—' : formatMoney(d.ceilingMinor)}</TableCell>
                    <TableCell>
                      <Tooltip title={d.submittedAt ? `${d.submittedBy?.name}, ${formatDate(d.submittedAt)}` : ''}>
                        <Chip size="small" label={s.label} color={s.color} />
                      </Tooltip>
                    </TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
                        {d.canEditLines && (
                          <Button size="small" onClick={() => setImporting(d)}>
                            Import CSV
                          </Button>
                        )}
                        {d.canSubmit && (
                          <Button
                            size="small"
                            variant="contained"
                            disabled={d.overCeiling}
                            loading={action.isPending && action.variables?.path === `/departments/${d.id}/submit`}
                            onClick={() => run({ path: `/departments/${d.id}/submit` })}
                          >
                            Submit
                          </Button>
                        )}
                        {d.canReturn && (
                          <Button size="small" onClick={() => setReturning(d)}>
                            Return
                          </Button>
                        )}
                        {d.canEdit && (
                          <Button size="small" onClick={() => setEditing(d)}>
                            Edit
                          </Button>
                        )}
                        {d.canDelete && (
                          <Button
                            size="small"
                            color="error"
                            onClick={() => run({ path: `/departments/${d.id}`, method: 'DELETE' })}
                          >
                            Remove
                          </Button>
                        )}
                      </Stack>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {editing && (
        <DepartmentDialog
          department={editing.id ? editing : null}
          pending={action.isPending}
          error={action.error}
          onClose={close(setEditing)}
          onSave={(body) =>
            run(
              editing.id
                ? { path: `/departments/${editing.id}`, method: 'PUT', body }
                : { path: '/departments', body },
              () => setEditing(null),
            )
          }
        />
      )}
      {returning && (
        <NoteDialog
          open
          title={`Return ${returning.name}'s lines`}
          text={`${returning.head?.name} will be notified and can change the lines and submit again.`}
          label="What needs to change"
          required
          confirm="Return"
          pending={action.isPending}
          error={action.error}
          onClose={close(setReturning)}
          onConfirm={(note) =>
            run({ path: `/departments/${returning.id}/return`, body: { note } }, () => setReturning(null))
          }
        />
      )}
      {importing && <ImportDialog planId={plan.id} department={importing} onClose={() => setImporting(null)} />}
    </>
  );
}

PlanDepartmentsTab.propTypes = { plan: PropTypes.object.isRequired };
