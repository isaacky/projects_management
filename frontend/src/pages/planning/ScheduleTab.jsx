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
  DialogContentText,
  DialogTitle,
  Grid,
  Skeleton,
  Slider,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import FilePicker from '../tenders/FilePicker.jsx';
import Gantt from './Gantt.jsx';
import SCurve from './SCurve.jsx';
import ScheduleEditor from './ScheduleEditor.jsx';
import { useBaseline, useImportSchedule, useRecordProgress, useSchedule } from '../../api/planning.js';
import { formatDate, todayInput } from '../../domain/contracts.js';
import { formatDateTime } from '../../domain/tenders.js';
import { pct } from '../../domain/planning.js';

function Stat({ label, value, hint, color }) {
  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="caption" color="text.secondary">
          {label}
        </Typography>
        <Typography variant="h6" sx={{ color }}>
          {value}
        </Typography>
        {hint && (
          <Typography variant="caption" color="text.secondary">
            {hint}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

Stat.propTypes = { label: PropTypes.string.isRequired, value: PropTypes.node, hint: PropTypes.node, color: PropTypes.string };

function ProgressDialog({ projectId, activity: a, onClose }) {
  const [start, setStart] = useState(a.actualStart ?? '');
  const [finish, setFinish] = useState(a.actualFinish ?? '');
  const [percent, setPercent] = useState(Number(a.percent));
  const [note, setNote] = useState(a.progressNote ?? '');
  const record = useRecordProgress(projectId);
  const done = Boolean(finish);
  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      component="form"
      onSubmit={(e) => {
        e.preventDefault();
        record.mutate(
          { activityId: a.id, actualStart: start || null, actualFinish: finish || null, percent: done ? 100 : percent, note: note.trim() || null },
          { onSuccess: onClose },
        );
      }}
    >
      <DialogTitle>
        Progress · {a.wbs} {a.name}
      </DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Planned {formatDate(a.start)} to {formatDate(a.finish)}
          {a.baselineFinish ? ` (baseline finish ${formatDate(a.baselineFinish)})` : ''}.
          {a.progressUpdatedAt ? ` Last updated ${formatDateTime(a.progressUpdatedAt)} by ${a.progressUpdatedBy?.name}.` : ''}
        </DialogContentText>
        {record.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {record.error.message}
          </Alert>
        )}
        <Stack spacing={2}>
          <Stack direction="row" spacing={2}>
            <TextField type="date" label="Actual start" value={start} onChange={(e) => setStart(e.target.value)} slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: todayInput() } }} fullWidth />
            <TextField type="date" label="Actual finish" value={finish} onChange={(e) => setFinish(e.target.value)} slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: todayInput() } }} helperText="Only when it is finished" fullWidth />
          </Stack>
          {!done && (
            <Box sx={{ px: 1 }}>
              <Typography variant="body2">Percent complete: {percent}%</Typography>
              <Slider value={percent} onChange={(_e, v) => setPercent(v)} step={5} min={0} max={95} marks valueLabelDisplay="auto" />
            </Box>
          )}
          <TextField label="Note" value={note} onChange={(e) => setNote(e.target.value)} multiline minRows={2} fullWidth />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" loading={record.isPending} disabled={(percent > 0 || done) && !start}>
          Save progress
        </Button>
      </DialogActions>
    </Dialog>
  );
}

ProgressDialog.propTypes = { projectId: PropTypes.string.isRequired, activity: PropTypes.object.isRequired, onClose: PropTypes.func.isRequired };

function BaselineDialog({ projectId, view, onClose }) {
  const [note, setNote] = useState('');
  const baseline = useBaseline(projectId);
  const again = view.baselineCount > 0;
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{again ? 'Re-baseline the schedule' : 'Set the baseline'}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          The baseline freezes today&apos;s planned dates (finish {formatDate(view.metrics?.plannedFinish)}). Progress and
          slippage are measured against it.
          {again ? ' Re-baselining hides past slippage, so it needs a reason and is recorded in the history.' : ''}
        </DialogContentText>
        {baseline.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {baseline.error.message}
          </Alert>
        )}
        <TextField label={again ? 'Reason (for example the approved change)' : 'Note (optional)'} value={note} onChange={(e) => setNote(e.target.value)} required={again} multiline minRows={2} fullWidth />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={again && !note.trim()} loading={baseline.isPending} onClick={() => baseline.mutate(note.trim() || null, { onSuccess: onClose })}>
          {again ? 'Re-baseline' : 'Set baseline'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

BaselineDialog.propTypes = { projectId: PropTypes.string.isRequired, view: PropTypes.object.isRequired, onClose: PropTypes.func.isRequired };

function ImportDialog({ projectId, hasActivities, onClose }) {
  const [files, setFiles] = useState([]);
  const run = useImportSchedule(projectId);
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Import from Microsoft Project</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          In MS Project choose File → Save As → XML. Tasks, the outline, finish-to-start and start-to-start links and
          any progress are imported. Durations become calendar days so the dates match MS Project.
          {hasActivities ? ' The current schedule will be replaced.' : ''}
        </DialogContentText>
        {run.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {run.error.message}
          </Alert>
        )}
        <FilePicker label="Choose XML file" files={files} onChange={(f) => setFiles(f.slice(-1))} />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!files.length} loading={run.isPending} onClick={() => run.mutate(files[0], { onSuccess: onClose })}>
          Import
        </Button>
      </DialogActions>
    </Dialog>
  );
}

ImportDialog.propTypes = { projectId: PropTypes.string.isRequired, hasActivities: PropTypes.bool, onClose: PropTypes.func.isRequired };

export default function ScheduleTab({ projectId }) {
  const { data: view, isLoading, error } = useSchedule(projectId);
  const [dialog, setDialog] = useState(null);
  const close = () => setDialog(null);
  const today = todayInput();

  if (error) return <ErrorAlert error={error} title="Could not load the schedule" />;
  if (isLoading || !view) return <Skeleton variant="rounded" height={240} />;
  const can = view.permissions;
  const m = view.metrics;
  const hasActivities = view.activities.length > 0;

  return (
    <>
      <Stack direction="row" spacing={1} useFlexGap sx={{ mb: 2, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
        {can.canImport && <Button onClick={() => setDialog('import')}>Import from MS Project</Button>}
        {can.canBaseline && (
          <Button onClick={() => setDialog('baseline')}>{view.baselineCount > 0 ? 'Re-baseline' : 'Set baseline'}</Button>
        )}
        {can.canEdit && (
          <Button variant="contained" onClick={() => setDialog('edit')}>
            {hasActivities ? 'Edit schedule' : 'Build schedule'}
          </Button>
        )}
      </Stack>
      {view.notices.map((n) => (
        <Alert key={n} severity="warning" sx={{ mb: 1 }}>
          {n}
        </Alert>
      ))}

      {!hasActivities ? (
        <Alert severity="info">
          No schedule yet.{' '}
          {can.canEdit ? 'Build one here or import it from MS Project.' : 'The project manager builds the schedule.'}
        </Alert>
      ) : (
        <>
          <Grid container spacing={2} sx={{ mb: 3 }}>
            <Grid size={{ xs: 6, md: 2 }}>
              <Stat label="Progress" value={pct(m.earnedPct)} hint={`planned ${pct(m.plannedPct)}`} />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <Stat
                label="Schedule performance (SPI)"
                value={m.spi ?? '—'}
                color={m.spi == null ? undefined : m.spi >= 0.95 ? 'success.main' : m.spi >= 0.85 ? 'warning.main' : 'error.main'}
                hint="earned ÷ planned"
              />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <Stat label="Baseline finish" value={formatDate(m.baselineFinish)} hint={view.baselineCount ? `baseline ${view.baselineCount}` : 'not baselined'} />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <Stat
                label="Forecast finish"
                value={formatDate(m.forecastFinish)}
                color={m.slipDays > 0 ? 'error.main' : 'success.main'}
                hint={m.slipDays > 0 ? `${m.slipDays} days late` : m.slipDays < 0 ? `${-m.slipDays} days early` : 'on time'}
              />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <Stat label="Critical activities" value={m.criticalActivities} hint={`of ${m.activities}`} />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <Stat label="Late activities" value={m.lateActivities} color={m.lateActivities ? 'error.main' : undefined} hint="should have finished" />
            </Grid>
          </Grid>

          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            Red bars are on the critical path; grey lines show the baseline; dashed outlines show a later forecast.
            {view.activities.some((a) => a.canUpdateProgress) ? ' Click an activity to record progress.' : ''}
          </Typography>
          <Gantt
            activities={view.activities}
            today={today}
            onRowClick={(a) => (a.canUpdateProgress ? setDialog({ progress: a }) : undefined)}
          />

          <Card sx={{ mt: 3 }}>
            <CardContent>
              <Typography variant="h6">Progress curve</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                {view.baselineCount ? 'Planned progress follows the baseline.' : 'Set a baseline to fix the planned curve.'} Earned
                progress is recorded each day progress is updated.
              </Typography>
              <SCurve planned={view.plannedCurve} actual={view.actualCurve} today={today} />
            </CardContent>
          </Card>
          {view.baselineSetAt && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
              Baseline set {formatDateTime(view.baselineSetAt)} by {view.baselineSetBy?.name}
              {view.baselineNote ? `: ${view.baselineNote}` : ''}
            </Typography>
          )}
        </>
      )}

      {dialog === 'edit' && <ScheduleEditor projectId={projectId} view={view} onClose={close} />}
      {dialog === 'baseline' && <BaselineDialog projectId={projectId} view={view} onClose={close} />}
      {dialog === 'import' && <ImportDialog projectId={projectId} hasActivities={hasActivities} onClose={close} />}
      {dialog?.progress && <ProgressDialog projectId={projectId} activity={dialog.progress} onClose={close} />}
    </>
  );
}

ScheduleTab.propTypes = { projectId: PropTypes.string.isRequired };
