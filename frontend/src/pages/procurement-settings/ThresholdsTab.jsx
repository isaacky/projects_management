import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  Grid,
  IconButton,
  MenuItem,
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
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import { useDeleteThreshold, useSaveThreshold } from '../../api/procurementSettings.js';
import { APPROVAL_STAGES, roleLabel } from '../../domain/procurement.js';
import { formatMoney } from '../../utils/money.js';
import { inputToMinor, minorToInput } from '../../utils/amountField.js';
import RoleField from './RoleField.jsx';
import { methodShape, thresholdShape } from './propTypes.js';

function band(t) {
  const from = formatMoney(t.minAmountMinor, t.currency);
  return t.maxAmountMinor == null
    ? `${from} and above`
    : `${from} to below ${formatMoney(t.maxAmountMinor, t.currency)}`;
}

function ThresholdDialog({ threshold, methods, onClose }) {
  const [form, setForm] = useState(() => ({
    stage: threshold?.stage ?? 'REQUISITION',
    methodCode: threshold?.methodCode ?? '',
    min: minorToInput(threshold?.minAmountMinor ?? 0),
    max: minorToInput(threshold?.maxAmountMinor),
    currency: threshold?.currency ?? 'KES',
    stepOrder: String(threshold?.stepOrder ?? 1),
    approverRole: threshold?.approverRole ?? '',
    active: threshold?.active ?? true,
  }));
  const [errors, setErrors] = useState({});
  const save = useSaveThreshold();
  const set = (f) => (e) =>
    setForm((s) => ({ ...s, [f]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));

  const submit = (e) => {
    e.preventDefault();
    const minAmountMinor = inputToMinor(form.min) ?? 0;
    const maxAmountMinor = inputToMinor(form.max);
    const errs = {};
    if (Number.isNaN(minAmountMinor)) errs.min = 'Use digits, e.g. 0';
    if (Number.isNaN(maxAmountMinor)) errs.max = 'Use digits, e.g. 5000000';
    if (!errs.min && !errs.max && maxAmountMinor != null && maxAmountMinor <= minAmountMinor)
      errs.max = 'Must be above the lower limit';
    if (!form.approverRole) errs.approverRole = 'Required';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    save.mutate(
      {
        id: threshold?.id,
        stage: form.stage,
        methodCode: form.methodCode || null,
        minAmountMinor,
        maxAmountMinor,
        currency: form.currency,
        stepOrder: Number(form.stepOrder) || 1,
        approverRole: form.approverRole,
        active: form.active,
      },
      { onSuccess: onClose },
    );
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={submit}>
      <DialogTitle>{threshold ? 'Edit approval step' : 'Add approval step'}</DialogTitle>
      <DialogContent>
        <DialogContentText variant="body2" sx={{ mb: 2 }}>
          A band covers amounts from its lower limit up to, but not including, its upper limit.
          Add several steps for the same band to build an approval chain.
        </DialogContentText>
        {save.error && <Alert severity="error" sx={{ mb: 2 }}>{save.error.message}</Alert>}
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField select label="Decision" value={form.stage} onChange={set('stage')} fullWidth>
              {APPROVAL_STAGES.map((s) => (
                <MenuItem key={s.key} value={s.key}>{s.label}</MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField select label="Procurement method" value={form.methodCode} onChange={set('methodCode')} fullWidth>
              <MenuItem value="">All methods</MenuItem>
              {methods.map((m) => (
                <MenuItem key={m.code} value={m.code}>{m.name}</MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              label={`From (${form.currency})`}
              value={form.min}
              onChange={set('min')}
              error={Boolean(errors.min)}
              helperText={errors.min || 'Included'}
              inputMode="decimal"
              fullWidth
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              label={`Up to (${form.currency})`}
              value={form.max}
              onChange={set('max')}
              error={Boolean(errors.max)}
              helperText={errors.max || 'Not included. Blank = no upper limit'}
              inputMode="decimal"
              fullWidth
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField label="Step" type="number" value={form.stepOrder} onChange={set('stepOrder')} slotProps={{ htmlInput: { min: 1, max: 20 } }} helperText="1 approves first" fullWidth />
          </Grid>
          <Grid size={{ xs: 12, sm: 8 }}>
            <RoleField
              label="Approver"
              value={form.approverRole}
              onChange={(v) => setForm((s) => ({ ...s, approverRole: v }))}
              required
              error={Boolean(errors.approverRole)}
              helperText={errors.approverRole}
            />
          </Grid>
          <Grid size={12}>
            <FormControlLabel control={<Switch checked={form.active} onChange={set('active')} />} label="Active" />
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

ThresholdDialog.propTypes = {
  threshold: thresholdShape,
  methods: PropTypes.arrayOf(methodShape).isRequired,
  onClose: PropTypes.func.isRequired,
};

function DeleteDialog({ threshold, onClose }) {
  const del = useDeleteThreshold();
  return (
    <Dialog open onClose={onClose}>
      <DialogTitle>Remove this approval step?</DialogTitle>
      <DialogContent>
        <DialogContentText>
          {roleLabel(threshold.approverRole)}, step {threshold.stepOrder}, {band(threshold)}. The removal is
          recorded in the audit log. To pause a step instead, edit it and switch it off.
        </DialogContentText>
        {del.error && <Alert severity="error" sx={{ mt: 2 }}>{del.error.message}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button color="error" loading={del.isPending} onClick={() => del.mutate(threshold.id, { onSuccess: onClose })}>
          Remove
        </Button>
      </DialogActions>
    </Dialog>
  );
}

DeleteDialog.propTypes = {
  threshold: thresholdShape.isRequired,
  onClose: PropTypes.func.isRequired,
};

export default function ThresholdsTab({ thresholds, methods }) {
  const [editing, setEditing] = useState(null); // null | 'new' | threshold
  const [deleting, setDeleting] = useState(null);
  const methodName = (code) => methods.find((m) => m.code === code)?.name ?? code;

  return (
    <>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="body2" color="text.secondary">
          Which amounts need which approver. A band set for one method overrides the general bands.
        </Typography>
        <Button startIcon={<AddIcon />} onClick={() => setEditing('new')}>Add approval step</Button>
      </Stack>

      {APPROVAL_STAGES.map((stage) => {
        const rows = thresholds.filter((t) => t.stage === stage.key);
        return (
          <TableContainer key={stage.key} sx={{ mb: 3 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>{stage.label}</Typography>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Method</TableCell>
                  <TableCell>Amount band</TableCell>
                  <TableCell align="center">Step</TableCell>
                  <TableCell>Approver</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((t) => (
                  <TableRow key={t.id} hover sx={{ opacity: t.active ? 1 : 0.55 }}>
                    <TableCell>{t.methodCode ? methodName(t.methodCode) : 'All methods'}</TableCell>
                    <TableCell>{band(t)}</TableCell>
                    <TableCell align="center">{t.stepOrder}</TableCell>
                    <TableCell>{roleLabel(t.approverRole)}</TableCell>
                    <TableCell>
                      <Chip size="small" variant="outlined" label={t.active ? 'Active' : 'Off'} color={t.active ? 'success' : 'default'} />
                    </TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <Tooltip title="Edit">
                        <IconButton size="small" onClick={() => setEditing(t)} aria-label="Edit approval step">
                          <EditOutlined fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Remove">
                        <IconButton size="small" onClick={() => setDeleting(t)} aria-label="Remove approval step">
                          <DeleteOutline fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
                {!rows.length && (
                  <TableRow>
                    <TableCell colSpan={6}>
                      <Typography variant="body2" color="text.secondary">No approvers set for this decision.</Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        );
      })}

      {editing && (
        <ThresholdDialog
          threshold={editing === 'new' ? null : editing}
          methods={methods}
          onClose={() => setEditing(null)}
        />
      )}
      {deleting && <DeleteDialog threshold={deleting} onClose={() => setDeleting(null)} />}
    </>
  );
}

ThresholdsTab.propTypes = {
  thresholds: PropTypes.arrayOf(thresholdShape).isRequired,
  methods: PropTypes.arrayOf(methodShape).isRequired,
};
