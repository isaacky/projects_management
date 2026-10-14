import { useState } from 'react';
import PropTypes from 'prop-types';
import { Link as RouterLink } from 'react-router';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  AlertTitle,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  LinearProgress,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Skeleton,
  Stack,
  Step,
  StepLabel,
  Stepper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import ExpandMoreOutlined from '@mui/icons-material/ExpandMoreOutlined';
import CheckCircleOutlined from '@mui/icons-material/CheckCircleOutlined';
import CancelOutlined from '@mui/icons-material/CancelOutlined';
import HourglassEmptyOutlined from '@mui/icons-material/HourglassEmptyOutlined';
import ErrorAlert from '../../../components/ErrorAlert.jsx';
import {
  useAppoint,
  useCloseStage,
  useEvaluation,
  useLoadTemplate,
  useSetUpEvaluation,
} from '../../../api/evaluation.js';
import {
  COMMITTEE_ROLE,
  EVALUATION_STATUS,
  EVALUATION_STEPS,
  ROLE_LABEL,
  STAGE_LABEL,
  committeeIssues,
  criteriaOf,
  criterionSummary,
  markedCount,
  stepIndex,
  technicalTotal,
} from '../../../domain/evaluation.js';
import { formatDateTime } from '../../../domain/tenders.js';
import { formatMoney } from '../../../utils/money.js';
import {
  CommitteeChangeDialog,
  CommitteeDialog,
  CriteriaDialog,
  DecisionDialog,
  DeclarationDialog,
  MarksDialog,
  PriceDialog,
  RejectBidDialog,
  ReportDialog,
  SignDialog,
} from './EvaluationDialogs.jsx';

const evalShape = PropTypes.object.isRequired;

function MutationError({ mutation }) {
  return mutation.error ? (
    <Alert severity="error" sx={{ mb: 2 }}>
      {mutation.error.message}
    </Alert>
  ) : null;
}

MutationError.propTypes = { mutation: PropTypes.object.isRequired };

function Section({ title, action, children }) {
  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1, gap: 1 }}>
          <Typography variant="h6">{title}</Typography>
          {action}
        </Stack>
        {children}
      </CardContent>
    </Card>
  );
}

Section.propTypes = { title: PropTypes.string.isRequired, action: PropTypes.node, children: PropTypes.node };

// ------------------------------------------------------------------- not set up yet
function SetUp({ tenderId, data }) {
  const suggested = data.templates.find((t) => t.suggested)?.code ?? data.templates[0]?.code ?? '';
  const [template, setTemplate] = useState(suggested);
  const setUp = useSetUpEvaluation(tenderId);

  if (!data.canSetUp) {
    return (
      <Alert severity="info">
        <AlertTitle>Evaluation not started</AlertTitle>
        {data.setUpBlockedReason ?? 'Procurement staff set up the evaluation once the bids are opened.'}
      </Alert>
    );
  }
  return (
    <Section title="Set up the evaluation">
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Choose the criteria template that matches the tender document. You can adjust the criteria and propose the
        committee before the accounting officer appoints it.
      </Typography>
      <MutationError mutation={setUp} />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField
          select
          label="Criteria template"
          value={template}
          onChange={(e) => setTemplate(e.target.value)}
          sx={{ minWidth: 320 }}
          helperText={data.templates.length ? null : 'Add a template under Procurement settings > Evaluation criteria'}
        >
          {data.templates.map((t) => (
            <MenuItem key={t.code} value={t.code}>
              {t.name}
            </MenuItem>
          ))}
        </TextField>
        <Button variant="contained" onClick={() => setUp.mutate(template)} loading={setUp.isPending} disabled={!template}>
          Set up evaluation
        </Button>
      </Stack>
    </Section>
  );
}

SetUp.propTypes = { tenderId: PropTypes.string.isRequired, data: PropTypes.object.isRequired };

// ------------------------------------------------------------------- criteria
function CriteriaCard({ tenderId, e, onEdit }) {
  const loadTemplate = useLoadTemplate(tenderId);
  const prelim = criteriaOf(e.criteria, 'PRELIMINARY');
  const tech = criteriaOf(e.criteria, 'TECHNICAL');
  return (
    <Section
      title="Criteria"
      action={
        e.permissions.canEditSetup && (
          <Button size="small" onClick={onEdit}>
            Edit
          </Button>
        )
      }
    >
      <MutationError mutation={loadTemplate} />
      <Typography variant="subtitle2" sx={{ mt: 1 }}>
        {STAGE_LABEL.PRELIMINARY}
      </Typography>
      <List dense disablePadding>
        {prelim.map((c) => (
          <ListItem key={c.id} disableGutters>
            <ListItemText primary={`${c.sortOrder}. ${c.name}`} secondary={c.description} />
          </ListItem>
        ))}
      </List>
      {tech.length > 0 && (
        <>
          <Typography variant="subtitle2" sx={{ mt: 1 }}>
            {STAGE_LABEL.TECHNICAL} · out of {technicalTotal(e.criteria)} · pass mark {e.passMarkPct ?? '—'}%
          </Typography>
          <List dense disablePadding>
            {tech.map((c) => (
              <ListItem key={c.id} disableGutters secondaryAction={<Typography variant="body2">{Number(c.maxScore)}</Typography>}>
                <ListItemText primary={c.name} secondary={c.description} />
              </ListItem>
            ))}
          </List>
        </>
      )}
      {e.templateCode && (
        <Typography variant="caption" color="text.secondary">
          From template {e.templateCode}
        </Typography>
      )}
    </Section>
  );
}

CriteriaCard.propTypes = { tenderId: PropTypes.string.isRequired, e: evalShape, onEdit: PropTypes.func.isRequired };

// ------------------------------------------------------------------- committee
function DeclarationChip({ m, required }) {
  if (m.hasConflict) return <Chip size="small" color="error" label="Conflict declared" />;
  if (m.declaredAt) return <Chip size="small" color="success" variant="outlined" label="No conflict" />;
  return required ? <Chip size="small" variant="outlined" label="Declaration pending" /> : null;
}

DeclarationChip.propTypes = { m: PropTypes.object.isRequired, required: PropTypes.bool };

function CommitteeCard({ e, onEdit, onChange }) {
  const appointed = e.status !== 'SETUP';
  const issues = committeeIssues(e.members, e.minMembers, e.maxMembers);
  const scoring = e.status === 'PRELIMINARY' || e.status === 'TECHNICAL';
  return (
    <Section
      title="Committee"
      action={
        <Stack direction="row" spacing={1}>
          {e.permissions.canEditSetup && (
            <Button size="small" onClick={onEdit}>
              Edit
            </Button>
          )}
          {e.permissions.canChangeCommittee && (
            <Button size="small" onClick={onChange}>
              Change
            </Button>
          )}
        </Stack>
      }
    >
      <Typography variant="body2" color="text.secondary">
        {e.minMembers === e.maxMembers ? e.minMembers : `${e.minMembers}–${e.maxMembers}`} voting members · quorum{' '}
        {e.quorum}
        {appointed && e.appointedBy ? ` · appointed by ${e.appointedBy.name} ${formatDateTime(e.appointedAt)}` : ''}
      </Typography>
      {!e.members.length && (
        <Typography variant="body2" sx={{ mt: 1 }}>
          No members proposed yet.
        </Typography>
      )}
      <List dense>
        {e.members.map((m) => (
          <ListItem key={m.userId} disableGutters divider sx={{ alignItems: 'flex-start' }}>
            <ListItemText
              primary={
                <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                  <span>{m.name}</span>
                  <Chip size="small" label={COMMITTEE_ROLE[m.role]} />
                  {appointed && <DeclarationChip m={m} required={e.requiresCoiDeclaration} />}
                  {m.signedThisRound && <Chip size="small" color="success" label="Signed" />}
                </Stack>
              }
              secondary={
                <>
                  {m.hasConflict && m.declaration ? `Conflict: ${m.declaration}. ` : ''}
                  {scoring && m.marksNeeded > 0 ? `Marked ${m.marksDone} of ${m.marksNeeded}. ` : ''}
                  {m.signNote ? `Signature note: ${m.signNote}` : ''}
                </>
              }
            />
          </ListItem>
        ))}
      </List>
      {!appointed && issues.length > 0 && (
        <Alert severity="info" sx={{ mt: 1 }}>
          Before appointment: {issues.join('; ')}.
        </Alert>
      )}
    </Section>
  );
}

CommitteeCard.propTypes = { e: evalShape, onEdit: PropTypes.func.isRequired, onChange: PropTypes.func.isRequired };

// ------------------------------------------------------------------- bids and results
function ResultCell({ passed, children }) {
  if (passed == null) return <TableCell>{children ?? '—'}</TableCell>;
  return (
    <TableCell>
      <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
        {passed ? <CheckCircleOutlined fontSize="small" color="success" /> : <CancelOutlined fontSize="small" color="error" />}
        <span>{children ?? (passed ? 'Passed' : 'Failed')}</span>
      </Stack>
    </TableCell>
  );
}

ResultCell.propTypes = { passed: PropTypes.bool, children: PropTypes.node };

function BidsCard({ e, onMark, onPrice, onReject }) {
  const can = e.permissions;
  const scoring = e.status === 'PRELIMINARY' || e.status === 'TECHNICAL';
  const stageCriteria = scoring ? criteriaOf(e.criteria, e.status) : [];
  const showTechnical = criteriaOf(e.criteria, 'TECHNICAL').length > 0;
  const showFinancial = ['FINANCIAL', 'REPORT', 'SIGNING', 'APPROVAL', 'AWARDED', 'TERMINATED'].includes(e.status);
  return (
    <Section title="Bids and results">
      <TableContainer>
        <Table size="small" sx={{ minWidth: 720 }}>
          <TableHead>
            <TableRow>
              <TableCell>Bid</TableCell>
              <TableCell>Preliminary</TableCell>
              {showTechnical && <TableCell>Technical</TableCell>}
              {showFinancial && <TableCell align="right">Read out</TableCell>}
              {showFinancial && <TableCell align="right">Evaluated price</TableCell>}
              {showFinancial && <TableCell align="center">Rank</TableCell>}
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {e.bids.map((b) => {
              const done = scoring ? markedCount(e.myMarks, b.bidId, stageCriteria) : 0;
              return (
                <TableRow key={b.bidId} sx={{ verticalAlign: 'top', opacity: b.disqualifiedStage ? 0.75 : 1 }}>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {b.supplier.name}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                      {b.receiptNo}
                      {b.agpoCategory ? ` · AGPO ${b.agpoCategory.toLowerCase()}` : ''}
                    </Typography>
                    {b.disqualifiedReason && (
                      <Typography variant="caption" color="error" sx={{ display: 'block', maxWidth: 320 }}>
                        Out at {b.disqualifiedStage.toLowerCase()}: {b.disqualifiedReason}
                      </Typography>
                    )}
                  </TableCell>
                  <ResultCell passed={b.preliminaryPassed} />
                  {showTechnical && (
                    <ResultCell passed={b.technicalPassed}>
                      {b.technicalPct != null ? `${Number(b.technicalPct)}%` : null}
                    </ResultCell>
                  )}
                  {showFinancial && (
                    <TableCell align="right">
                      {b.readOutPriceMinor != null ? formatMoney(b.readOutPriceMinor, e.currency) : '—'}
                    </TableCell>
                  )}
                  {showFinancial && (
                    <TableCell align="right">
                      {b.evaluatedPriceMinor != null ? formatMoney(b.evaluatedPriceMinor, e.currency) : '—'}
                      {b.priceNote && (
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                          {b.priceNote}
                        </Typography>
                      )}
                    </TableCell>
                  )}
                  {showFinancial && (
                    <TableCell align="center">
                      {b.rank != null ? <Chip size="small" color={b.rank === 1 ? 'success' : 'default'} label={b.rank} /> : '—'}
                    </TableCell>
                  )}
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {can.canScore && b.inCurrentStage && (
                      <Button size="small" variant={done === stageCriteria.length ? 'text' : 'outlined'} onClick={() => onMark(b)}>
                        {done === stageCriteria.length ? 'Review marks' : `Mark (${done}/${stageCriteria.length})`}
                      </Button>
                    )}
                    {can.canEditFinancial && b.inCurrentStage && (
                      <>
                        <Button size="small" onClick={() => onPrice(b)}>
                          Price
                        </Button>
                        <Button size="small" color="error" onClick={() => onReject(b)}>
                          Reject
                        </Button>
                      </>
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
      {!e.bids.length && (
        <Typography variant="body2" color="text.secondary">
          No bids were received.
        </Typography>
      )}
    </Section>
  );
}

BidsCard.propTypes = {
  e: evalShape,
  onMark: PropTypes.func.isRequired,
  onPrice: PropTypes.func.isRequired,
  onReject: PropTypes.func.isRequired,
};

/** Per-criterion results of the closed stages, from every member's marks. */
function ScoreSheet({ e }) {
  const stages = [];
  if (e.preliminaryClosedAt) stages.push('PRELIMINARY');
  if (e.technicalClosedAt && criteriaOf(e.criteria, 'TECHNICAL').length) stages.push('TECHNICAL');
  if (!stages.length) return null;
  return (
    <Box sx={{ mb: 3 }}>
      {stages.map((stage) => {
        const crit = criteriaOf(e.criteria, stage);
        const bids = e.bids.filter((b) => stage === 'PRELIMINARY' || b.preliminaryPassed);
        return (
          <Accordion key={stage} disableGutters>
            <AccordionSummary expandIcon={<ExpandMoreOutlined />}>
              <Typography>Score sheet · {STAGE_LABEL[stage]}</Typography>
            </AccordionSummary>
            <AccordionDetails>
              <TableContainer>
                <Table size="small" sx={{ minWidth: 560 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Criterion</TableCell>
                      {bids.map((b) => (
                        <TableCell key={b.bidId} align="center">
                          {b.supplier.name}
                        </TableCell>
                      ))}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {crit.map((c) => (
                      <TableRow key={c.id}>
                        <TableCell>
                          {c.name}
                          {stage === 'TECHNICAL' ? ` (/${Number(c.maxScore)})` : ''}
                        </TableCell>
                        {bids.map((b) => {
                          const s = criterionSummary(e.closedStageMarks, c, b.bidId);
                          return (
                            <TableCell key={b.bidId} align="center">
                              {stage === 'PRELIMINARY'
                                ? `${s.met} met · ${s.notMet} not`
                                : s.average == null
                                  ? '—'
                                  : s.average}
                            </TableCell>
                          );
                        })}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <Typography variant="caption" color="text.secondary">
                {stage === 'PRELIMINARY'
                  ? 'A criterion is met when most members marked it met; on a tie the chair decides.'
                  : 'Average of the members who completed their marking.'}
              </Typography>
            </AccordionDetails>
          </Accordion>
        );
      })}
    </Box>
  );
}

ScoreSheet.propTypes = { e: evalShape };

// ------------------------------------------------------------------- report and approval
function ReportCard({ e, onSign, onDecide }) {
  const rec = e.recommendation;
  if (!rec) return null;
  const winner = e.bids.find((b) => b.bidId === rec.bidId);
  const rounds = [...new Set(e.approvals.map((a) => a.round))].sort((a, b) => b - a);
  return (
    <Section
      title={`Recommendation${rec.round > 1 ? ` (round ${rec.round})` : ''}`}
      action={
        <Stack direction="row" spacing={1}>
          {e.permissions.canSign && (
            <Button variant="contained" onClick={onSign}>
              Sign report
            </Button>
          )}
          {e.permissions.canDecide && (
            <Button variant="contained" color="warning" onClick={onDecide}>
              {e.permissions.decisionIsOpinion ? 'Give professional opinion' : 'Decide'}
            </Button>
          )}
        </Stack>
      }
    >
      <Typography variant="subtitle1">
        {rec.noAward
          ? 'No award'
          : `Award to ${winner?.supplier.name ?? '—'} at ${formatMoney(rec.amountMinor, e.currency)}`}
      </Typography>
      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', my: 1 }}>
        {rec.text}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        Submitted by {rec.submittedBy?.name} {formatDateTime(rec.submittedAt)}
      </Typography>

      <Typography variant="subtitle2" sx={{ mt: 2 }}>
        Signatures
      </Typography>
      <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', mt: 0.5 }}>
        {e.signatories.map((s) => {
          const m = e.members.find((x) => x.userId === s.id);
          return (
            <Chip
              key={s.id}
              size="small"
              icon={m?.signedThisRound ? <CheckCircleOutlined /> : <HourglassEmptyOutlined />}
              color={m?.signedThisRound ? 'success' : 'default'}
              variant={m?.signedThisRound ? 'filled' : 'outlined'}
              label={s.name}
            />
          );
        })}
      </Stack>

      {e.approvals.length > 0 && (
        <>
          <Typography variant="subtitle2" sx={{ mt: 2 }}>
            Opinion and approval
          </Typography>
          {rounds.map((round) => (
            <List dense key={round}>
              {e.approvals
                .filter((a) => a.round === round)
                .map((a) => (
                  <ListItem key={a.id} disableGutters divider>
                    <ListItemText
                      primary={
                        <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                          <span>
                            {a.stepOrder}. {ROLE_LABEL[a.approverRole] ?? a.approverRole}
                            {a.opinion ? ' · professional opinion' : ''}
                          </span>
                          <Chip
                            size="small"
                            label={a.status.toLowerCase()}
                            color={
                              a.status === 'APPROVED'
                                ? 'success'
                                : a.status === 'PENDING'
                                  ? 'warning'
                                  : a.status === 'SKIPPED'
                                    ? 'default'
                                    : 'error'
                            }
                          />
                          {rounds.length > 1 && <Typography variant="caption">round {round}</Typography>}
                        </Stack>
                      }
                      secondary={
                        a.decidedBy ? `${a.decidedBy.name}, ${formatDateTime(a.decidedAt)}${a.note ? `: ${a.note}` : ''}` : null
                      }
                    />
                  </ListItem>
                ))}
            </List>
          ))}
        </>
      )}
    </Section>
  );
}

ReportCard.propTypes = { e: evalShape, onSign: PropTypes.func.isRequired, onDecide: PropTypes.func.isRequired };

// ------------------------------------------------------------------- panel
export default function EvaluationPanel({ tender }) {
  const { data, isLoading, error } = useEvaluation(tender.id);
  const appoint = useAppoint(tender.id);
  const closeStage = useCloseStage(tender.id);
  const [dialog, setDialog] = useState(null);
  const close = () => setDialog(null);

  if (error) return <ErrorAlert error={error} title="Could not load the evaluation" />;
  if (isLoading || !data) return <Skeleton variant="rounded" height={240} />;
  if (!data.exists) return <SetUp tenderId={tender.id} data={data} />;

  const e = data.evaluation;
  const can = e.permissions;
  const status = EVALUATION_STATUS[e.status];
  const p = e.progress;

  return (
    <>
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
            <Typography variant="h6">Evaluation</Typography>
            <Chip size="small" color={status.color} label={status.label} />
          </Stack>
          <Stepper activeStep={stepIndex(e.status)} alternativeLabel sx={{ overflowX: 'auto', pb: 1 }}>
            {EVALUATION_STEPS.map((s, i) => (
              <Step key={s.status} completed={i < stepIndex(e.status) || e.status === 'AWARDED'}>
                <StepLabel error={e.status === 'TERMINATED' && i === EVALUATION_STEPS.length - 1}>
                  {e.status === 'TERMINATED' && i === EVALUATION_STEPS.length - 1 ? 'Terminated' : s.label}
                </StepLabel>
              </Step>
            ))}
          </Stepper>
        </CardContent>
      </Card>

      {e.warnings.map((w) => (
        <Alert key={w} severity="warning" sx={{ mb: 2 }}>
          {w}
        </Alert>
      ))}
      <MutationError mutation={appoint} />
      <MutationError mutation={closeStage} />

      {can.canDeclare && (
        <Alert
          severity="warning"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={() => setDialog({ type: 'declare' })}>
              Declare
            </Button>
          }
        >
          {e.requiresCoiDeclaration
            ? 'Make your conflict of interest declaration before you can see bid documents or mark bids.'
            : 'You can record a conflict of interest declaration.'}
        </Alert>
      )}
      {e.me.role && !e.me.cleared && !can.canDeclare && e.status !== 'SETUP' && (
        <Alert severity="info" sx={{ mb: 2 }}>
          You declared a conflict of interest and take no further part in this evaluation.
        </Alert>
      )}

      {e.status === 'SETUP' && (
        <Alert
          severity="info"
          sx={{ mb: 2 }}
          action={
            can.canAppoint && (
              <Button color="inherit" size="small" onClick={() => appoint.mutate()} loading={appoint.isPending}>
                Appoint committee
              </Button>
            )
          }
        >
          {can.canAppoint
            ? 'Review the criteria and the proposed committee, then appoint the committee to start the evaluation.'
            : 'Procurement staff propose the criteria and committee; the accounting officer appoints the committee.'}
        </Alert>
      )}

      {p && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
              <Box sx={{ flex: 1 }}>
                <Typography variant="subtitle1">{STAGE_LABEL[p.stage]} stage</Typography>
                {p.stage !== 'FINANCIAL' ? (
                  <>
                    <Typography variant="body2" color="text.secondary">
                      {p.completeMembers} of {p.scoringMembers} members have finished marking {p.bids} bid
                      {p.bids === 1 ? '' : 's'} on {p.criteria} criteria · quorum {p.quorum}
                    </Typography>
                    <LinearProgress
                      variant="determinate"
                      value={Math.min(100, (p.completeMembers / Math.max(p.quorum, 1)) * 100)}
                      sx={{ mt: 1, maxWidth: 360 }}
                    />
                  </>
                ) : null}
                {p.waitingFor && (
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Waiting for: {p.waitingFor}
                  </Typography>
                )}
                {p.stage === 'FINANCIAL' && p.waitingFor?.includes('opened') && (
                  <Typography variant="body2" sx={{ mt: 0.5 }}>
                    Procurement opens the financial envelopes on the{' '}
                    <RouterLink to={`/tenders/${tender.id}`}>Tender tab</RouterLink>.
                  </Typography>
                )}
              </Box>
              {can.canCloseStage && (
                <Button variant="contained" disabled={!p.readyToClose} onClick={() => setDialog({ type: 'close' })}>
                  Close {p.stage.toLowerCase()} stage
                </Button>
              )}
            </Stack>
          </CardContent>
        </Card>
      )}

      {e.status === 'REPORT' && (
        <Alert
          severity="info"
          sx={{ mb: 2 }}
          action={
            can.canSubmitReport && (
              <Button color="inherit" size="small" onClick={() => setDialog({ type: 'report' })}>
                Write recommendation
              </Button>
            )
          }
        >
          {e.round > 1
            ? 'The report was returned. The chair revises the recommendation; everyone signs again.'
            : 'The evaluation is complete. The chair writes the committee’s recommendation.'}
        </Alert>
      )}

      {e.status === 'AWARDED' && e.award && (
        <Alert
          severity="success"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" component={RouterLink} to={`/tenders/${tender.id}/letters`}>
              Notification letters
            </Button>
          }
        >
          <AlertTitle>
            Awarded to {e.award.supplier?.name} at {formatMoney(e.award.amountMinor, e.currency)}
          </AlertTitle>
          Approved {formatDateTime(e.award.awardedAt)}. Notify all bidders at the same time. The contract may be signed
          after the standstill period ends on {formatDateTime(e.award.standstillEndsAt)}.
        </Alert>
      )}
      {e.status === 'TERMINATED' && (
        <Alert
          severity="warning"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" component={RouterLink} to={`/tenders/${tender.id}/letters`}>
              Notification letters
            </Button>
          }
        >
          The tender was terminated without award: {tender.cancelledReason}
        </Alert>
      )}

      <ReportCard e={e} onSign={() => setDialog({ type: 'sign' })} onDecide={() => setDialog({ type: 'decide' })} />
      {e.status !== 'SETUP' && (
        <BidsCard
          e={e}
          onMark={(b) => setDialog({ type: 'marks', bid: b })}
          onPrice={(b) => setDialog({ type: 'price', bid: b })}
          onReject={(b) => setDialog({ type: 'reject', bid: b })}
        />
      )}
      <ScoreSheet e={e} />
      <CommitteeCard e={e} onEdit={() => setDialog({ type: 'committee' })} onChange={() => setDialog({ type: 'change' })} />
      <CriteriaCard tenderId={tender.id} e={e} onEdit={() => setDialog({ type: 'criteria' })} />

      {dialog?.type === 'criteria' && <CriteriaDialog tenderId={tender.id} evaluation={e} onClose={close} />}
      {dialog?.type === 'committee' && <CommitteeDialog tenderId={tender.id} evaluation={e} onClose={close} />}
      {dialog?.type === 'change' && <CommitteeChangeDialog tenderId={tender.id} evaluation={e} onClose={close} />}
      {dialog?.type === 'declare' && <DeclarationDialog tenderId={tender.id} evaluation={e} onClose={close} />}
      {dialog?.type === 'marks' && <MarksDialog tenderId={tender.id} evaluation={e} bid={dialog.bid} onClose={close} />}
      {dialog?.type === 'price' && <PriceDialog tenderId={tender.id} evaluation={e} bid={dialog.bid} onClose={close} />}
      {dialog?.type === 'reject' && <RejectBidDialog tenderId={tender.id} bid={dialog.bid} onClose={close} />}
      {dialog?.type === 'report' && <ReportDialog tenderId={tender.id} evaluation={e} onClose={close} />}
      {dialog?.type === 'sign' && <SignDialog tenderId={tender.id} onClose={close} />}
      {dialog?.type === 'decide' && <DecisionDialog tenderId={tender.id} evaluation={e} onClose={close} />}
      {dialog?.type === 'close' && (
        <Dialog open onClose={close} fullWidth maxWidth="xs">
          <DialogTitle>Close the {p?.stage.toLowerCase()} stage?</DialogTitle>
          <DialogContent>
            <DialogContentText>
              {p?.stage === 'FINANCIAL'
                ? 'The bids are ranked by evaluated price and the committee moves on to its recommendation.'
                : `The results are worked out from the marks of the ${p?.completeMembers} member(s) who finished. Bids that fail are out of the evaluation. Members' marks become visible to the committee. This cannot be undone.`}
            </DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={close}>Cancel</Button>
            <Button
              variant="contained"
              loading={closeStage.isPending}
              onClick={() => closeStage.mutate(undefined, { onSettled: close })}
            >
              Close stage
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </>
  );
}

EvaluationPanel.propTypes = { tender: PropTypes.object.isRequired };
