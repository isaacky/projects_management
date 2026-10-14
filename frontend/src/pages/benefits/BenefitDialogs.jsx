import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useUsers } from '../../api/me.js';
import { CATEGORY, FREQUENCY, OBJECTIVES_MET, RATING, formatValue } from '../../domain/benefits.js';
import { inputToMinor, minorToInput } from '../../utils/amountField.js';
import { formatMoney } from '../../utils/money.js';

const today = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Nairobi' });
const isNumber = (s) => /^-?\d+(\.\d{1,4})?$/.test(String(s).trim());

function ErrorBox({ error }) {
  return error ? (
    <Alert severity="error" sx={{ mb: 2 }}>
      {error.message}
    </Alert>
  ) : null;
}

ErrorBox.propTypes = { error: PropTypes.instanceOf(Error) };

function UserSelect({ label, value, onChange, helperText }) {
  const { data: users } = useUsers();
  return (
    <TextField select label={label} value={value} onChange={onChange} helperText={helperText} required fullWidth>
      {(users ?? []).map((u) => (
        <MenuItem key={u.id} value={u.id}>
          {u.displayName}
        </MenuItem>
      ))}
    </TextField>
  );
}

UserSelect.propTypes = {
  label: PropTypes.string.isRequired,
  value: PropTypes.string.isRequired,
  onChange: PropTypes.func.isRequired,
  helperText: PropTypes.string,
};

export function BenefitDialog({ benefit, pending, error, onClose, onSave }) {
  const [f, setF] = useState({
    title: benefit?.title ?? '',
    description: benefit?.description ?? '',
    category: benefit?.category ?? 'OPERATIONAL',
    objective: benefit?.objective ?? '',
    ownerId: benefit?.owner?.id ?? '',
    measure: benefit?.measure ?? '',
    unit: benefit?.unit ?? '',
    baselineValue: benefit ? String(Number(benefit.baselineValue)) : '',
    baselineDate: benefit?.baselineDate ?? today(),
    targetValue: benefit ? String(Number(benefit.targetValue)) : '',
    targetDate: benefit?.targetDate ?? '',
    frequency: benefit?.frequency ?? 'QUARTERLY',
    annualValue: minorToInput(benefit?.annualValueMinor),
  });
  const set = (k) => (e) => setF((s) => ({ ...s, [k]: e.target.value }));
  const annualValueMinor = inputToMinor(f.annualValue);
  const numbersOk = isNumber(f.baselineValue) && isNumber(f.targetValue);
  const sameValue = numbersOk && Number(f.baselineValue) === Number(f.targetValue);
  const datesOk = f.baselineDate && f.targetDate && f.targetDate > f.baselineDate;
  const invalid =
    !f.title.trim() || !f.ownerId || !f.measure.trim() || !f.unit.trim() || !numbersOk || sameValue || !datesOk ||
    Number.isNaN(annualValueMinor);
  const direction = numbersOk && !sameValue ? (Number(f.targetValue) > Number(f.baselineValue) ? 'up' : 'down') : null;

  const save = () =>
    onSave({
      title: f.title.trim(),
      description: f.description.trim() || null,
      category: f.category,
      objective: f.objective.trim() || null,
      ownerId: f.ownerId,
      measure: f.measure.trim(),
      unit: f.unit.trim(),
      baselineValue: Number(f.baselineValue),
      baselineDate: f.baselineDate,
      targetValue: Number(f.targetValue),
      targetDate: f.targetDate,
      frequency: f.frequency,
      annualValueMinor,
      version: benefit?.version ?? null,
    });

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>{benefit ? `Edit B${benefit.number}` : 'Add a benefit'}</DialogTitle>
      <DialogContent>
        <ErrorBox error={error} />
        <Grid container spacing={2} sx={{ mt: 0.5 }}>
          <Grid size={{ xs: 12, sm: 8 }}>
            <TextField label="Benefit" value={f.title} onChange={set('title')} required fullWidth placeholder="e.g. Fewer estimated bills" slotProps={{ htmlInput: { maxLength: 200 } }} />
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField select label="Category" value={f.category} onChange={set('category')} fullWidth>
              {Object.entries(CATEGORY).map(([k, v]) => (
                <MenuItem key={k} value={k}>
                  {v}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={12}>
            <TextField label="Description" value={f.description} onChange={set('description')} multiline minRows={2} fullWidth slotProps={{ htmlInput: { maxLength: 2000 } }} />
          </Grid>
          <Grid size={{ xs: 12, sm: 7 }}>
            <TextField
              label="Business-case objective it serves"
              value={f.objective}
              onChange={set('objective')}
              fullWidth
              slotProps={{ htmlInput: { maxLength: 500 } }}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 5 }}>
            <UserSelect label="Benefit owner" value={f.ownerId} onChange={set('ownerId')} helperText="Records the measurements" />
          </Grid>
          <Grid size={12}>
            <Divider textAlign="left">
              <Typography variant="caption" color="text.secondary">
                How it is measured
              </Typography>
            </Divider>
          </Grid>
          <Grid size={{ xs: 12, sm: 8 }}>
            <TextField label="Measure (KPI)" value={f.measure} onChange={set('measure')} required fullWidth placeholder="e.g. Share of bills based on estimates" slotProps={{ htmlInput: { maxLength: 200 } }} />
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField label="Unit" value={f.unit} onChange={set('unit')} required fullWidth placeholder="%, customers, minutes" slotProps={{ htmlInput: { maxLength: 32 } }} />
          </Grid>
          <Grid size={{ xs: 6, sm: 3 }}>
            <TextField label="Baseline" value={f.baselineValue} onChange={set('baselineValue')} error={f.baselineValue !== '' && !isNumber(f.baselineValue)} required fullWidth inputMode="decimal" />
          </Grid>
          <Grid size={{ xs: 6, sm: 3 }}>
            <TextField label="Baseline date" type="date" value={f.baselineDate} onChange={set('baselineDate')} required fullWidth slotProps={{ inputLabel: { shrink: true } }} />
          </Grid>
          <Grid size={{ xs: 6, sm: 3 }}>
            <TextField
              label="Target"
              value={f.targetValue}
              onChange={set('targetValue')}
              error={(f.targetValue !== '' && !isNumber(f.targetValue)) || sameValue}
              helperText={sameValue ? 'Must differ from the baseline' : direction ? `The measure should go ${direction}` : ' '}
              required
              fullWidth
              inputMode="decimal"
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 3 }}>
            <TextField
              label="Target date"
              type="date"
              value={f.targetDate}
              onChange={set('targetDate')}
              error={Boolean(f.targetDate) && !datesOk}
              helperText={f.targetDate && !datesOk ? 'After the baseline date' : ' '}
              required
              fullWidth
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField select label="Measured" value={f.frequency} onChange={set('frequency')} fullWidth>
              {Object.entries(FREQUENCY).map(([k, v]) => (
                <MenuItem key={k} value={k}>
                  {v}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              label="Yearly value at target (KES)"
              value={f.annualValue}
              onChange={set('annualValue')}
              error={Number.isNaN(annualValueMinor)}
              helperText="Optional; used for the money realised so far"
              fullWidth
              inputMode="decimal"
            />
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={invalid} loading={pending} onClick={save}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

BenefitDialog.propTypes = {
  benefit: PropTypes.object,
  pending: PropTypes.bool,
  error: PropTypes.instanceOf(Error),
  onClose: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
};

export function MeasurementDialog({ benefit, pending, error, onClose, onSave }) {
  const [f, setF] = useState({ measuredOn: today(), value: '', source: '', note: '' });
  const set = (k) => (e) => setF((s) => ({ ...s, [k]: e.target.value }));
  const invalid = !f.measuredOn || !isNumber(f.value);
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>
        Record B{benefit.number}: {benefit.measure}
      </DialogTitle>
      <DialogContent>
        <ErrorBox error={error} />
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Baseline {formatValue(benefit.baselineValue, benefit.unit)} · target {formatValue(benefit.targetValue, benefit.unit)} by{' '}
          {benefit.targetDate}
          {benefit.latestValue !== null && ` · last ${formatValue(benefit.latestValue, benefit.unit)} on ${benefit.latestOn}`}
        </Typography>
        <Stack spacing={2}>
          <Stack direction="row" spacing={2}>
            <TextField label="Measured on" type="date" value={f.measuredOn} onChange={set('measuredOn')} required slotProps={{ inputLabel: { shrink: true } }} />
            <TextField
              label={`Value (${benefit.unit})`}
              value={f.value}
              onChange={set('value')}
              error={f.value !== '' && !isNumber(f.value)}
              required
              inputMode="decimal"
              autoFocus
              fullWidth
            />
          </Stack>
          <TextField label="Source" value={f.source} onChange={set('source')} placeholder="e.g. Billing system report BR-12" slotProps={{ htmlInput: { maxLength: 200 } }} />
          <TextField label="Note" value={f.note} onChange={set('note')} multiline minRows={2} slotProps={{ htmlInput: { maxLength: 1000 } }} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={invalid}
          loading={pending}
          onClick={() =>
            onSave({ measuredOn: f.measuredOn, value: Number(f.value), source: f.source.trim() || null, note: f.note.trim() || null })
          }
        >
          Record
        </Button>
      </DialogActions>
    </Dialog>
  );
}

MeasurementDialog.propTypes = {
  benefit: PropTypes.object.isRequired,
  pending: PropTypes.bool,
  error: PropTypes.instanceOf(Error),
  onClose: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
};

export function CloseBenefitDialog({ benefit, pending, error, onClose, onSave }) {
  const [outcome, setOutcome] = useState(benefit.rating === 'ACHIEVED' ? 'ACHIEVED' : 'NOT_ACHIEVED');
  const [note, setNote] = useState('');
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Close B{benefit.number}</DialogTitle>
      <DialogContent>
        <ErrorBox error={error} />
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          A closed benefit takes no more measurements. The PMO can reopen it.
        </Typography>
        <Stack spacing={2}>
          <TextField select label="Outcome" value={outcome} onChange={(e) => setOutcome(e.target.value)}>
            <MenuItem value="ACHIEVED">Achieved</MenuItem>
            <MenuItem value="NOT_ACHIEVED">Not achieved</MenuItem>
            <MenuItem value="CANCELLED">Cancelled (no longer relevant)</MenuItem>
          </TextField>
          <TextField label="Reason" value={note} onChange={(e) => setNote(e.target.value)} required multiline minRows={3} slotProps={{ htmlInput: { maxLength: 2000 } }} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!note.trim()} loading={pending} onClick={() => onSave({ outcome, note: note.trim() })}>
          Close benefit
        </Button>
      </DialogActions>
    </Dialog>
  );
}

CloseBenefitDialog.propTypes = {
  benefit: PropTypes.object.isRequired,
  pending: PropTypes.bool,
  error: PropTypes.instanceOf(Error),
  onClose: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
};

export function ReviewDialog({ review, pending, error, onClose, onSave }) {
  const [f, setF] = useState({
    title: review?.title ?? 'Post-implementation review',
    dueDate: review?.dueDate ?? '',
    leadId: review?.lead?.id ?? '',
  });
  const set = (k) => (e) => setF((s) => ({ ...s, [k]: e.target.value }));
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{review ? 'Change the review' : 'Plan a post-implementation review'}</DialogTitle>
      <DialogContent>
        <ErrorBox error={error} />
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="Title" value={f.title} onChange={set('title')} required slotProps={{ htmlInput: { maxLength: 200 } }} />
          <TextField label="Due" type="date" value={f.dueDate} onChange={set('dueDate')} required slotProps={{ inputLabel: { shrink: true } }} />
          <UserSelect label="Review lead" value={f.leadId} onChange={set('leadId')} helperText="Writes up and completes the review" />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={!f.title.trim() || !f.dueDate || !f.leadId}
          loading={pending}
          onClick={() => onSave({ title: f.title.trim(), dueDate: f.dueDate, leadId: f.leadId, version: review?.version ?? null })}
        >
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

ReviewDialog.propTypes = {
  review: PropTypes.object,
  pending: PropTypes.bool,
  error: PropTypes.instanceOf(Error),
  onClose: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
};

export function StandardReviewsDialog({ handoverDate, pending, error, onClose, onSave }) {
  const [leadId, setLeadId] = useState('');
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Plan the 6- and 12-month reviews</DialogTitle>
      <DialogContent>
        <ErrorBox error={error} />
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Timed from the handover on {handoverDate}.
        </Typography>
        <UserSelect label="Review lead" value={leadId} onChange={(e) => setLeadId(e.target.value)} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!leadId} loading={pending} onClick={() => onSave({ leadId })}>
          Plan reviews
        </Button>
      </DialogActions>
    </Dialog>
  );
}

StandardReviewsDialog.propTypes = {
  handoverDate: PropTypes.string.isRequired,
  pending: PropTypes.bool,
  error: PropTypes.instanceOf(Error),
  onClose: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
};

/** Writing up a review: the overall findings and a rating and comment for each benefit. */
export function FindingsDialog({ review, benefits, pending, error, onClose, onSave }) {
  const assessed = Object.fromEntries((review.assessments ?? []).map((a) => [a.benefitId, a]));
  const scope = benefits.filter((b) => b.status !== 'CANCELLED');
  const [f, setF] = useState({
    conductedOn: review.conductedOn ?? today(),
    objectivesMet: review.objectivesMet ?? '',
    summary: review.summary ?? '',
    recommendations: review.recommendations ?? '',
  });
  const [rows, setRows] = useState(() =>
    Object.fromEntries(
      scope.map((b) => [
        b.id,
        { rating: assessed[b.id]?.rating ?? b.rating ?? '', comment: assessed[b.id]?.comment ?? '' },
      ]),
    ),
  );
  const set = (k) => (e) => setF((s) => ({ ...s, [k]: e.target.value }));
  const setRow = (id, k) => (e) => setRows((s) => ({ ...s, [id]: { ...s[id], [k]: e.target.value } }));
  const body = () => ({
    conductedOn: f.conductedOn || null,
    objectivesMet: f.objectivesMet || null,
    summary: f.summary.trim() || null,
    recommendations: f.recommendations.trim() || null,
    assessments: scope
      .filter((b) => rows[b.id].rating)
      .map((b) => ({ benefitId: b.id, rating: rows[b.id].rating, comment: rows[b.id].comment.trim() || null })),
  });
  const complete =
    f.conductedOn && f.objectivesMet && f.summary.trim() && scope.every((b) => rows[b.id].rating);

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>{review.title}</DialogTitle>
      <DialogContent>
        <ErrorBox error={error} />
        <Grid container spacing={2} sx={{ mt: 0.5 }}>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField label="Held on" type="date" value={f.conductedOn} onChange={set('conductedOn')} fullWidth slotProps={{ inputLabel: { shrink: true } }} />
          </Grid>
          <Grid size={{ xs: 12, sm: 8 }}>
            <TextField select label="Business-case objectives met?" value={f.objectivesMet} onChange={set('objectivesMet')} fullWidth>
              {Object.entries(OBJECTIVES_MET).map(([k, v]) => (
                <MenuItem key={k} value={k}>
                  {v}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={12}>
            <TextField label="Summary" value={f.summary} onChange={set('summary')} multiline minRows={3} fullWidth slotProps={{ htmlInput: { maxLength: 4000 } }} />
          </Grid>
          <Grid size={12}>
            <TextField label="Recommendations" value={f.recommendations} onChange={set('recommendations')} multiline minRows={2} fullWidth slotProps={{ htmlInput: { maxLength: 4000 } }} />
          </Grid>
        </Grid>
        <Typography variant="subtitle2" sx={{ mt: 3, mb: 1 }}>
          Each benefit
        </Typography>
        <Stack spacing={2}>
          {scope.map((b) => (
            <Box key={b.id} sx={{ p: 1.5, border: 1, borderColor: 'divider', borderRadius: 1 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                B{b.number} · {b.title}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {b.measure}: {formatValue(b.latestValue, b.unit)} now, target {formatValue(b.targetValue, b.unit)} by {b.targetDate}
                {b.annualValueMinor ? ` · worth ${formatMoney(b.annualValueMinor, 'KES', { compact: true })} a year` : ''}
              </Typography>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mt: 1 }}>
                <TextField select size="small" label="Rating" value={rows[b.id].rating} onChange={setRow(b.id, 'rating')} sx={{ minWidth: 170 }}>
                  {Object.entries(RATING).map(([k, v]) => (
                    <MenuItem key={k} value={k}>
                      {v.label}
                    </MenuItem>
                  ))}
                </TextField>
                <TextField size="small" label="Comment" value={rows[b.id].comment} onChange={setRow(b.id, 'comment')} fullWidth slotProps={{ htmlInput: { maxLength: 2000 } }} />
              </Stack>
            </Box>
          ))}
          {scope.length === 0 && (
            <Typography variant="body2" color="text.secondary">
              The project has no benefits to assess.
            </Typography>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button loading={pending} onClick={() => onSave(body(), false)}>
          Save draft
        </Button>
        <Button variant="contained" disabled={!complete} loading={pending} onClick={() => onSave(body(), true)}>
          Complete review
        </Button>
      </DialogActions>
    </Dialog>
  );
}

FindingsDialog.propTypes = {
  review: PropTypes.object.isRequired,
  benefits: PropTypes.array.isRequired,
  pending: PropTypes.bool,
  error: PropTypes.instanceOf(Error),
  onClose: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
};
