import { useState } from 'react';
import PropTypes from 'prop-types';
import { useNavigate } from 'react-router';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from '@mui/material';
import { useCreateTender } from '../../api/tenders.js';
import { useProcurementCheck, useProcurementSettings } from '../../api/procurementSettings.js';
import { formatMoney } from '../../utils/money.js';
import { ReservedField } from './TenderDialogs.jsx';

/** Starts a tender from an approved requisition, offering only the methods allowed for its value. */
export default function CreateTenderDialog({ requisition, onClose }) {
  const navigate = useNavigate();
  const create = useCreateTender();
  const { data: settings } = useProcurementSettings();
  const { data: check } = useProcurementCheck({
    stage: 'AWARD',
    amountMinor: requisition.estimatedTotalMinor,
    currency: requisition.currency,
  });
  const allowed = (settings?.methods ?? []).filter((m) => check?.allowedMethods?.includes(m.code));
  const [method, setMethod] = useState(requisition.proposedMethodCode ?? '');
  const [title, setTitle] = useState(requisition.title);
  const [reserved, setReserved] = useState('');
  const chosen = allowed.find((m) => m.code === method);

  const submit = (e) => {
    e.preventDefault();
    create.mutate(
      { requisitionId: requisition.id, methodCode: method, title: title.trim() || null, reservedGroup: reserved || null },
      { onSuccess: (t) => navigate(`/tenders/${t.id}`) },
    );
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={submit}>
      <DialogTitle>Create tender from {requisition.number}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Estimated value {formatMoney(requisition.estimatedTotalMinor, requisition.currency)}. The bid period,
          envelopes and opening quorum come from the procurement settings for the method you choose.
        </DialogContentText>
        {create.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {create.error.message}
          </Alert>
        )}
        <Stack spacing={2}>
          <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required fullWidth />
          <TextField
            select
            label="Procurement method"
            value={method}
            onChange={(e) => setMethod(e.target.value)}
            required
            fullWidth
            helperText={
              chosen
                ? `Minimum bid period ${chosen.minBidPeriodDays} days${chosen.requiresPublicAdvert ? ', publicly advertised' : ', invited suppliers only'}`
                : 'Only methods allowed for this value are listed'
            }
          >
            {allowed.map((m) => (
              <MenuItem key={m.code} value={m.code}>
                {m.name}
              </MenuItem>
            ))}
          </TextField>
          <ReservedField value={reserved} onChange={setReserved} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" disabled={!method} loading={create.isPending}>
          Create draft tender
        </Button>
      </DialogActions>
    </Dialog>
  );
}

CreateTenderDialog.propTypes = {
  requisition: PropTypes.object.isRequired,
  onClose: PropTypes.func.isRequired,
};
