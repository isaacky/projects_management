import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  TextField,
} from '@mui/material';
import { useCreateProject } from '../api/projects.js';
import { toMinorUnits } from '../utils/money.js';

const EMPTY = { code: '', name: '', sponsor: '', description: '', budget: '', currency: 'KES' };
const CURRENCIES = ['KES', 'USD', 'EUR'];

export default function NewProjectDialog({ open, onClose, onCreated }) {
  const [form, setForm] = useState(EMPTY);
  const [localErrors, setLocalErrors] = useState({});
  const create = useCreateProject();
  const serverErrors = create.error?.fieldErrors ?? {};

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  const close = () => {
    setForm(EMPTY);
    setLocalErrors({});
    create.reset();
    onClose();
  };

  const submit = (e) => {
    e.preventDefault();
    const budgetMinor = toMinorUnits(form.budget);
    const errors = {};
    if (!form.code.trim()) errors.code = 'Required';
    if (!form.name.trim()) errors.name = 'Required';
    if (Number.isNaN(budgetMinor)) errors.budget = 'Use digits, e.g. 1,250,000.00';
    setLocalErrors(errors);
    if (Object.keys(errors).length) return;

    create.mutate(
      {
        code: form.code.trim(),
        name: form.name.trim(),
        sponsor: form.sponsor.trim() || null,
        description: form.description.trim() || null,
        budgetMinor,
        currency: form.currency,
      },
      {
        onSuccess: (project) => {
          close();
          onCreated(project);
        },
      },
    );
  };

  const err = (field, serverField = field) => localErrors[field] || serverErrors[serverField];

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="sm" component="form" onSubmit={submit}>
      <DialogTitle>New project</DialogTitle>
      <DialogContent>
        {create.error && !Object.keys(serverErrors).length && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {create.error.message}
          </Alert>
        )}
        <Grid container spacing={2} sx={{ mt: 0.5 }}>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField
              label="Code"
              value={form.code}
              onChange={set('code')}
              error={Boolean(err('code'))}
              helperText={err('code') || 'e.g. NET-2026-010'}
              required
              fullWidth
              autoFocus
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 8 }}>
            <TextField
              label="Name"
              value={form.name}
              onChange={set('name')}
              error={Boolean(err('name'))}
              helperText={err('name')}
              required
              fullWidth
            />
          </Grid>
          <Grid size={12}>
            <TextField label="Sponsor" value={form.sponsor} onChange={set('sponsor')} fullWidth />
          </Grid>
          <Grid size={{ xs: 12, sm: 8 }}>
            <TextField
              label="Approved budget"
              value={form.budget}
              onChange={set('budget')}
              error={Boolean(err('budget', 'budgetMinor'))}
              helperText={err('budget', 'budgetMinor')}
              inputMode="decimal"
              fullWidth
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField select label="Currency" value={form.currency} onChange={set('currency')} fullWidth>
              {CURRENCIES.map((c) => (
                <MenuItem key={c} value={c}>
                  {c}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={12}>
            <TextField
              label="Description"
              value={form.description}
              onChange={set('description')}
              multiline
              minRows={3}
              fullWidth
            />
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={close}>Cancel</Button>
        <Button type="submit" variant="contained" loading={create.isPending}>
          Create project
        </Button>
      </DialogActions>
    </Dialog>
  );
}

NewProjectDialog.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onCreated: PropTypes.func.isRequired,
};
