import { useState } from 'react';
import PropTypes from 'prop-types';
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
  MenuItem,
  Skeleton,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import RagChip from './RagChip.jsx';
import { useCreateReport, useEditReport, useReportAction, useStatusReports } from '../../api/execution.js';
import { RAG, pct } from '../../domain/planning.js';
import { formatDate, todayInput } from '../../domain/contracts.js';
import { formatDateTime } from '../../domain/tenders.js';
import { formatMoney } from '../../utils/money.js';

const ASPECTS = [
  ['overall', 'Overall'],
  ['schedule', 'Schedule'],
  ['cost', 'Cost'],
  ['scope', 'Scope'],
  ['risk', 'Risk'],
];

function Figures({ f, currency }) {
  if (!f) return null;
  const row = (label, value) => (
    <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2">{value}</Typography>
    </Stack>
  );
  return (
    <Box>
      <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
        {f.frozen ? 'Figures at submission' : 'Current figures (frozen on submission)'}
      </Typography>
      {row('Progress (earned / planned)', `${pct(f.earnedPct)} / ${pct(f.plannedPct)}`)}
      {row('SPI / CPI', `${f.spi ?? '—'} / ${f.cpi ?? '—'}`)}
      {row('Finish: baseline → forecast', `${formatDate(f.baselineFinish)} → ${formatDate(f.forecastFinish)}`)}
      {row('Budget', formatMoney(f.budgetMinor, currency))}
      {row('Committed (contracts)', formatMoney(f.committedMinor, currency))}
      {row('Certified', formatMoney(f.certifiedMinor, currency))}
      {row('Open risks (high or critical)', `${f.openRisks} (${f.highRisks})`)}
      {row('Open issues', f.openIssues)}
    </Box>
  );
}

Figures.propTypes = { f: PropTypes.object, currency: PropTypes.string.isRequired };

function EditReportDialog({ projectId, report: r, onClose }) {
  const [f, setF] = useState({
    periodEnd: r.periodEnd,
    overall: r.overall,
    schedule: r.schedule,
    cost: r.cost,
    scope: r.scope,
    risk: r.risk,
    summary: r.summary === 'To be written' ? '' : r.summary,
    achievements: r.achievements ?? '',
    nextSteps: r.nextSteps ?? '',
    escalations: r.escalations ?? '',
  });
  const edit = useEditReport(projectId);
  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }));
  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="md"
      component="form"
      onSubmit={(e) => {
        e.preventDefault();
        edit.mutate({ id: r.id, ...f }, { onSuccess: onClose });
      }}
    >
      <DialogTitle>Status report, period ending {formatDate(r.periodEnd)}</DialogTitle>
      <DialogContent>
        {edit.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {edit.error.message}
          </Alert>
        )}
        {r.suggested && (
          <Alert severity="info" sx={{ mb: 2 }}>
            Suggested from the figures: schedule {RAG[r.suggested.schedule].label.toLowerCase()}, cost{' '}
            {RAG[r.suggested.cost].label.toLowerCase()}, risk {RAG[r.suggested.risk].label.toLowerCase()}. Use your
            judgement; explain any difference in the summary.
          </Alert>
        )}
        <Grid container spacing={2}>
          {ASPECTS.map(([k, label]) => (
            <Grid key={k} size={{ xs: 6, sm: 2.4 }}>
              <TextField select label={label} value={f[k]} onChange={set(k)} fullWidth>
                {Object.entries(RAG).map(([code, v]) => (
                  <MenuItem key={code} value={code}>
                    {v.label}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
          ))}
          <Grid size={12}>
            <TextField label="Summary" value={f.summary} onChange={set('summary')} required multiline minRows={3} fullWidth />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField label="Achievements this period" value={f.achievements} onChange={set('achievements')} multiline minRows={3} fullWidth />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField label="Next period" value={f.nextSteps} onChange={set('nextSteps')} multiline minRows={3} fullWidth />
          </Grid>
          <Grid size={12}>
            <TextField label="Decisions or support needed (escalations)" value={f.escalations} onChange={set('escalations')} multiline minRows={2} fullWidth />
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" disabled={!f.summary.trim()} loading={edit.isPending}>
          Save draft
        </Button>
      </DialogActions>
    </Dialog>
  );
}

EditReportDialog.propTypes = { projectId: PropTypes.string.isRequired, report: PropTypes.object.isRequired, onClose: PropTypes.func.isRequired };

function ReviewDialog({ projectId, report, onClose }) {
  const [note, setNote] = useState('');
  const run = useReportAction(projectId);
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Review status report</DialogTitle>
      <DialogContent>
        {run.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {run.error.message}
          </Alert>
        )}
        <TextField label="PMO comment" value={note} onChange={(e) => setNote(e.target.value)} multiline minRows={3} fullWidth sx={{ mt: 1 }} />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" loading={run.isPending} onClick={() => run.mutate({ id: report.id, action: 'review', note: note.trim() || null }, { onSuccess: onClose })}>
          Mark reviewed
        </Button>
      </DialogActions>
    </Dialog>
  );
}

ReviewDialog.propTypes = { projectId: PropTypes.string.isRequired, report: PropTypes.object.isRequired, onClose: PropTypes.func.isRequired };

export default function ReportsTab({ project }) {
  const projectId = project.id;
  const { data, isLoading, error } = useStatusReports(projectId);
  const create = useCreateReport(projectId);
  const run = useReportAction(projectId);
  const [periodEnd, setPeriodEnd] = useState(todayInput());
  const [dialog, setDialog] = useState(null);
  if (error) return <ErrorAlert error={error} title="Could not load status reports" />;
  if (isLoading || !data) return <Skeleton variant="rounded" height={200} />;

  return (
    <>
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: { sm: 'center' } }}>
            <Typography variant="h6" sx={{ flex: 1 }}>
              Status reports
            </Typography>
            <TextField size="small" type="date" label="Period ending" value={periodEnd} onChange={(e) => setPeriodEnd(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
            <Button variant="contained" loading={create.isPending} onClick={() => create.mutate(periodEnd, { onSuccess: (r) => setDialog({ edit: r }) })}>
              New report
            </Button>
          </Stack>
          {(create.error || run.error) && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {(create.error || run.error).message}
            </Alert>
          )}
        </CardContent>
      </Card>
      {!data.length && <Alert severity="info">No status reports yet. Project managers write one each reporting period.</Alert>}
      {data.map((r) => (
        <Card key={r.id} sx={{ mb: 2 }}>
          <CardContent>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: 'space-between', mb: 1 }}>
              <Box>
                <Typography variant="subtitle1">
                  Period ending {formatDate(r.periodEnd)} · {r.status.toLowerCase()}
                </Typography>
                <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap', mt: 0.5 }}>
                  {ASPECTS.map(([k, label]) => (
                    <RagChip key={k} rag={r[k]} label={label} />
                  ))}
                </Stack>
              </Box>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
                {r.canEdit && <Button onClick={() => setDialog({ edit: r })}>Edit</Button>}
                {r.canSubmit && (
                  <Button variant="contained" loading={run.isPending} onClick={() => run.mutate({ id: r.id, action: 'submit' })}>
                    Submit
                  </Button>
                )}
                {r.canReview && (
                  <Button variant="contained" onClick={() => setDialog({ review: r })}>
                    Review
                  </Button>
                )}
              </Stack>
            </Stack>
            <Grid container spacing={3}>
              <Grid size={{ xs: 12, md: 7 }}>
                <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                  {r.summary}
                </Typography>
                {r.achievements && (
                  <Typography variant="body2" sx={{ mt: 1, whiteSpace: 'pre-wrap' }}>
                    <strong>Achievements:</strong> {r.achievements}
                  </Typography>
                )}
                {r.nextSteps && (
                  <Typography variant="body2" sx={{ mt: 1, whiteSpace: 'pre-wrap' }}>
                    <strong>Next period:</strong> {r.nextSteps}
                  </Typography>
                )}
                {r.escalations && (
                  <Alert severity="warning" sx={{ mt: 1 }}>
                    {r.escalations}
                  </Alert>
                )}
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                  {r.submittedBy ? `Submitted by ${r.submittedBy.name} ${formatDateTime(r.submittedAt)}. ` : 'Draft. '}
                  {r.reviewedBy
                    ? `Reviewed by ${r.reviewedBy.name} ${formatDateTime(r.reviewedAt)}${r.reviewComment ? `: ${r.reviewComment}` : ''}`
                    : ''}
                </Typography>
              </Grid>
              <Grid size={{ xs: 12, md: 5 }}>
                <Figures f={r.figures} currency={project.currency} />
              </Grid>
            </Grid>
          </CardContent>
        </Card>
      ))}
      {dialog?.edit && <EditReportDialog projectId={projectId} report={dialog.edit} onClose={() => setDialog(null)} />}
      {dialog?.review && <ReviewDialog projectId={projectId} report={dialog.review} onClose={() => setDialog(null)} />}
    </>
  );
}

ReportsTab.propTypes = { project: PropTypes.object.isRequired };
