import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Box,
  Button,
  Chip,
  Grid,
  MenuItem,
  Stack,
  Step,
  StepLabel,
  Stepper,
  TextField,
  Typography,
} from '@mui/material';
import { useProcurementCheck } from '../../api/procurementSettings.js';
import { APPROVAL_STAGES, roleLabel, signatoriesLabel } from '../../domain/procurement.js';
import { toMinorUnits } from '../../utils/money.js';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import { methodShape } from './propTypes.js';

/** Tries the current settings on one purchase, so changes can be checked before they matter. */
export default function CheckTab({ methods }) {
  const [form, setForm] = useState({ stage: 'AWARD', method: '', amount: '' });
  const [amountError, setAmountError] = useState('');
  const [params, setParams] = useState(null);
  const { data, isFetching, error } = useProcurementCheck(params);
  const set = (f) => (e) => setForm((s) => ({ ...s, [f]: e.target.value }));
  const methodName = (code) => methods.find((m) => m.code === code)?.name ?? code;

  const submit = (e) => {
    e.preventDefault();
    const amountMinor = toMinorUnits(form.amount);
    if (Number.isNaN(amountMinor) || form.amount.trim() === '') {
      setAmountError('Enter an amount, e.g. 2500000');
      return;
    }
    setAmountError('');
    setParams({ stage: form.stage, method: form.method, amountMinor, currency: 'KES' });
  };

  return (
    <>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Enter a purchase to see which methods are allowed, who must approve it and which committee rule applies.
      </Typography>
      <Box component="form" onSubmit={submit}>
        <Grid container spacing={2} sx={{ alignItems: 'flex-start' }}>
          <Grid size={{ xs: 12, sm: 3 }}>
            <TextField select label="Decision" value={form.stage} onChange={set('stage')} fullWidth>
              {APPROVAL_STAGES.map((s) => (
                <MenuItem key={s.key} value={s.key}>{s.label}</MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField select label="Procurement method" value={form.method} onChange={set('method')} fullWidth>
              <MenuItem value="">Not chosen yet</MenuItem>
              {methods.map((m) => (
                <MenuItem key={m.code} value={m.code}>{m.name}</MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 3 }}>
            <TextField
              label="Amount (KES)"
              value={form.amount}
              onChange={set('amount')}
              error={Boolean(amountError)}
              helperText={amountError}
              inputMode="decimal"
              fullWidth
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 2 }}>
            <Button type="submit" variant="contained" size="large" fullWidth loading={isFetching}>
              Check
            </Button>
          </Grid>
        </Grid>
      </Box>

      <ErrorAlert error={error} title="Check failed" />

      {data && (
        <Stack spacing={3} sx={{ mt: 3 }}>
          {data.warnings.map((w) => (
            <Alert key={w} severity="warning">{w}</Alert>
          ))}

          <Box>
            <Typography variant="subtitle2" gutterBottom>Methods allowed for this amount</Typography>
            <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
              {data.allowedMethods.length ? (
                data.allowedMethods.map((code) => (
                  <Chip
                    key={code}
                    label={methodName(code)}
                    color={code === data.methodCode ? 'primary' : 'default'}
                    variant={code === data.methodCode ? 'filled' : 'outlined'}
                  />
                ))
              ) : (
                <Typography variant="body2" color="text.secondary">None</Typography>
              )}
            </Stack>
          </Box>

          <Box>
            <Typography variant="subtitle2" gutterBottom>Approval chain</Typography>
            {data.approvers.length ? (
              <Stepper orientation="vertical" activeStep={-1}>
                {data.approvers.map((a) => (
                  <Step key={a.step} expanded>
                    <StepLabel>{roleLabel(a.approverRole)}</StepLabel>
                  </Step>
                ))}
              </Stepper>
            ) : (
              <Typography variant="body2" color="text.secondary">No approver configured</Typography>
            )}
          </Box>

          {data.committeeRule && (
            <Box>
              <Typography variant="subtitle2" gutterBottom>
                Evaluation committee ({data.committeeRule.methodCode ? methodName(data.committeeRule.methodCode) : 'default rule'})
              </Typography>
              <Typography variant="body2">
                {data.committeeRule.minMembers}–{data.committeeRule.maxMembers} members, quorum {data.committeeRule.quorum}.{' '}
                {data.committeeRule.twoEnvelope
                  ? `Technical bids opened first${data.committeeRule.technicalPassMarkPct != null ? ` (pass mark ${data.committeeRule.technicalPassMarkPct}%)` : ''}, then financial.`
                  : 'Technical and financial bids opened together.'}{' '}
                Report signed by {signatoriesLabel(data.committeeRule.reportSignatories).toLowerCase()}
                {data.committeeRule.reportReviewerRole ? `, reviewed by ${roleLabel(data.committeeRule.reportReviewerRole).toLowerCase()}` : ''}
                , award approved by {roleLabel(data.committeeRule.awardApproverRole).toLowerCase()}.
              </Typography>
            </Box>
          )}
        </Stack>
      )}
    </>
  );
}

CheckTab.propTypes = {
  methods: PropTypes.arrayOf(methodShape).isRequired,
};
