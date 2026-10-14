import { useState } from 'react';
import PropTypes from 'prop-types';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  Grid,
  IconButton,
  MenuItem,
  Skeleton,
  Stack,
  ToggleButton,
  ToggleButtonGroup,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableFooter,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import { useProjects } from '../../api/projects.js';
import { useProcurementSettings } from '../../api/procurementSettings.js';
import { useRequisition, useRequisitionAction, useSaveRequisition } from '../../api/requisitions.js';
import { formatMoney, toMinorUnits } from '../../utils/money.js';
import { minorToInput } from '../../utils/amountField.js';
import { lineTotalMinor } from '../../domain/requisitions.js';
import { useAvailablePlanLines } from '../../api/procurementPlans.js';

const MIN_UNPLANNED_REASON = 20;

/** Which approved plan line the requisition draws on, or why it is not in the plan. */
function PlanSection({ projectId, total, value, onChange }) {
  const { data: lines, isLoading } = useAvailablePlanLines(projectId || null);
  const options = lines ?? [];
  const selected = options.find((l) => l.id === value.planLineId) ?? value.current ?? null;
  if (isLoading || (options.length === 0 && !value.current && !value.unplannedReason)) return null;
  const over = value.mode === 'plan' && selected && total > selected.balanceMinor;
  const shortReason = value.mode === 'unplanned' && value.unplannedReason.trim().length < MIN_UNPLANNED_REASON;
  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', mb: 2 }}>
          <Box>
            <Typography variant="h6">Annual procurement plan</Typography>
            <Typography variant="body2" color="text.secondary">
              Choose the approved plan line this purchase comes under. A purchase that is not in the plan needs a
              reason and also goes to the accounting officer.
            </Typography>
          </Box>
          <ToggleButtonGroup
            size="small"
            exclusive
            value={value.mode}
            onChange={(_e, mode) => mode && onChange({ ...value, mode })}
            sx={{ flexShrink: 0, alignSelf: 'flex-start' }}
          >
            <ToggleButton value="plan">In the plan</ToggleButton>
            <ToggleButton value="unplanned">Not in the plan</ToggleButton>
          </ToggleButtonGroup>
        </Stack>
        {value.mode === 'plan' ? (
          <Autocomplete
            options={options}
            value={selected}
            onChange={(_e, line) => onChange({ ...value, planLineId: line?.id ?? '', current: null, line })}
            getOptionLabel={(l) => `${l.reference} · ${l.description}`}
            getOptionDisabled={(l) => l.balanceMinor <= 0}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            renderOption={(props, l) => {
              const { key, ...rest } = props;
              return (
                <li key={key} {...rest}>
                  <Box>
                    <Typography variant="body2">
                      {l.reference} · {l.description}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {l.department} · {l.methodCode} · Q{l.quarter} · balance {formatMoney(l.balanceMinor)} of{' '}
                      {formatMoney(l.approvedMinor)}
                    </Typography>
                  </Box>
                </li>
              );
            }}
            renderInput={(params) => (
              <TextField
                {...params}
                label="Plan line"
                error={Boolean(over)}
                helperText={
                  selected
                    ? over
                      ? `More than the ${formatMoney(selected.balanceMinor)} left on this line; it will be refused on submission`
                      : `${formatMoney(selected.balanceMinor)} left on this line`
                    : 'Required before submitting'
                }
              />
            )}
          />
        ) : (
          <TextField
            label="Why this is not in the plan"
            value={value.unplannedReason}
            onChange={(e) => onChange({ ...value, unplannedReason: e.target.value })}
            error={shortReason && value.unplannedReason.length > 0}
            helperText={`At least ${MIN_UNPLANNED_REASON} characters, e.g. an emergency or a need that arose after the plan was approved`}
            slotProps={{ htmlInput: { maxLength: 2000 } }}
            multiline
            minRows={2}
            fullWidth
          />
        )}
      </CardContent>
    </Card>
  );
}

PlanSection.propTypes = {
  projectId: PropTypes.string,
  total: PropTypes.number.isRequired,
  value: PropTypes.object.isRequired,
  onChange: PropTypes.func.isRequired,
};

// Keys only need to be unique within the form (crypto.randomUUID needs HTTPS, so avoid it).
let lineKey = 0;
const emptyLine = () => ({ key: `new-${++lineKey}`, description: '', quantity: '1', unit: 'each', price: '' });

function initialForm(existing, projectId) {
  if (!existing) {
    return {
      projectId: projectId ?? '',
      title: '',
      department: '',
      neededBy: '',
      proposedMethodCode: '',
      justification: '',
      lines: [emptyLine()],
    };
  }
  return {
    projectId: existing.project.id,
    title: existing.title,
    department: existing.department ?? '',
    neededBy: existing.neededBy ?? '',
    proposedMethodCode: existing.proposedMethodCode ?? '',
    justification: existing.justification ?? '',
    lines: existing.lines.map((l) => ({
      key: l.id,
      description: l.description,
      quantity: String(l.quantity),
      unit: l.unit,
      price: minorToInput(l.unitPriceMinor),
    })),
  };
}

function RequisitionForm({ existing, projectId }) {
  const navigate = useNavigate();
  const [form, setForm] = useState(() => initialForm(existing, projectId));
  const [errors, setErrors] = useState({});
  const [plan, setPlan] = useState(() => ({
    mode: existing?.unplannedReason ? 'unplanned' : 'plan',
    planLineId: existing?.planLine?.id ?? '',
    current: existing?.planLine ?? null,
    unplannedReason: existing?.unplannedReason ?? '',
  }));
  const choosePlan = (next) => {
    setPlan(next);
    // Take the line's method when none is proposed yet.
    if (next.line && !form.proposedMethodCode) setForm((s) => ({ ...s, proposedMethodCode: next.line.methodCode }));
  };
  const save = useSaveRequisition();
  const action = useRequisitionAction(existing?.id);
  const { data: projects } = useProjects();
  const { data: settings } = useProcurementSettings();

  const activeProjects = (projects ?? []).filter((p) => p.status === 'ACTIVE');
  const methods = (settings?.methods ?? []).filter((m) => m.enabled);
  const set = (f) => (e) => setForm((s) => ({ ...s, [f]: e.target.value }));
  const setLine = (key, f) => (e) =>
    setForm((s) => ({ ...s, lines: s.lines.map((l) => (l.key === key ? { ...l, [f]: e.target.value } : l)) }));
  const addLine = () => setForm((s) => ({ ...s, lines: [...s.lines, emptyLine()] }));
  const removeLine = (key) => setForm((s) => ({ ...s, lines: s.lines.filter((l) => l.key !== key) }));

  const priced = form.lines.map((l) => {
    const unitPriceMinor = toMinorUnits(l.price);
    const quantity = Number(l.quantity);
    return { ...l, unitPriceMinor, quantityNum: quantity, total: lineTotalMinor(quantity, unitPriceMinor) };
  });
  const total = priced.reduce((sum, l) => sum + (Number.isFinite(l.total) ? l.total : 0), 0);

  const validate = () => {
    const errs = {};
    if (!form.projectId) errs.projectId = 'Pick a project';
    if (!form.title.trim()) errs.title = 'Required';
    priced.forEach((l) => {
      if (!l.description.trim()) errs[`line-${l.key}-description`] = 'Required';
      if (!(l.quantityNum > 0)) errs[`line-${l.key}-quantity`] = '> 0';
      if (!l.unit.trim()) errs[`line-${l.key}-unit`] = 'Required';
      if (Number.isNaN(l.unitPriceMinor) || l.price.trim() === '') errs[`line-${l.key}-price`] = 'e.g. 1500.00';
    });
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const body = () => ({
    id: existing?.id,
    projectId: form.projectId,
    title: form.title.trim(),
    department: form.department.trim() || null,
    neededBy: form.neededBy || null,
    proposedMethodCode: form.proposedMethodCode || null,
    justification: form.justification.trim() || null,
    currency: 'KES',
    planLineId: plan.mode === 'plan' ? plan.planLineId || null : null,
    unplannedReason: plan.mode === 'unplanned' ? plan.unplannedReason.trim() || null : null,
    lines: priced.map((l) => ({
      description: l.description.trim(),
      quantity: l.quantityNum,
      unit: l.unit.trim(),
      unitPriceMinor: l.unitPriceMinor,
    })),
  });

  const submit = (andSubmit) => (e) => {
    e?.preventDefault();
    if (!validate()) return;
    save.mutate(body(), {
      onSuccess: (saved) => {
        if (!andSubmit) {
          navigate(`/requisitions/${saved.id}`);
          return;
        }
        // Only offered when editing an existing draft, so the action hook's id is this one.
        // Stay on the form if submission is refused (e.g. over the plan line's balance) so the reason shows.
        action.mutate({ action: 'submit' }, { onSuccess: () => navigate(`/requisitions/${saved.id}`) });
      },
    });
  };

  const err = save.error || action.error;

  return (
    <Box component="form" onSubmit={submit(false)}>
      {err && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {err.message}
        </Alert>
      )}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                select
                label="Project"
                value={form.projectId}
                onChange={set('projectId')}
                error={Boolean(errors.projectId)}
                helperText={errors.projectId}
                required
                fullWidth
              >
                {activeProjects.map((p) => (
                  <MenuItem key={p.id} value={p.id}>
                    {p.code} · {p.name}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                label="Title"
                value={form.title}
                onChange={set('title')}
                error={Boolean(errors.title)}
                helperText={errors.title || 'What is being bought, in a few words'}
                required
                fullWidth
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField label="Department" value={form.department} onChange={set('department')} fullWidth />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField
                label="Needed by"
                type="date"
                value={form.neededBy}
                onChange={set('neededBy')}
                slotProps={{ inputLabel: { shrink: true } }}
                fullWidth
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField
                select
                label="Proposed method"
                value={form.proposedMethodCode}
                onChange={set('proposedMethodCode')}
                helperText="Optional; procurement can decide"
                fullWidth
              >
                <MenuItem value="">Let procurement decide</MenuItem>
                {methods.map((m) => (
                  <MenuItem key={m.code} value={m.code}>
                    {m.name}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={12}>
              <TextField
                label="Justification"
                value={form.justification}
                onChange={set('justification')}
                helperText="Why this is needed and how it supports the project"
                multiline
                minRows={3}
                fullWidth
              />
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
            <Typography variant="h6">Items</Typography>
            <Button startIcon={<AddIcon />} onClick={addLine}>
              Add line
            </Button>
          </Stack>
          <TableContainer>
            <Table size="small" sx={{ minWidth: 720 }}>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ width: 40 }}>#</TableCell>
                  <TableCell>Description</TableCell>
                  <TableCell sx={{ width: 110 }}>Quantity</TableCell>
                  <TableCell sx={{ width: 110 }}>Unit</TableCell>
                  <TableCell sx={{ width: 160 }}>Unit price (KES)</TableCell>
                  <TableCell align="right" sx={{ width: 170 }}>
                    Total
                  </TableCell>
                  <TableCell sx={{ width: 48 }} />
                </TableRow>
              </TableHead>
              <TableBody>
                {priced.map((l, i) => (
                  <TableRow key={l.key}>
                    <TableCell>{i + 1}</TableCell>
                    <TableCell>
                      <TextField
                        value={l.description}
                        onChange={setLine(l.key, 'description')}
                        error={Boolean(errors[`line-${l.key}-description`])}
                        placeholder="e.g. Distribution transformer 200 kVA"
                        size="small"
                        fullWidth
                      />
                    </TableCell>
                    <TableCell>
                      <TextField
                        value={l.quantity}
                        onChange={setLine(l.key, 'quantity')}
                        error={Boolean(errors[`line-${l.key}-quantity`])}
                        inputMode="decimal"
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      <TextField
                        value={l.unit}
                        onChange={setLine(l.key, 'unit')}
                        error={Boolean(errors[`line-${l.key}-unit`])}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      <TextField
                        value={l.price}
                        onChange={setLine(l.key, 'price')}
                        error={Boolean(errors[`line-${l.key}-price`])}
                        helperText={errors[`line-${l.key}-price`]}
                        inputMode="decimal"
                        size="small"
                      />
                    </TableCell>
                    <TableCell align="right">{Number.isFinite(l.total) ? formatMoney(l.total) : '—'}</TableCell>
                    <TableCell>
                      <Tooltip title="Remove line">
                        <span>
                          <IconButton
                            size="small"
                            onClick={() => removeLine(l.key)}
                            disabled={form.lines.length === 1}
                            aria-label={`Remove line ${i + 1}`}
                          >
                            <DeleteOutline fontSize="small" />
                          </IconButton>
                        </span>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
              <TableFooter>
                <TableRow>
                  <TableCell colSpan={5} align="right" sx={{ fontWeight: 600, color: 'text.primary' }}>
                    Estimated total
                  </TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700, color: 'text.primary' }}>
                    {formatMoney(total)}
                  </TableCell>
                  <TableCell />
                </TableRow>
              </TableFooter>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>

      <PlanSection projectId={form.projectId} total={total} value={plan} onChange={choosePlan} />

      <Stack direction="row" spacing={2} sx={{ justifyContent: 'flex-end' }}>
        <Button onClick={() => navigate(existing ? `/requisitions/${existing.id}` : '/requisitions')}>Cancel</Button>
        <Button type="submit" variant="outlined" loading={save.isPending && !action.isPending}>
          Save draft
        </Button>
        {existing && (
          <Button variant="contained" onClick={submit(true)} loading={save.isPending || action.isPending}>
            Save and submit for approval
          </Button>
        )}
      </Stack>
    </Box>
  );
}

RequisitionForm.propTypes = {
  existing: PropTypes.object,
  projectId: PropTypes.string,
};

export default function RequisitionFormPage() {
  const { id } = useParams();
  const [search] = useSearchParams();
  const { data: existing, isLoading, error } = useRequisition(id);

  if (id && error) return <ErrorAlert error={error} title="Could not load requisition" />;
  if (id && (isLoading || !existing)) return <Skeleton variant="rounded" height={320} />;
  if (existing && !existing.permissions.canEdit) {
    return (
      <Alert severity="info">
        {existing.number} can no longer be edited. Only the requester can change a draft or a returned requisition.
      </Alert>
    );
  }

  return (
    <>
      <PageHeader
        title={existing ? `Edit ${existing.number}` : 'New requisition'}
        subtitle={
          existing
            ? 'Changes are saved as a draft until you submit'
            : 'Save as a draft first; submit for approval from the next page'
        }
      />
      <RequisitionForm key={existing?.id ?? 'new'} existing={existing} projectId={search.get('projectId')} />
    </>
  );
}
