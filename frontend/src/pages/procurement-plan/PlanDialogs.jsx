import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Button,
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
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useUsers } from '../../api/me.js';
import { useProjects } from '../../api/projects.js';
import { useProcurementSettings } from '../../api/procurementSettings.js';
import { useImportPlanLines } from '../../api/procurementPlans.js';
import { CATEGORY, IMPORT_TEMPLATE, RESERVED, quarterRange, saveText } from '../../domain/procurementPlan.js';
import { formatMoney } from '../../utils/money.js';
import { inputToMinor, minorToInput } from '../../utils/amountField.js';

/** A note or reason, with an optional minimum length. */
export function NoteDialog({ open, title, text, label, required, minLength = 0, confirm, pending, error, onClose, onConfirm }) {
  const [note, setNote] = useState('');
  const short = note.trim().length < (required ? Math.max(1, minLength) : 0);
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        {text && <DialogContentText sx={{ mb: 2 }}>{text}</DialogContentText>}
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error.message}
          </Alert>
        )}
        <TextField
          label={label}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          required={required}
          helperText={minLength > 0 ? `At least ${minLength} characters` : undefined}
          slotProps={{ htmlInput: { maxLength: 2000 } }}
          multiline
          minRows={3}
          fullWidth
          autoFocus
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={short} loading={pending} onClick={() => onConfirm(note.trim() || null)}>
          {confirm}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

NoteDialog.propTypes = {
  open: PropTypes.bool.isRequired,
  title: PropTypes.string.isRequired,
  text: PropTypes.node,
  label: PropTypes.string.isRequired,
  required: PropTypes.bool,
  minLength: PropTypes.number,
  confirm: PropTypes.string.isRequired,
  pending: PropTypes.bool,
  error: PropTypes.instanceOf(Error),
  onClose: PropTypes.func.isRequired,
  onConfirm: PropTypes.func.isRequired,
};

export function DepartmentDialog({ department, pending, error, onClose, onSave }) {
  const { data: users } = useUsers();
  const [f, setF] = useState({
    name: department?.name ?? '',
    headId: department?.head?.id ?? '',
    ceiling: minorToInput(department?.ceilingMinor),
  });
  const ceilingMinor = inputToMinor(f.ceiling);
  const invalid = !f.name.trim() || !f.headId || Number.isNaN(ceilingMinor);
  const set = (k) => (e) => setF((s) => ({ ...s, [k]: e.target.value }));
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{department ? `Edit ${department.name}` : 'Add a department'}</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error.message}
          </Alert>
        )}
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="Department" value={f.name} onChange={set('name')} required slotProps={{ htmlInput: { maxLength: 120 } }} />
          <TextField
            select
            label="Head"
            value={f.headId}
            onChange={set('headId')}
            required
            helperText="Enters the department's lines and submits them to procurement"
          >
            {(users ?? []).map((u) => (
              <MenuItem key={u.id} value={u.id}>
                {u.displayName}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Budget ceiling (KES)"
            value={f.ceiling}
            onChange={set('ceiling')}
            error={Number.isNaN(ceilingMinor)}
            helperText="Optional. The department cannot submit lines that add up to more."
            inputMode="decimal"
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={invalid}
          loading={pending}
          onClick={() => onSave({ name: f.name.trim(), headId: f.headId, ceilingMinor })}
        >
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

DepartmentDialog.propTypes = {
  department: PropTypes.object,
  pending: PropTypes.bool,
  error: PropTypes.instanceOf(Error),
  onClose: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
};

/** Enabled methods whose value range covers the amount. */
function allowedMethods(methods, minor) {
  return methods.filter(
    (m) =>
      m.enabled &&
      (m.minValueMinor === null || m.minValueMinor === undefined || minor >= m.minValueMinor) &&
      (m.maxValueMinor === null || m.maxValueMinor === undefined || minor <= m.maxValueMinor),
  );
}

export function LineDialog({ plan, line, departments, pending, error, onClose, onSave }) {
  const { data: settings } = useProcurementSettings();
  const { data: projects } = useProjects();
  const [f, setF] = useState({
    departmentId: line?.departmentId ?? departments[0]?.id ?? '',
    description: line?.description ?? '',
    category: line?.category ?? 'GOODS',
    quantity: line ? String(line.quantity) : '1',
    unit: line?.unit ?? 'lot',
    estimate: minorToInput(line?.estimateMinor),
    methodCode: line?.methodCode ?? '',
    quarter: line?.quarter ?? 1,
    sourceOfFunds: line?.sourceOfFunds ?? '',
    budgetCode: line?.budgetCode ?? '',
    reservedGroup: line?.reservedGroup ?? '',
    projectId: line?.project?.id ?? '',
  });
  const set = (k) => (e) => setF((s) => ({ ...s, [k]: e.target.value }));
  const estimateMinor = inputToMinor(f.estimate);
  const quantity = Number(f.quantity);
  const methods = settings?.methods ?? [];
  const fits = Number.isFinite(estimateMinor) && estimateMinor !== null ? allowedMethods(methods, estimateMinor) : [];
  const methodFits = !f.methodCode || fits.some((m) => m.code === f.methodCode);
  const invalid =
    !f.departmentId ||
    !f.description.trim() ||
    !(quantity > 0) ||
    !f.unit.trim() ||
    estimateMinor === null ||
    Number.isNaN(estimateMinor) ||
    !f.methodCode;

  const save = () =>
    onSave({
      departmentId: f.departmentId,
      description: f.description.trim(),
      category: f.category,
      quantity,
      unit: f.unit.trim(),
      estimateMinor,
      methodCode: f.methodCode,
      quarter: Number(f.quarter),
      sourceOfFunds: f.sourceOfFunds.trim() || null,
      budgetCode: f.budgetCode.trim() || null,
      reservedGroup: f.reservedGroup || null,
      projectId: f.projectId || null,
    });

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>{line ? `Edit ${line.reference}` : 'Add a planned purchase'}</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error.message}
          </Alert>
        )}
        {line?.approvedEstimateMinor !== null && line?.approvedEstimateMinor !== undefined && (
          <Alert severity="info" sx={{ mb: 2 }}>
            Approved at {formatMoney(line.approvedEstimateMinor)}. Requisitions use the approved amount until this
            revision is approved.
          </Alert>
        )}
        <Grid container spacing={2} sx={{ mt: 0.5 }}>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField select label="Department" value={f.departmentId} onChange={set('departmentId')} required fullWidth>
              {departments.map((d) => (
                <MenuItem key={d.id} value={d.id}>
                  {d.name}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField select label="Category" value={f.category} onChange={set('category')} required fullWidth>
              {Object.entries(CATEGORY).map(([k, v]) => (
                <MenuItem key={k} value={k}>
                  {v}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={12}>
            <TextField
              label="What is to be bought"
              value={f.description}
              onChange={set('description')}
              required
              fullWidth
              slotProps={{ htmlInput: { maxLength: 500 } }}
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 3 }}>
            <TextField label="Quantity" value={f.quantity} onChange={set('quantity')} error={!(quantity > 0)} inputMode="decimal" fullWidth />
          </Grid>
          <Grid size={{ xs: 6, sm: 3 }}>
            <TextField label="Unit" value={f.unit} onChange={set('unit')} required fullWidth slotProps={{ htmlInput: { maxLength: 32 } }} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              label="Estimated cost (KES)"
              value={f.estimate}
              onChange={set('estimate')}
              error={Number.isNaN(estimateMinor)}
              helperText={Number.isFinite(estimateMinor) && estimateMinor !== null ? formatMoney(estimateMinor) : 'e.g. 1250000'}
              required
              inputMode="decimal"
              fullWidth
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              select
              label="Procurement method"
              value={f.methodCode}
              onChange={set('methodCode')}
              error={!methodFits}
              helperText={
                !methodFits
                  ? 'This method is not allowed for this amount'
                  : fits.length
                    ? `Allowed for this amount: ${fits.map((m) => m.code).join(', ')}`
                    : 'Enter the estimate to see the allowed methods'
              }
              required
              fullWidth
            >
              {methods
                .filter((m) => m.enabled)
                .map((m) => (
                  <MenuItem key={m.code} value={m.code}>
                    {m.name}
                  </MenuItem>
                ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField select label="Quarter to start" value={f.quarter} onChange={set('quarter')} required fullWidth>
              {[1, 2, 3, 4].map((q) => (
                <MenuItem key={q} value={q}>
                  Q{q} · {quarterRange(plan.fyStart, q)}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              label="Source of funds"
              value={f.sourceOfFunds}
              onChange={set('sourceOfFunds')}
              placeholder="e.g. GoK development, internal resources"
              fullWidth
              slotProps={{ htmlInput: { maxLength: 120 } }}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField label="Budget code" value={f.budgetCode} onChange={set('budgetCode')} fullWidth slotProps={{ htmlInput: { maxLength: 60 } }} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              select
              label="Reserved for (AGPO)"
              value={f.reservedGroup}
              onChange={set('reservedGroup')}
              helperText="Counts towards the AGPO target"
              fullWidth
            >
              <MenuItem value="">Not reserved</MenuItem>
              {Object.entries(RESERVED).map(([k, v]) => (
                <MenuItem key={k} value={k}>
                  {v}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField select label="Project" value={f.projectId} onChange={set('projectId')} helperText="Optional" fullWidth>
              <MenuItem value="">None</MenuItem>
              {(projects ?? []).map((p) => (
                <MenuItem key={p.id} value={p.id}>
                  {p.code} · {p.name}
                </MenuItem>
              ))}
            </TextField>
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

LineDialog.propTypes = {
  plan: PropTypes.object.isRequired,
  line: PropTypes.object,
  departments: PropTypes.array.isRequired,
  pending: PropTypes.bool,
  error: PropTypes.instanceOf(Error),
  onClose: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
};

export function ImportDialog({ planId, department, onClose }) {
  const [file, setFile] = useState(null);
  const upload = useImportPlanLines(planId);
  const result = upload.data;
  const done = result && result.imported > 0;
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Import lines into {department.name}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" sx={{ mb: 2 }}>
          Save the department&apos;s sheet from Excel as CSV. Columns are matched by name: description, category,
          quantity, unit, estimate (KES), method, quarter, and optionally source of funds, budget code, reserved and
          project code. Every row is checked first; if any row has a problem nothing is imported.{' '}
          <Link component="button" type="button" onClick={() => saveText(IMPORT_TEMPLATE, 'plan-lines-template.csv')}>
            Download a template
          </Link>
        </Typography>
        {upload.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {upload.error.message}
          </Alert>
        )}
        {result && result.errors.length > 0 && (
          <Alert severity="error" sx={{ mb: 2 }}>
            Nothing was imported. Fix these rows and try again:
            <List dense disablePadding>
              {result.errors.map((e) => (
                <ListItem key={e} disableGutters>
                  <ListItemText primary={e} />
                </ListItem>
              ))}
            </List>
          </Alert>
        )}
        {done && (
          <Alert severity="success" sx={{ mb: 2 }}>
            Imported {result.imported} line{result.imported === 1 ? '' : 's'}.
          </Alert>
        )}
        {!done && (
          <Button variant="outlined" component="label">
            {file ? file.name : 'Choose CSV file'}
            <input type="file" accept=".csv,text/csv" hidden onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          </Button>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{done ? 'Close' : 'Cancel'}</Button>
        {!done && (
          <Button
            variant="contained"
            disabled={!file}
            loading={upload.isPending}
            onClick={() => upload.mutate({ departmentId: department.id, file })}
          >
            Import
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}

ImportDialog.propTypes = {
  planId: PropTypes.string.isRequired,
  department: PropTypes.object.isRequired,
  onClose: PropTypes.func.isRequired,
};
