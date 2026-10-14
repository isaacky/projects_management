import { useState } from 'react';
import PropTypes from 'prop-types';
import { Link as RouterLink } from 'react-router';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  LinearProgress,
  Link,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { usePlanAction } from '../../api/procurementPlans.js';
import { CATEGORY, quarterRange } from '../../domain/procurementPlan.js';
import { formatDate } from '../../domain/contracts.js';
import { formatMoney } from '../../utils/money.js';
import RequisitionStatusChip from '../requisitions/RequisitionStatusChip.jsx';

function pctOf(part, total) {
  return total > 0 ? Math.round((part * 1000) / total) / 10 : 0;
}

/** Planned value per group, with how much of it requisitions already draw on. */
function BucketTable({ title, buckets, total, label }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" sx={{ mb: 1 }}>
          {title}
        </Typography>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell />
              <TableCell align="right">Lines</TableCell>
              <TableCell align="right">Planned</TableCell>
              <TableCell sx={{ width: '35%' }}>Requisitioned</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {buckets.map((b) => (
              <TableRow key={b.key}>
                <TableCell>{label(b.key)}</TableCell>
                <TableCell align="right">{b.lines}</TableCell>
                <TableCell align="right">
                  {formatMoney(b.plannedMinor, 'KES', { compact: true })}
                  <Typography variant="caption" color="text.secondary" component="div">
                    {pctOf(b.plannedMinor, total)}% of plan
                  </Typography>
                </TableCell>
                <TableCell>
                  <LinearProgress
                    variant="determinate"
                    value={Math.min(100, pctOf(b.committedMinor, b.plannedMinor))}
                    sx={{ height: 8, borderRadius: 4 }}
                    aria-label={`${label(b.key)} requisitioned`}
                  />
                  <Typography variant="caption" color="text.secondary">
                    {pctOf(b.committedMinor, b.plannedMinor)}%
                  </Typography>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

BucketTable.propTypes = {
  title: PropTypes.string.isRequired,
  buckets: PropTypes.array.isRequired,
  total: PropTypes.number.isRequired,
  label: PropTypes.func.isRequired,
};

function AgpoTargetDialog({ plan, onClose }) {
  const action = usePlanAction(plan.id);
  const [value, setValue] = useState(String(plan.agpoTargetPct));
  const n = Number(value);
  const valid = value.trim() !== '' && n >= 0 && n <= 100;
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>AGPO target</DialogTitle>
      <DialogContent>
        {action.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {action.error.message}
          </Alert>
        )}
        <Typography variant="body2" sx={{ mb: 2 }}>
          The share of the plan to reserve for youth, women and persons with disabilities. The law sets a minimum of
          30%.
        </Typography>
        <TextField
          label="Target (%)"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          error={!valid}
          inputMode="decimal"
          fullWidth
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={!valid}
          loading={action.isPending}
          onClick={() => action.mutate({ path: '/settings', method: 'PUT', body: { agpoTargetPct: n } }, { onSuccess: onClose })}
        >
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

AgpoTargetDialog.propTypes = { plan: PropTypes.object.isRequired, onClose: PropTypes.func.isRequired };

export default function PlanSummaryTab({ plan }) {
  const s = plan.summary;
  const [editingTarget, setEditingTarget] = useState(false);
  const agpo = Number(s.agpoPct);
  const target = Number(s.agpoTargetPct);
  return (
    <Grid container spacing={3}>
      <Grid size={{ xs: 12, md: 6 }}>
        <BucketTable
          title="By quarter"
          buckets={s.byQuarter}
          total={s.totalMinor}
          label={(k) => `${k} · ${quarterRange(plan.fyStart, Number(k.slice(1)))}`}
        />
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="h6">Reserved for AGPO groups</Typography>
              {plan.permissions.canManage && plan.status === 'DRAFT' && (
                <Button size="small" onClick={() => setEditingTarget(true)}>
                  Change target
                </Button>
              )}
            </Stack>
            <Typography variant="h4" sx={{ my: 1, color: agpo < target ? 'warning.main' : 'success.main' }}>
              {agpo}%
            </Typography>
            <Box sx={{ position: 'relative', mb: 1 }}>
              <LinearProgress
                variant="determinate"
                value={Math.min(100, agpo)}
                color={agpo < target ? 'warning' : 'success'}
                sx={{ height: 10, borderRadius: 5 }}
                aria-label="Share reserved for AGPO groups"
              />
              <Box
                sx={{ position: 'absolute', top: -3, bottom: -3, left: `${Math.min(100, target)}%`, width: 2, bgcolor: 'text.primary' }}
                title={`Target ${target}%`}
              />
            </Box>
            <Typography variant="body2" color="text.secondary">
              {formatMoney(s.agpoMinor)} of {formatMoney(s.totalMinor)} · target {target}%
            </Typography>
          </CardContent>
        </Card>
        <BucketTable title="By category" buckets={s.byCategory} total={s.totalMinor} label={(k) => CATEGORY[k] ?? k} />
      </Grid>
      <Grid size={12}>
        <BucketTable title="By method" buckets={s.byMethod} total={s.totalMinor} label={(k) => k} />
      </Grid>
      {editingTarget && <AgpoTargetDialog plan={plan} onClose={() => setEditingTarget(false)} />}
    </Grid>
  );
}

PlanSummaryTab.propTypes = { plan: PropTypes.object.isRequired };

export function UnplannedTab({ plan }) {
  return (
    <>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Requisitions submitted this financial year that name no plan line. Each one needed a reason and the accounting
        officer&apos;s approval. Many of them, or large ones, may mean the plan needs a revision.
      </Typography>
      <Card>
        <TableContainer>
          <Table size="small" sx={{ minWidth: 800 }}>
            <TableHead>
              <TableRow>
                <TableCell>Requisition</TableCell>
                <TableCell>Reason</TableCell>
                <TableCell align="right">Amount</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Submitted</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {plan.unplanned.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5}>
                    <Typography variant="body2" color="text.secondary">
                      None.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {plan.unplanned.map((u) => (
                <TableRow key={u.requisitionId}>
                  <TableCell>
                    <Link component={RouterLink} to={`/requisitions/${u.requisitionId}`}>
                      {u.number}
                    </Link>
                    <Typography variant="body2">{u.title}</Typography>
                  </TableCell>
                  <TableCell sx={{ maxWidth: 360 }}>{u.reason}</TableCell>
                  <TableCell align="right">{formatMoney(u.amountMinor)}</TableCell>
                  <TableCell>
                    <RequisitionStatusChip status={u.status} />
                  </TableCell>
                  <TableCell>{formatDate(u.submittedAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>
    </>
  );
}

UnplannedTab.propTypes = { plan: PropTypes.object.isRequired };

export function ApprovalsTab({ plan }) {
  return (
    <Card>
      <TableContainer>
        <Table size="small" sx={{ minWidth: 700 }}>
          <TableHead>
            <TableRow>
              <TableCell>Approval</TableCell>
              <TableCell>By</TableCell>
              <TableCell>On</TableCell>
              <TableCell align="right">Lines</TableCell>
              <TableCell align="right">Value</TableCell>
              <TableCell>Reason for revision / note</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {plan.revisions.length === 0 && (
              <TableRow>
                <TableCell colSpan={6}>
                  <Typography variant="body2" color="text.secondary">
                    Not approved yet.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
            {plan.revisions.map((r) => (
              <TableRow key={r.revision}>
                <TableCell>{r.revision === 1 ? 'Original plan' : `Revision ${r.revision - 1}`}</TableCell>
                <TableCell>{r.approvedBy?.name}</TableCell>
                <TableCell>{formatDate(r.approvedAt)}</TableCell>
                <TableCell align="right">{r.lineCount}</TableCell>
                <TableCell align="right">{formatMoney(r.totalMinor)}</TableCell>
                <TableCell>
                  {r.reason && <Typography variant="body2">{r.reason}</Typography>}
                  {r.note && (
                    <Typography variant="body2" color="text.secondary">
                      {r.note}
                    </Typography>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Card>
  );
}

ApprovalsTab.propTypes = { plan: PropTypes.object.isRequired };
