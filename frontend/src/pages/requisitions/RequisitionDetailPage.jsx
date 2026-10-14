import { useState } from 'react';
import PropTypes from 'prop-types';
import { Link as RouterLink, useParams } from 'react-router';
import {
  Alert,
  Box,
  Breadcrumbs,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Grid,
  Link,
  List,
  ListItem,
  ListItemText,
  Skeleton,
  Stack,
  Step,
  StepContent,
  StepLabel,
  Stepper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableFooter,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import RequisitionStatusChip from './RequisitionStatusChip.jsx';
import { useRequisition, useRequisitionAction, useRequisitionHistory } from '../../api/requisitions.js';
import { roleLabel } from '../../domain/procurement.js';
import { STEP_STATUS } from '../../domain/requisitions.js';
import { formatMoney } from '../../utils/money.js';
import { useTenders } from '../../api/tenders.js';
import { isProcurement, useMe } from '../../api/me.js';
import CreateTenderDialog from '../tenders/CreateTenderDialog.jsx';
import TenderStatusChip from '../tenders/TenderStatusChip.jsx';

const ACTIONS = {
  submit: {
    title: 'Submit for approval?',
    text: 'The approval chain is fixed from the current procurement settings, and you can no longer edit unless an approver returns it.',
    button: 'Submit',
    color: 'primary',
    comment: null,
  },
  approve: {
    title: 'Approve this requisition?',
    text: 'Your approval is recorded in the audit log. The next approver, if any, is notified.',
    button: 'Approve',
    color: 'success',
    comment: 'optional',
  },
  return: {
    title: 'Return for changes?',
    text: 'The requester can edit and resubmit. The approval chain starts again from step 1.',
    button: 'Return',
    color: 'warning',
    comment: 'required',
  },
  reject: {
    title: 'Reject this requisition?',
    text: 'Rejection is final. The requester will need to raise a new requisition.',
    button: 'Reject',
    color: 'error',
    comment: 'required',
  },
  cancel: {
    title: 'Withdraw this requisition?',
    text: 'It will be cancelled. Anyone waiting to approve it is told no action is needed.',
    button: 'Withdraw',
    color: 'error',
    comment: 'optional',
  },
};

function ActionDialog({ id, action, onClose }) {
  const cfg = ACTIONS[action];
  const [comment, setComment] = useState('');
  const run = useRequisitionAction(id);
  const blocked = cfg.comment === 'required' && !comment.trim();

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{cfg.title}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>{cfg.text}</DialogContentText>
        {run.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {run.error.message}
          </Alert>
        )}
        {cfg.comment && (
          <TextField
            label={cfg.comment === 'required' ? 'Reason (required)' : 'Comment (optional)'}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            multiline
            minRows={3}
            fullWidth
            autoFocus
          />
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Back</Button>
        <Button
          variant="contained"
          color={cfg.color}
          disabled={blocked}
          loading={run.isPending}
          onClick={() => run.mutate({ action, comment: comment.trim() }, { onSuccess: onClose })}
        >
          {cfg.button}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

ActionDialog.propTypes = {
  id: PropTypes.string.isRequired,
  action: PropTypes.oneOf(Object.keys(ACTIONS)).isRequired,
  onClose: PropTypes.func.isRequired,
};

function Field({ label, children }) {
  return (
    <Box sx={{ mb: 2 }}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body1">{children || '—'}</Typography>
    </Box>
  );
}

Field.propTypes = { label: PropTypes.string.isRequired, children: PropTypes.node };

function ApprovalChain({ approvals, round }) {
  const current = approvals.filter((a) => a.round === round);
  const earlier = approvals.filter((a) => a.round < round);
  if (!current.length) {
    return (
      <Typography variant="body2" color="text.secondary">
        Not submitted yet. The approval chain is set from the procurement settings when you submit.
      </Typography>
    );
  }
  const active = current.findIndex((a) => a.status === 'PENDING');
  return (
    <>
      <Stepper orientation="vertical" activeStep={active === -1 ? current.length : active}>
        {current.map((a) => (
          <Step key={a.id} completed={a.status === 'APPROVED'} expanded>
            <StepLabel
              error={a.status === 'REJECTED'}
              optional={
                <Typography variant="caption" color="text.secondary">
                  {STEP_STATUS[a.status]}
                  {a.decidedBy ? ` · ${a.decidedBy.displayName}` : ''}
                  {a.decidedAt ? ` · ${new Date(a.decidedAt).toLocaleString('en-KE')}` : ''}
                </Typography>
              }
            >
              {roleLabel(a.approverRole)}
            </StepLabel>
            <StepContent>
              {a.comment && (
                <Typography variant="body2" sx={{ fontStyle: 'italic' }}>
                  “{a.comment}”
                </Typography>
              )}
            </StepContent>
          </Step>
        ))}
      </Stepper>
      {earlier.length > 0 && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
          Round {round}. Earlier rounds are in the history below.
        </Typography>
      )}
    </>
  );
}

ApprovalChain.propTypes = {
  approvals: PropTypes.arrayOf(PropTypes.object).isRequired,
  round: PropTypes.number.isRequired,
};

const HISTORY_LABELS = {
  'requisition.created': 'Created',
  'requisition.updated': 'Edited',
  'requisition.submitted': 'Submitted for approval',
  'requisition.approved_step': 'Approved',
  'requisition.rejected': 'Rejected',
  'requisition.returned': 'Returned for changes',
  'requisition.cancelled': 'Withdrawn',
};

function safeParse(json) {
  try {
    return JSON.parse(json) ?? {};
  } catch {
    return {};
  }
}

/** The procurement plan line, or why the purchase is not in the plan. */
function PlanField({ r }) {
  if (!r.planLine && !r.unplannedReason && r.planStatus !== 'NO_PLAN') return null;
  return (
    <Box sx={{ mb: 2 }}>
      <Typography variant="caption" color="text.secondary">
        Procurement plan
      </Typography>
      {r.planLine && (
        <>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography variant="body1">
              {r.planLine.reference} · {r.planLine.description}
            </Typography>
            {r.planStatus === 'PLANNED' && <Chip size="small" color="success" variant="outlined" label="In the plan" />}
          </Stack>
          <Typography variant="body2" color="text.secondary">
            {r.planLine.department} · approved {formatMoney(r.planLine.approvedMinor)} · other requisitions{' '}
            {formatMoney(r.planLine.committedMinor)} · left {formatMoney(r.planLine.balanceMinor)}
          </Typography>
        </>
      )}
      {!r.planLine && r.unplannedReason && r.planStatus !== 'NO_PLAN' && (
        <>
          <Box>
            <Chip size="small" color="warning" label="Not in the plan" sx={{ my: 0.5 }} />
          </Box>
          <Typography variant="body1">{r.unplannedReason}</Typography>
        </>
      )}
      {!r.planLine && r.planStatus === 'NO_PLAN' && (
        <Typography variant="body1">No approved plan for the year when it was submitted</Typography>
      )}
    </Box>
  );
}

PlanField.propTypes = { r: PropTypes.object.isRequired };

export default function RequisitionDetailPage() {
  const { id } = useParams();
  const { data: r, isLoading, error } = useRequisition(id);
  const { data: history } = useRequisitionHistory(id);
  const [action, setAction] = useState(null);
  const [creatingTender, setCreatingTender] = useState(false);
  const { data: me } = useMe();
  const { data: tenders } = useTenders(id);

  if (error) return <ErrorAlert error={error} title="Could not load requisition" />;
  if (isLoading || !r) return <Skeleton variant="rounded" height={320} />;

  const can = r.permissions;
  const pending = r.approvals.find((a) => a.round === r.currentRound && a.status === 'PENDING');

  return (
    <>
      <Breadcrumbs sx={{ mb: 1 }}>
        <Link component={RouterLink} to="/requisitions" underline="hover" color="inherit">
          Requisitions
        </Link>
        <Typography color="text.primary">{r.number}</Typography>
      </Breadcrumbs>
      <PageHeader
        title={r.title}
        subtitle={
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }} useFlexGap>
            <Typography variant="body2" color="text.secondary">
              {r.number}
            </Typography>
            <RequisitionStatusChip status={r.status} />
            {pending && <Chip size="small" label={`With ${roleLabel(pending.approverRole).toLowerCase()}`} />}
          </Stack>
        }
        actions={
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
            {can.canEdit && (
              <Button component={RouterLink} to={`/requisitions/${r.id}/edit`}>
                Edit
              </Button>
            )}
            {can.canCancel && (
              <Button color="error" onClick={() => setAction('cancel')}>
                Withdraw
              </Button>
            )}
            {can.canSubmit && (
              <Button variant="contained" onClick={() => setAction('submit')}>
                Submit for approval
              </Button>
            )}
            {r.status === 'APPROVED' && isProcurement(me) && !(tenders ?? []).some((t) => t.status !== 'CANCELLED') && (
              <Button variant="contained" onClick={() => setCreatingTender(true)}>
                Create tender
              </Button>
            )}
            {can.canDecide && (
              <>
                <Button color="warning" onClick={() => setAction('return')}>
                  Return
                </Button>
                <Button color="error" onClick={() => setAction('reject')}>
                  Reject
                </Button>
                <Button variant="contained" color="success" onClick={() => setAction('approve')}>
                  Approve
                </Button>
              </>
            )}
          </Stack>
        }
      />

      {can.canDecide && (
        <Alert severity="info" sx={{ mb: 3 }}>
          This requisition is waiting for your approval.
        </Alert>
      )}
      {r.status === 'RETURNED' && can.canEdit && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          Returned for changes. Edit it and submit again.
        </Alert>
      )}

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 8 }}>
          <Card sx={{ mb: 3 }}>
            <CardContent>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Field label="Project">
                    <Link component={RouterLink} to={`/projects/${r.project.id}`}>
                      {r.project.code} · {r.project.name}
                    </Link>
                  </Field>
                  <Field label="Requested by">{r.requestedBy.displayName}</Field>
                  <Field label="Department">{r.department}</Field>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Field label="Estimated value">{formatMoney(r.estimatedTotalMinor, r.currency)}</Field>
                  <Field label="Needed by">{r.neededBy && new Date(r.neededBy).toLocaleDateString('en-KE')}</Field>
                  <Field label="Proposed method">{r.proposedMethodCode && roleLabel(r.proposedMethodCode)}</Field>
                </Grid>
              </Grid>
              <Field label="Justification">{r.justification}</Field>
              <PlanField r={r} />
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 1 }}>
                Items
              </Typography>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>#</TableCell>
                      <TableCell>Description</TableCell>
                      <TableCell align="right">Quantity</TableCell>
                      <TableCell align="right">Unit price</TableCell>
                      <TableCell align="right">Total</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {r.lines.map((l) => (
                      <TableRow key={l.id}>
                        <TableCell>{l.lineNo}</TableCell>
                        <TableCell>{l.description}</TableCell>
                        <TableCell align="right">
                          {Number(l.quantity).toLocaleString('en-KE')} {l.unit}
                        </TableCell>
                        <TableCell align="right">{formatMoney(l.unitPriceMinor, r.currency)}</TableCell>
                        <TableCell align="right">{formatMoney(l.totalMinor, r.currency)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                  <TableFooter>
                    <TableRow>
                      <TableCell colSpan={4} align="right" sx={{ fontWeight: 600, color: 'text.primary' }}>
                        Estimated total
                      </TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700, color: 'text.primary' }}>
                        {formatMoney(r.estimatedTotalMinor, r.currency)}
                      </TableCell>
                    </TableRow>
                  </TableFooter>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          {(tenders ?? []).length > 0 && (
            <Card sx={{ mb: 3 }}>
              <CardContent>
                <Typography variant="h6">Tenders</Typography>
                <List dense>
                  {tenders.map((t) => (
                    <ListItem key={t.id} disableGutters secondaryAction={<TenderStatusChip tender={t} />}>
                      <ListItemText
                        primary={
                          <Link component={RouterLink} to={`/tenders/${t.id}`}>
                            {t.number}
                          </Link>
                        }
                        secondary={`${t.bidCount} bid${t.bidCount === 1 ? '' : 's'}`}
                      />
                    </ListItem>
                  ))}
                </List>
              </CardContent>
            </Card>
          )}
          <Card sx={{ mb: 3 }}>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 1 }}>
                Approval chain
              </Typography>
              <ApprovalChain approvals={r.approvals} round={r.currentRound} />
            </CardContent>
          </Card>
          <Card>
            <CardContent>
              <Typography variant="h6">History</Typography>
              <List dense>
                {history?.map((h) => {
                  const d = safeParse(h.payload);
                  return (
                    <ListItem key={h.hash} disableGutters divider>
                      <ListItemText
                        primary={HISTORY_LABELS[h.action] ?? h.action}
                        secondary={
                          <>
                            {new Date(h.occurredAt).toLocaleString('en-KE')} · {h.actor.replace(/\s*<.*>$/, '')}
                            {d.comment && (
                              <Box component="span" sx={{ display: 'block' }}>
                                “{d.comment}”
                              </Box>
                            )}
                          </>
                        }
                      />
                    </ListItem>
                  );
                })}
              </List>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {action && <ActionDialog id={r.id} action={action} onClose={() => setAction(null)} />}
      {creatingTender && <CreateTenderDialog requisition={r} onClose={() => setCreatingTender(false)} />}
    </>
  );
}
