import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Grid,
  IconButton,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditOutlined from '@mui/icons-material/EditOutlined';
import { useSaveMethod } from '../../api/procurementSettings.js';
import { formatMoney } from '../../utils/money.js';
import { inputToMinor, minorToInput } from '../../utils/amountField.js';
import { toRoleCode } from '../../domain/procurement.js';
import { methodShape } from './propTypes.js';

function range(m) {
  if (m.minValueMinor == null && m.maxValueMinor == null) return 'Any value';
  if (m.minValueMinor == null) return `Up to ${formatMoney(m.maxValueMinor, m.currency)}`;
  if (m.maxValueMinor == null) return `From ${formatMoney(m.minValueMinor, m.currency)}`;
  return `${formatMoney(m.minValueMinor, m.currency)} – ${formatMoney(m.maxValueMinor, m.currency)}`;
}

const EMPTY = {
  code: '',
  name: '',
  description: '',
  enabled: true,
  min: '',
  max: '',
  currency: 'KES',
  minBidPeriodDays: '0',
  standstillDays: '14',
  requiresPublicAdvert: false,
  requiresJustification: false,
  sortOrder: '100',
};

function toForm(m) {
  return {
    code: m.code,
    name: m.name,
    description: m.description ?? '',
    enabled: m.enabled,
    min: minorToInput(m.minValueMinor),
    max: minorToInput(m.maxValueMinor),
    currency: m.currency,
    minBidPeriodDays: String(m.minBidPeriodDays),
    standstillDays: String(m.standstillDays ?? 14),
    requiresPublicAdvert: m.requiresPublicAdvert,
    requiresJustification: m.requiresJustification,
    sortOrder: String(m.sortOrder),
  };
}

function MethodDialog({ method, isNew, onClose }) {
  const [form, setForm] = useState(method ? toForm(method) : EMPTY);
  const [errors, setErrors] = useState({});
  const save = useSaveMethod();
  const set = (f) => (e) =>
    setForm((s) => ({ ...s, [f]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));

  const submit = (e) => {
    e.preventDefault();
    const minValueMinor = inputToMinor(form.min);
    const maxValueMinor = inputToMinor(form.max);
    const code = toRoleCode(form.code);
    const errs = {};
    if (!code) errs.code = 'Required';
    if (!form.name.trim()) errs.name = 'Required';
    if (Number.isNaN(minValueMinor)) errs.min = 'Use digits, e.g. 1000000';
    if (Number.isNaN(maxValueMinor)) errs.max = 'Use digits, e.g. 3000000';
    if (!errs.min && !errs.max && minValueMinor != null && maxValueMinor != null && minValueMinor > maxValueMinor)
      errs.max = 'Must be at least the minimum';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    save.mutate(
      {
        code,
        name: form.name.trim(),
        description: form.description.trim() || null,
        enabled: form.enabled,
        minValueMinor,
        maxValueMinor,
        currency: form.currency,
        minBidPeriodDays: Number(form.minBidPeriodDays) || 0,
        standstillDays: Number(form.standstillDays) || 0,
        requiresPublicAdvert: form.requiresPublicAdvert,
        requiresJustification: form.requiresJustification,
        sortOrder: Number(form.sortOrder) || 0,
      },
      { onSuccess: onClose },
    );
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={submit}>
      <DialogTitle>{isNew ? 'Add procurement method' : `Edit ${method.name}`}</DialogTitle>
      <DialogContent>
        {save.error && <Alert severity="error" sx={{ mb: 2 }}>{save.error.message}</Alert>}
        <Grid container spacing={2} sx={{ mt: 0.5 }}>
          <Grid size={{ xs: 12, sm: 5 }}>
            <TextField
              label="Code"
              value={form.code}
              onChange={set('code')}
              disabled={!isNew}
              error={Boolean(errors.code)}
              helperText={errors.code || 'e.g. OPEN_TENDER'}
              fullWidth
              required
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 7 }}>
            <TextField
              label="Name"
              value={form.name}
              onChange={set('name')}
              error={Boolean(errors.name)}
              helperText={errors.name}
              fullWidth
              required
            />
          </Grid>
          <Grid size={12}>
            <TextField label="Description" value={form.description} onChange={set('description')} multiline minRows={2} fullWidth />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              label={`Minimum value (${form.currency})`}
              value={form.min}
              onChange={set('min')}
              error={Boolean(errors.min)}
              helperText={errors.min || 'Blank = no minimum'}
              inputMode="decimal"
              fullWidth
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              label={`Maximum value (${form.currency})`}
              value={form.max}
              onChange={set('max')}
              error={Boolean(errors.max)}
              helperText={errors.max || 'Blank = no maximum'}
              inputMode="decimal"
              fullWidth
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 4 }}>
            <TextField label="Minimum bid period (days)" type="number" value={form.minBidPeriodDays} onChange={set('minBidPeriodDays')} slotProps={{ htmlInput: { min: 0, max: 365 } }} fullWidth />
          </Grid>
          <Grid size={{ xs: 6, sm: 4 }}>
            <TextField
              label="Standstill after award (days)"
              type="number"
              value={form.standstillDays}
              onChange={set('standstillDays')}
              slotProps={{ htmlInput: { min: 0, max: 90 } }}
              helperText="Before the contract may be signed"
              fullWidth
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 4 }}>
            <TextField label="Display order" type="number" value={form.sortOrder} onChange={set('sortOrder')} fullWidth />
          </Grid>
          <Grid size={12}>
            <Stack>
              <FormControlLabel control={<Switch checked={form.enabled} onChange={set('enabled')} />} label="In use" />
              <FormControlLabel control={<Switch checked={form.requiresPublicAdvert} onChange={set('requiresPublicAdvert')} />} label="Must be publicly advertised" />
              <FormControlLabel control={<Switch checked={form.requiresJustification} onChange={set('requiresJustification')} />} label="Needs a written justification" />
            </Stack>
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" loading={save.isPending}>Save</Button>
      </DialogActions>
    </Dialog>
  );
}

MethodDialog.propTypes = {
  method: methodShape,
  isNew: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
};

export default function MethodsTab({ methods }) {
  const [editing, setEditing] = useState(null); // null | 'new' | method

  return (
    <>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="body2" color="text.secondary">
          The methods this organisation uses, and the estimated values each may be used for.
        </Typography>
        <Button startIcon={<AddIcon />} onClick={() => setEditing('new')}>Add method</Button>
      </Stack>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Method</TableCell>
              <TableCell>Value range</TableCell>
              <TableCell align="right">Min. bid period</TableCell>
              <TableCell>Conditions</TableCell>
              <TableCell>Status</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {methods.map((m) => (
              <TableRow key={m.code} hover>
                <TableCell>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>{m.name}</Typography>
                  <Typography variant="caption" color="text.secondary">{m.code}</Typography>
                </TableCell>
                <TableCell>{range(m)}</TableCell>
                <TableCell align="right">{m.minBidPeriodDays} days</TableCell>
                <TableCell>
                  <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
                    {m.requiresPublicAdvert && <Chip size="small" label="Advertised" />}
                    {m.requiresJustification && <Chip size="small" label="Justification" />}
                  </Stack>
                </TableCell>
                <TableCell>
                  <Chip size="small" label={m.enabled ? 'In use' : 'Off'} color={m.enabled ? 'success' : 'default'} variant="outlined" />
                </TableCell>
                <TableCell align="right">
                  <Tooltip title="Edit">
                    <IconButton size="small" onClick={() => setEditing(m)} aria-label={`Edit ${m.name}`}>
                      <EditOutlined fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
            {!methods.length && (
              <TableRow>
                <TableCell colSpan={6}>No procurement methods yet. Add the ones this organisation uses.</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
      {editing && (
        <MethodDialog
          method={editing === 'new' ? null : editing}
          isNew={editing === 'new'}
          onClose={() => setEditing(null)}
        />
      )}
    </>
  );
}

MethodsTab.propTypes = {
  methods: PropTypes.arrayOf(methodShape).isRequired,
};
