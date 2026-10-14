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
import { useSaveSupplier } from '../../api/suppliers.js';

const EMPTY = {
  name: '',
  kraPin: '',
  registrationNo: '',
  email: '',
  phone: '',
  address: '',
  categories: '',
  agpoCategory: '',
  agpoCertificateNo: '',
  agpoExpiry: '',
  status: 'ACTIVE',
};

export default function SupplierDialog({ supplier, onClose }) {
  const [form, setForm] = useState(() =>
    supplier
      ? Object.fromEntries(Object.keys(EMPTY).map((k) => [k, supplier[k] ?? '']))
      : EMPTY,
  );
  const save = useSaveSupplier();
  const errors = save.error?.fieldErrors ?? {};
  const set = (f) => (e) => setForm((s) => ({ ...s, [f]: e.target.value }));

  const submit = (e) => {
    e.preventDefault();
    const body = Object.fromEntries(Object.entries(form).map(([k, v]) => [k, v === '' ? null : v]));
    save.mutate({ id: supplier?.id, ...body, kraPin: form.kraPin.trim().toUpperCase() }, { onSuccess: onClose });
  };

  const field = (name, label, props = {}) => (
    <TextField
      label={label}
      value={form[name]}
      onChange={set(name)}
      error={Boolean(errors[name])}
      helperText={errors[name] ?? props.helperText}
      fullWidth
      {...props}
    />
  );

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md" component="form" onSubmit={submit}>
      <DialogTitle>{supplier ? `Edit ${supplier.name}` : 'Add supplier'}</DialogTitle>
      <DialogContent>
        {save.error && !Object.keys(errors).length && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {save.error.message}
          </Alert>
        )}
        <Grid container spacing={2} sx={{ mt: 0.5 }}>
          <Grid size={{ xs: 12, md: 8 }}>{field('name', 'Supplier name', { required: true })}</Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            {field('kraPin', 'KRA PIN', {
              required: true,
              disabled: Boolean(supplier),
              helperText: supplier ? 'Identifies the supplier; cannot change' : 'e.g. P051234567X',
            })}
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>{field('registrationNo', 'Registration no.')}</Grid>
          <Grid size={{ xs: 12, md: 4 }}>{field('email', 'Email', { type: 'email' })}</Grid>
          <Grid size={{ xs: 12, md: 4 }}>{field('phone', 'Phone')}</Grid>
          <Grid size={12}>{field('address', 'Address')}</Grid>
          <Grid size={12}>{field('categories', 'Supply categories', { helperText: 'e.g. Transformers; Cables' })}</Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            {field('agpoCategory', 'AGPO category', {
              select: true,
              children: [
                <MenuItem key="" value="">None</MenuItem>,
                <MenuItem key="YOUTH" value="YOUTH">Youth</MenuItem>,
                <MenuItem key="WOMEN" value="WOMEN">Women</MenuItem>,
                <MenuItem key="PWD" value="PWD">Persons with disabilities</MenuItem>,
              ],
            })}
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>{field('agpoCertificateNo', 'AGPO certificate no.')}</Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            {field('agpoExpiry', 'AGPO expiry', { type: 'date', slotProps: { inputLabel: { shrink: true } } })}
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            {field('status', 'Status', {
              select: true,
              children: [
                <MenuItem key="ACTIVE" value="ACTIVE">Active</MenuItem>,
                <MenuItem key="SUSPENDED" value="SUSPENDED">Suspended</MenuItem>,
                <MenuItem key="DEBARRED" value="DEBARRED">Debarred</MenuItem>,
              ],
            })}
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" loading={save.isPending}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

SupplierDialog.propTypes = {
  supplier: PropTypes.object,
  onClose: PropTypes.func.isRequired,
};
