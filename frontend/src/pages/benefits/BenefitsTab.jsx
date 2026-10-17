import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Collapse,
  Grid,
  IconButton,
  LinearProgress,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import { useBenefitAction, useProjectBenefits } from '../../api/benefits.js';
import {
  CATEGORY,
  FREQUENCY,
  OBJECTIVES_MET,
  RATING,
  REVIEW_STATUS,
  STATUS,
  barValue,
  formatPct,
  formatValue,
  sparkPoints,
} from '../../domain/benefits.js';
import { formatDate } from '../../domain/contracts.js';
import { OUTCOME, lessonDraft, shortfalls } from '../../domain/objectives.js';
import { LessonDialog } from '../closeout/LessonsSection.jsx';
import { formatMoney } from '../../utils/money.js';
import { NoteDialog } from '../procurement-plan/PlanDialogs.jsx';
import {
  BenefitDialog,
  CloseBenefitDialog,
  FindingsDialog,
  MeasurementDialog,
  ReviewDialog,
  StandardReviewsDialog,
} from './BenefitDialogs.jsx';

export function RatingChip({ rating, size = 'small' }) {
  if (!rating) return <Chip size={size} label="Not measured yet" variant="outlined" />;
  const r = RATING[rating];
  return <Chip size={size} label={r.label} color={r.color} />;
}

RatingChip.propTypes = { rating: PropTypes.string, size: PropTypes.string };

/** Measurements as a small line against the baseline and the target. */
export function Sparkline({ benefit, width = 160, height = 44 }) {
  const { line, target, baselineY } = sparkPoints(benefit, width, height);
  const d = line.map((p, i) => `${i ? 'L' : 'M'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');
  return (
    <svg width={width} height={height + 8} viewBox={`0 -4 ${width} ${height + 8}`} role="img" aria-label={`Trend of ${benefit.measure}`}>
      <line x1="0" x2={width} y1={baselineY} y2={baselineY} stroke="currentColor" strokeOpacity="0.2" strokeDasharray="2 3" />
      <line x1="0" x2={width} y1={target.y} y2={target.y} stroke="#2e7d32" strokeOpacity="0.5" strokeDasharray="4 3" />
      <line x1="0" y1={baselineY} x2={target.x} y2={target.y} stroke="currentColor" strokeOpacity="0.25" />
      <path d={d} fill="none" stroke="#1976d2" strokeWidth="2" />
      {line.slice(1).map((p, i) => (
        <circle key={i} cx={p.x} cy={p.y} r="2.5" fill="#1976d2" />
      ))}
    </svg>
  );
}

Sparkline.propTypes = { benefit: PropTypes.object.isRequired, width: PropTypes.number, height: PropTypes.number };

function BenefitCard({ benefit: b, onAction, onEdit, onMeasure, onClose }) {
  const [open, setOpen] = useState(false);
  const s = STATUS[b.status];
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ justifyContent: 'space-between' }}>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', mb: 0.5 }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                B{b.number} · {b.title}
              </Typography>
              {b.status === 'ACTIVE' ? <RatingChip rating={b.rating} /> : <Chip size="small" label={s.label} color={s.color} />}
              {b.overdue && <Chip size="small" color="error" variant="outlined" label={`Measurement due ${formatDate(b.nextDue)}`} />}
            </Stack>
            <Typography variant="body2" color="text.secondary">
              {CATEGORY[b.category]} · {b.measure} · owner {b.owner?.name} · {FREQUENCY[b.frequency].toLowerCase()}
              {b.objective ? ` · ${b.objective}` : ''}
            </Typography>
            <Box sx={{ mt: 1.5, maxWidth: 520 }}>
              <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                <Typography variant="caption">
                  {formatValue(b.baselineValue, b.unit)} ({formatDate(b.baselineDate)})
                </Typography>
                <Typography variant="caption">
                  target {formatValue(b.targetValue, b.unit)} by {formatDate(b.targetDate)}
                </Typography>
              </Stack>
              <Box sx={{ position: 'relative', my: 0.5 }}>
                <LinearProgress
                  variant="determinate"
                  value={barValue(b.progressPct)}
                  color={RATING[b.rating]?.color === 'error' ? 'error' : RATING[b.rating]?.color === 'warning' ? 'warning' : 'success'}
                  sx={{ height: 10, borderRadius: 5 }}
                  aria-label={`B${b.number} progress`}
                />
                {b.status === 'ACTIVE' && (
                  <Tooltip title={`Where it should be by now: ${formatPct(b.expectedPct)}`}>
                    <Box sx={{ position: 'absolute', top: -3, bottom: -3, width: 2, bgcolor: 'text.primary', left: `${barValue(b.expectedPct)}%` }} />
                  </Tooltip>
                )}
              </Box>
              <Typography variant="body2">
                {b.latestValue === null
                  ? 'No measurement yet'
                  : `Latest ${formatValue(b.latestValue, b.unit)} on ${formatDate(b.latestOn)} · ${formatPct(b.progressPct)} of the way`}
                {b.status === 'ACTIVE' && b.nextDue && !b.overdue ? ` · next due ${formatDate(b.nextDue)}` : ''}
              </Typography>
              {b.annualValueMinor !== null && (
                <Typography variant="caption" color="text.secondary">
                  Worth {formatMoney(b.annualValueMinor, 'KES', { compact: true })} a year at target ·{' '}
                  {formatMoney(b.realisedMinor, 'KES', { compact: true })} realised so far
                </Typography>
              )}
              {b.closedNote && (
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                  Closed by {b.closedBy?.name}: {b.closedNote}
                </Typography>
              )}
            </Box>
          </Box>
          <Stack spacing={1} sx={{ alignItems: { md: 'flex-end' } }}>
            <Sparkline benefit={b} />
            <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', justifyContent: 'flex-end' }}>
              {b.canMeasure && (
                <Button size="small" variant="contained" onClick={() => onMeasure(b)}>
                  Record
                </Button>
              )}
              {b.canEdit && (
                <Button size="small" onClick={() => onEdit(b)}>
                  Edit
                </Button>
              )}
              {b.canClose && (
                <Button size="small" onClick={() => onClose(b)}>
                  Close
                </Button>
              )}
              {b.canReopen && (
                <Button size="small" onClick={() => onAction({ path: `/benefits/${b.id}/reopen` })}>
                  Reopen
                </Button>
              )}
              <Button size="small" onClick={() => setOpen((o) => !o)} disabled={b.measurements.length === 0}>
                {open ? 'Hide' : `Readings (${b.measurements.length})`}
              </Button>
            </Stack>
          </Stack>
        </Stack>
        <Collapse in={open} unmountOnExit>
          <Table size="small" sx={{ mt: 2 }}>
            <TableHead>
              <TableRow>
                <TableCell>Date</TableCell>
                <TableCell align="right">Value</TableCell>
                <TableCell align="right">Progress</TableCell>
                <TableCell>Source / note</TableCell>
                <TableCell>By</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {[...b.measurements].reverse().map((m) => (
                <TableRow key={m.id}>
                  <TableCell>{formatDate(m.measuredOn)}</TableCell>
                  <TableCell align="right">{formatValue(m.value, b.unit)}</TableCell>
                  <TableCell align="right">{formatPct(m.progressPct)}</TableCell>
                  <TableCell>{[m.source, m.note].filter(Boolean).join(' · ') || '—'}</TableCell>
                  <TableCell>{m.recordedBy?.name}</TableCell>
                  <TableCell align="right">
                    {m.canDelete && (
                      <Tooltip title="Delete this reading">
                        <IconButton
                          size="small"
                          aria-label={`Delete reading of ${m.measuredOn}`}
                          onClick={() => onAction({ path: `/benefits/${b.id}/measurements/${m.id}`, method: 'DELETE' })}
                        >
                          <DeleteOutline fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Collapse>
      </CardContent>
    </Card>
  );
}

BenefitCard.propTypes = {
  benefit: PropTypes.object.isRequired,
  onAction: PropTypes.func.isRequired,
  onEdit: PropTypes.func.isRequired,
  onMeasure: PropTypes.func.isRequired,
  onClose: PropTypes.func.isRequired,
};

export function OutcomeChip({ outcome, size = 'small' }) {
  const o = OUTCOME[outcome];
  return o ? <Chip size={size} label={o.label} color={o.color} variant={outcome === 'TOO_EARLY' ? 'outlined' : 'filled'} /> : null;
}

OutcomeChip.propTypes = { outcome: PropTypes.string, size: PropTypes.string };

function ReviewCard({ review: r, onWrite, onEdit, onCancel }) {
  const st = REVIEW_STATUS[r.status];
  const [lesson, setLesson] = useState(null);
  const gaps = new Set(shortfalls(r.objectiveAssessments).map((a) => a.objectiveId));
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: 'space-between' }}>
          <Box>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                {r.title}
              </Typography>
              <Chip size="small" label={st.label} color={st.color} />
              {r.overdue && <Chip size="small" color="error" variant="outlined" label="Overdue" />}
            </Stack>
            <Typography variant="body2" color="text.secondary">
              {r.status === 'COMPLETED'
                ? `Held ${formatDate(r.conductedOn)} · led by ${r.lead?.name} · objectives met: ${OBJECTIVES_MET[r.objectivesMet]?.toLowerCase()}`
                : `Due ${formatDate(r.dueDate)} · led by ${r.lead?.name}`}
            </Typography>
          </Box>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
            {r.canComplete && (
              <Button size="small" variant="contained" onClick={() => onWrite(r)}>
                {r.summary || r.assessments.length || r.objectiveAssessments?.length ? 'Continue write-up' : 'Write up'}
              </Button>
            )}
            {r.canEdit && (
              <Button size="small" onClick={() => onEdit(r)}>
                Edit
              </Button>
            )}
            {r.canCancel && (
              <Button size="small" onClick={() => onCancel(r)}>
                Cancel
              </Button>
            )}
          </Stack>
        </Stack>
        {r.status === 'COMPLETED' && (
          <Box sx={{ mt: 1.5 }}>
            <Typography variant="body2">{r.summary}</Typography>
            {r.recommendations && (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Recommendations: {r.recommendations}
              </Typography>
            )}
            {r.objectiveAssessments?.length > 0 && (
              <Table size="small" sx={{ mt: 1 }} aria-label="Objectives">
                <TableBody>
                  {r.objectiveAssessments.map((a) => (
                    <TableRow key={a.objectiveId}>
                      <TableCell sx={{ width: '35%' }}>
                        O{a.objectiveNumber} · {a.statement}
                      </TableCell>
                      <TableCell>
                        <OutcomeChip outcome={a.outcome} />
                      </TableCell>
                      <TableCell>{a.evidence}</TableCell>
                      <TableCell align="right">
                        {gaps.has(a.objectiveId) && (
                          <Button size="small" onClick={() => setLesson(lessonDraft(a, r.title))}>
                            Record a lesson
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
            <Table size="small" sx={{ mt: 1 }} aria-label="Benefits">
              <TableBody>
                {r.assessments.map((a) => (
                  <TableRow key={a.benefitId}>
                    <TableCell sx={{ width: '35%' }}>
                      B{a.benefitNumber} · {a.benefitTitle}
                    </TableCell>
                    <TableCell>
                      {a.actualValue === null
                        ? 'Not measured'
                        : `${formatValue(a.actualValue, a.unit)} of ${formatValue(a.targetValue, a.unit)} (${formatPct(a.progressPct)})`}
                    </TableCell>
                    <TableCell>
                      <RatingChip rating={a.rating} />
                    </TableCell>
                    <TableCell>{a.comment}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        )}
        {lesson && <LessonDialog projectId={r.project.id} draft={lesson} onClose={() => setLesson(null)} />}
        {r.status === 'CANCELLED' && r.cancelNote && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            Cancelled: {r.cancelNote}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

ReviewCard.propTypes = {
  review: PropTypes.object.isRequired,
  onWrite: PropTypes.func.isRequired,
  onEdit: PropTypes.func.isRequired,
  onCancel: PropTypes.func.isRequired,
};

function Stat({ label, value, color }) {
  return (
    <Box>
      <Typography variant="overline" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="h6" sx={{ color }}>
        {value}
      </Typography>
    </Box>
  );
}

Stat.propTypes = { label: PropTypes.string.isRequired, value: PropTypes.node, color: PropTypes.string };

export default function BenefitsTab({ projectId }) {
  const { data: v, isLoading, error } = useProjectBenefits(projectId);
  const action = useBenefitAction(projectId);
  const [dialog, setOpenDialog] = useState(null); // { kind, item }
  // Opening a dialog clears any error left by an earlier inline action.
  const setDialog = (d) => {
    if (d) action.reset();
    setOpenDialog(d);
  };

  if (error) return <ErrorAlert error={error} title="Could not load benefits" />;
  if (isLoading || !v) return <Skeleton variant="rounded" height={300} />;

  const close = () => {
    action.reset();
    setDialog(null);
  };
  const run = (vars) => action.mutate(vars, { onSuccess: () => setDialog(null) });
  const common = { pending: action.isPending, error: action.error, onClose: close };
  const s = v.summary;
  const STANDARD = ['6-month post-implementation review', '12-month post-implementation review'];
  const hasStandard = STANDARD.every((t) => v.reviews.some((r) => r.status !== 'CANCELLED' && r.title === t));

  return (
    <>
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Grid container spacing={2}>
            <Grid size={{ xs: 6, md: 2 }}>
              <Stat label="Benefits" value={s.benefits} />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <Stat label="On track" value={s.onTrack + s.achieved} color="success.main" />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <Stat label="At risk / off track" value={`${s.atRisk} / ${s.offTrack}`} color={s.atRisk + s.offTrack ? 'warning.main' : undefined} />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <Stat label="Measurements due" value={s.overdueMeasurements} color={s.overdueMeasurements ? 'error.main' : undefined} />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <Stat
                label="Value realised / at target (a year)"
                value={`${formatMoney(s.realisedMinor, 'KES', { compact: true })} / ${formatMoney(s.annualValueMinor, 'KES', { compact: true })}`}
              />
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {action.error && !dialog && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {action.error.message}
        </Alert>
      )}

      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
        <Typography variant="h6">Benefits register</Typography>
        {v.permissions.canAdd && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialog({ kind: 'benefit' })}>
            Add benefit
          </Button>
        )}
      </Stack>
      <Stack spacing={2} sx={{ mb: 4 }}>
        {v.benefits.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            No benefits yet. Add what the project should achieve, how it is measured, the baseline and the target.
          </Typography>
        )}
        {v.benefits.map((b) => (
          <BenefitCard
            key={b.id}
            benefit={b}
            onAction={(vars) => action.mutate(vars)}
            onEdit={(item) => setDialog({ kind: 'benefit', item })}
            onMeasure={(item) => setDialog({ kind: 'measure', item })}
            onClose={(item) => setDialog({ kind: 'close', item })}
          />
        ))}
      </Stack>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, mb: 1.5 }}>
        <Box>
          <Typography variant="h6">Post-implementation reviews</Typography>
          <Typography variant="body2" color="text.secondary">
            {v.handoverDate ? `Handed over ${formatDate(v.handoverDate)}.` : 'Reviews are usually held 6 and 12 months after the handover.'}
          </Typography>
        </Box>
        {v.permissions.canPlanReviews && (
          <Stack direction="row" spacing={1}>
            {v.handoverDate && !hasStandard && (
              <Button onClick={() => setDialog({ kind: 'standard' })}>Plan 6- and 12-month reviews</Button>
            )}
            <Button variant="outlined" startIcon={<AddIcon />} onClick={() => setDialog({ kind: 'review' })}>
              Plan review
            </Button>
          </Stack>
        )}
      </Stack>
      <Stack spacing={2}>
        {v.reviews.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            No reviews planned.
          </Typography>
        )}
        {v.reviews.map((r) => (
          <ReviewCard
            key={r.id}
            review={r}
            onWrite={(item) => setDialog({ kind: 'findings', item })}
            onEdit={(item) => setDialog({ kind: 'review', item })}
            onCancel={(item) => setDialog({ kind: 'cancelReview', item })}
          />
        ))}
      </Stack>

      {dialog?.kind === 'benefit' && (
        <BenefitDialog
          benefit={dialog.item}
          {...common}
          onSave={(body) =>
            run(dialog.item ? { path: `/benefits/${dialog.item.id}`, method: 'PUT', body } : { path: '/benefits', body })
          }
        />
      )}
      {dialog?.kind === 'measure' && (
        <MeasurementDialog benefit={dialog.item} {...common} onSave={(body) => run({ path: `/benefits/${dialog.item.id}/measurements`, body })} />
      )}
      {dialog?.kind === 'close' && (
        <CloseBenefitDialog benefit={dialog.item} {...common} onSave={(body) => run({ path: `/benefits/${dialog.item.id}/close`, body })} />
      )}
      {dialog?.kind === 'review' && (
        <ReviewDialog
          review={dialog.item}
          {...common}
          onSave={(body) =>
            run(
              dialog.item
                ? { path: `/benefit-reviews/${dialog.item.id}`, method: 'PUT', body }
                : { path: '/benefit-reviews', body },
            )
          }
        />
      )}
      {dialog?.kind === 'standard' && (
        <StandardReviewsDialog handoverDate={v.handoverDate} {...common} onSave={(body) => run({ path: '/benefit-reviews/standard', body })} />
      )}
      {dialog?.kind === 'findings' && (
        <FindingsDialog
          review={dialog.item}
          benefits={v.benefits}
          objectives={v.objectives ?? []}
          {...common}
          onSave={(body, complete) =>
            run(
              complete
                ? { path: `/benefit-reviews/${dialog.item.id}/complete`, body }
                : { path: `/benefit-reviews/${dialog.item.id}/findings`, method: 'PUT', body },
            )
          }
        />
      )}
      {dialog?.kind === 'cancelReview' && (
        <NoteDialog
          open
          title={`Cancel ${dialog.item.title}`}
          label="Reason"
          required
          confirm="Cancel review"
          {...common}
          onConfirm={(note) => run({ path: `/benefit-reviews/${dialog.item.id}/cancel`, body: { note } })}
        />
      )}
    </>
  );
}

BenefitsTab.propTypes = { projectId: PropTypes.string.isRequired };
