import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Button,
  Card,
  CardActions,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  Grid,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useDeleteCommitteeRule, useSaveCommitteeRule } from '../../api/procurementSettings.js';
import { REPORT_SIGNATORIES, roleLabel, signatoriesLabel } from '../../domain/procurement.js';
import RoleField from './RoleField.jsx';
import { committeeRuleShape, methodShape } from './propTypes.js';

const NEW_RULE = {
  minMembers: 3,
  maxMembers: 5,
  quorum: 3,
  twoEnvelope: false,
  technicalPassMarkPct: null,
  requiresCoiDeclaration: true,
  reportSignatories: 'ALL_MEMBERS',
  reportReviewerRole: 'HEAD_OF_PROCUREMENT',
  awardApproverRole: 'ACCOUNTING_OFFICER',
};

function Row({ label, children }) {
  return (
    <Stack direction="row" spacing={2} sx={{ justifyContent: 'space-between', py: 0.5 }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="body2" sx={{ textAlign: 'right' }}>{children}</Typography>
    </Stack>
  );
}

Row.propTypes = { label: PropTypes.string.isRequired, children: PropTypes.node };

function RuleDialog({ rule, methodCode, title, availableMethods, onClose }) {
  const isNewMethodRule = availableMethods !== null;
  const start = rule ?? NEW_RULE;
  const [form, setForm] = useState({
    methodCode: methodCode ?? (isNewMethodRule ? availableMethods[0]?.code ?? '' : null),
    minMembers: String(start.minMembers),
    maxMembers: String(start.maxMembers),
    quorum: String(start.quorum),
    twoEnvelope: start.twoEnvelope,
    passMark: start.technicalPassMarkPct == null ? '' : String(start.technicalPassMarkPct),
    requiresCoiDeclaration: start.requiresCoiDeclaration,
    reportSignatories: start.reportSignatories,
    reportReviewerRole: start.reportReviewerRole ?? '',
    awardApproverRole: start.awardApproverRole,
  });
  const [errors, setErrors] = useState({});
  const save = useSaveCommitteeRule();
  const set = (f) => (e) =>
    setForm((s) => ({ ...s, [f]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));

  const submit = (e) => {
    e.preventDefault();
    const min = Number(form.minMembers);
    const max = Number(form.maxMembers);
    const quorum = Number(form.quorum);
    const passMark = form.passMark === '' ? null : Number(form.passMark);
    const errs = {};
    if (!(min >= 1)) errs.minMembers = 'At least 1';
    if (!(max >= min)) errs.maxMembers = 'At least the minimum';
    if (!(quorum >= 1 && quorum <= min)) errs.quorum = 'Between 1 and the minimum size';
    if (passMark !== null && !(passMark >= 0 && passMark <= 100)) errs.passMark = '0 to 100';
    if (!form.awardApproverRole) errs.awardApproverRole = 'Required';
    if (isNewMethodRule && !form.methodCode) errs.methodCode = 'Pick a method';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    save.mutate(
      {
        methodCode: form.methodCode,
        minMembers: min,
        maxMembers: max,
        quorum,
        twoEnvelope: form.twoEnvelope,
        technicalPassMarkPct: passMark,
        requiresCoiDeclaration: form.requiresCoiDeclaration,
        reportSignatories: form.reportSignatories,
        reportReviewerRole: form.reportReviewerRole || null,
        awardApproverRole: form.awardApproverRole,
      },
      { onSuccess: onClose },
    );
  };

  const num = (field, label, helper) => (
    <TextField
      label={label}
      type="number"
      value={form[field]}
      onChange={set(field)}
      error={Boolean(errors[field])}
      helperText={errors[field] || helper}
      slotProps={{ htmlInput: { min: 0, max: 100 } }}
      fullWidth
    />
  );

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={submit}>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        {save.error && <Alert severity="error" sx={{ mb: 2 }}>{save.error.message}</Alert>}
        <Grid container spacing={2} sx={{ mt: 0.5 }}>
          {isNewMethodRule && (
            <Grid size={12}>
              <TextField
                select
                label="Procurement method"
                value={form.methodCode}
                onChange={set('methodCode')}
                error={Boolean(errors.methodCode)}
                helperText={errors.methodCode || 'This rule replaces the default for this method'}
                fullWidth
              >
                {availableMethods.map((m) => (
                  <MenuItem key={m.code} value={m.code}>{m.name}</MenuItem>
                ))}
              </TextField>
            </Grid>
          )}
          <Grid size={{ xs: 4 }}>{num('minMembers', 'Min. members')}</Grid>
          <Grid size={{ xs: 4 }}>{num('maxMembers', 'Max. members')}</Grid>
          <Grid size={{ xs: 4 }}>{num('quorum', 'Quorum')}</Grid>
          <Grid size={12}>
            <FormControlLabel
              control={<Switch checked={form.twoEnvelope} onChange={set('twoEnvelope')} />}
              label="Open technical and financial bids separately (two-envelope)"
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            {num('passMark', 'Technical pass mark (%)', form.twoEnvelope ? 'Bidders below it are not opened financially' : 'Optional')}
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField select label="Report signed by" value={form.reportSignatories} onChange={set('reportSignatories')} fullWidth>
              {REPORT_SIGNATORIES.map((s) => (
                <MenuItem key={s.key} value={s.key}>{s.label}</MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <RoleField
              label="Report reviewed by"
              value={form.reportReviewerRole}
              onChange={(v) => setForm((s) => ({ ...s, reportReviewerRole: v }))}
              helperText="e.g. professional opinion; optional"
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <RoleField
              label="Award approved by"
              value={form.awardApproverRole}
              onChange={(v) => setForm((s) => ({ ...s, awardApproverRole: v }))}
              required
              error={Boolean(errors.awardApproverRole)}
              helperText={errors.awardApproverRole}
            />
          </Grid>
          <Grid size={12}>
            <FormControlLabel
              control={<Switch checked={form.requiresCoiDeclaration} onChange={set('requiresCoiDeclaration')} />}
              label="Members must declare conflicts of interest before seeing bids"
            />
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

RuleDialog.propTypes = {
  rule: committeeRuleShape,
  methodCode: PropTypes.string,
  title: PropTypes.string.isRequired,
  availableMethods: PropTypes.arrayOf(methodShape), // non-null = choosing a method for a new rule
  onClose: PropTypes.func.isRequired,
};

function RemoveDialog({ methodCode, name, onClose }) {
  const del = useDeleteCommitteeRule();
  return (
    <Dialog open onClose={onClose}>
      <DialogTitle>Remove the rule for {name}?</DialogTitle>
      <DialogContent>
        <DialogContentText>{name} will then follow the default committee rule.</DialogContentText>
        {del.error && <Alert severity="error" sx={{ mt: 2 }}>{del.error.message}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button color="error" loading={del.isPending} onClick={() => del.mutate(methodCode, { onSuccess: onClose })}>
          Remove
        </Button>
      </DialogActions>
    </Dialog>
  );
}

RemoveDialog.propTypes = {
  methodCode: PropTypes.string.isRequired,
  name: PropTypes.string.isRequired,
  onClose: PropTypes.func.isRequired,
};

export default function CommitteesTab({ rules, methods }) {
  const [dialog, setDialog] = useState(null);
  const methodName = (code) => methods.find((m) => m.code === code)?.name ?? code;
  const defaultRule = rules.find((r) => r.methodCode == null);
  const methodRules = rules.filter((r) => r.methodCode != null);
  const withoutRule = methods.filter((m) => !methodRules.some((r) => r.methodCode === m.code));

  const card = (rule, name) => (
    <Grid key={rule.id} size={{ xs: 12, md: 6, lg: 4 }}>
      <Card variant="outlined" sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
        <CardContent sx={{ flexGrow: 1 }}>
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{name}</Typography>
            {rule.methodCode == null && <Chip size="small" label="Default" />}
          </Stack>
          <Row label="Members">
            {rule.minMembers === rule.maxMembers ? rule.minMembers : `${rule.minMembers}–${rule.maxMembers}`}
          </Row>
          <Row label="Quorum">{rule.quorum}</Row>
          <Row label="Bid opening">
            {rule.twoEnvelope ? 'Technical, then financial' : 'Together'}
          </Row>
          {rule.technicalPassMarkPct != null && <Row label="Technical pass mark">{rule.technicalPassMarkPct}%</Row>}
          <Row label="Conflict-of-interest declaration">{rule.requiresCoiDeclaration ? 'Required' : 'Not required'}</Row>
          <Row label="Report signed by">{signatoriesLabel(rule.reportSignatories)}</Row>
          <Row label="Report reviewed by">{roleLabel(rule.reportReviewerRole)}</Row>
          <Row label="Award approved by">{roleLabel(rule.awardApproverRole)}</Row>
        </CardContent>
        <CardActions>
          <Button size="small" onClick={() => setDialog({ type: 'edit', rule, name })}>Edit</Button>
          {rule.methodCode != null && (
            <Button size="small" color="error" onClick={() => setDialog({ type: 'remove', rule, name })}>
              Remove
            </Button>
          )}
        </CardActions>
      </Card>
    </Grid>
  );

  return (
    <>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="body2" color="text.secondary">
          How evaluation committees are formed and who signs their report. Methods without their own rule use the default.
        </Typography>
        <Button
          startIcon={<AddIcon />}
          disabled={!withoutRule.length}
          onClick={() => setDialog({ type: 'new' })}
        >
          Add rule for a method
        </Button>
      </Stack>

      {!defaultRule && (
        <Alert
          severity="warning"
          sx={{ mb: 2 }}
          action={<Button color="inherit" size="small" onClick={() => setDialog({ type: 'edit', rule: null, name: 'Default rule' })}>Set up</Button>}
        >
          No default committee rule yet. Methods without their own rule cannot be evaluated.
        </Alert>
      )}

      <Grid container spacing={2}>
        {defaultRule && card(defaultRule, 'Default rule')}
        {methodRules.map((r) => card(r, methodName(r.methodCode)))}
      </Grid>

      {dialog?.type === 'edit' && (
        <RuleDialog
          rule={dialog.rule}
          methodCode={dialog.rule?.methodCode ?? null}
          title={`Edit: ${dialog.name}`}
          availableMethods={null}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.type === 'new' && (
        <RuleDialog
          rule={defaultRule}
          methodCode={null}
          title="Add committee rule for a method"
          availableMethods={withoutRule}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.type === 'remove' && (
        <RemoveDialog methodCode={dialog.rule.methodCode} name={dialog.name} onClose={() => setDialog(null)} />
      )}
    </>
  );
}

CommitteesTab.propTypes = {
  rules: PropTypes.arrayOf(committeeRuleShape).isRequired,
  methods: PropTypes.arrayOf(methodShape).isRequired,
};
