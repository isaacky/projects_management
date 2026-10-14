import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Autocomplete,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  FormGroup,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import FilePicker, { filesTooBig } from './FilePicker.jsx';
import {
  useAddScans,
  useEditTender,
  useReadOut,
  useRecordBid,
  useTenderAction,
  useWithdrawBid,
} from '../../api/tenders.js';
import { useSuppliers } from '../../api/suppliers.js';
import { useMe, useUsers } from '../../api/me.js';
import { ENVELOPE_LABEL, formatDateTime, fromLocalInput, toLocalInput } from '../../domain/tenders.js';
import { minorToInput, inputToMinor } from '../../utils/amountField.js';
import { RESERVED_GROUP, eligibleFor } from '../../domain/evaluation.js';
import { formatMoney } from '../../utils/money.js';

function ErrorBox({ error }) {
  return error ? (
    <Alert severity="error" sx={{ mb: 2 }}>
      {error.message}
    </Alert>
  ) : null;
}

ErrorBox.propTypes = { error: PropTypes.instanceOf(Error) };

const tenderShape = PropTypes.object.isRequired;

// ------------------------------------------------------------------- reservation
export function ReservedField({ value, onChange }) {
  return (
    <TextField
      select
      label="Reserved for"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      fullWidth
      helperText="AGPO reservation: only suppliers with a valid certificate for the group may be invited or bid"
    >
      <MenuItem value="">Not reserved (open to all eligible suppliers)</MenuItem>
      {Object.entries(RESERVED_GROUP).map(([code, label]) => (
        <MenuItem key={code} value={code}>
          {label}
        </MenuItem>
      ))}
    </TextField>
  );
}

ReservedField.propTypes = { value: PropTypes.string.isRequired, onChange: PropTypes.func.isRequired };

// ------------------------------------------------------------------- edit
export function EditTenderDialog({ tender, onClose }) {
  const [title, setTitle] = useState(tender.title);
  const [description, setDescription] = useState(tender.description ?? '');
  const [closing, setClosing] = useState(toLocalInput(tender.closingAt));
  const [reserved, setReserved] = useState(tender.reservedGroup ?? '');
  const edit = useEditTender(tender.id);
  const earliest = new Date(tender.earliestClosingIfPublishedNow);

  const submit = (e) => {
    e.preventDefault();
    edit.mutate(
      {
        title: title.trim(),
        description: description.trim() || null,
        closingAt: fromLocalInput(closing),
        reservedGroup: reserved || null,
      },
      { onSuccess: onClose },
    );
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={submit}>
      <DialogTitle>Edit tender</DialogTitle>
      <DialogContent>
        <ErrorBox error={edit.error} />
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required fullWidth />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            multiline
            minRows={3}
            fullWidth
          />
          <TextField
            label="Closing date and time"
            type="datetime-local"
            value={closing}
            onChange={(e) => setClosing(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
            helperText={`At least ${tender.minBidPeriodDays} days after publication: if published now, ${formatDateTime(earliest.toISOString())} or later`}
            fullWidth
          />
          <ReservedField value={reserved} onChange={setReserved} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" loading={edit.isPending}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

EditTenderDialog.propTypes = { tender: tenderShape, onClose: PropTypes.func.isRequired };

// ------------------------------------------------------------------- invite
export function InviteDialog({ tender, onClose }) {
  const { data: suppliers } = useSuppliers();
  const [picked, setPicked] = useState([]);
  const run = useTenderAction(tender.id);
  const invited = new Set(tender.invitedSuppliers.map((s) => s.id));
  const options = (suppliers ?? []).filter(
    (s) => s.status === 'ACTIVE' && !invited.has(s.id) && eligibleFor(tender.reservedGroup, s),
  );

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Invite suppliers</DialogTitle>
      <DialogContent>
        <ErrorBox error={run.error} />
        <Autocomplete
          multiple
          options={options}
          value={picked}
          onChange={(_e, v) => setPicked(v)}
          getOptionLabel={(s) => `${s.name} (${s.kraPin})`}
          isOptionEqualToValue={(a, b) => a.id === b.id}
          renderInput={(params) => <TextField {...params} label="Suppliers" sx={{ mt: 1 }} />}
        />
        <Typography variant="caption" color="text.secondary">
          Only active suppliers are listed{tender.reservedGroup ? ', with a valid AGPO certificate for the reserved group' : ''}.
        </Typography>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={!picked.length}
          loading={run.isPending}
          onClick={() =>
            run.mutate(
              { action: 'invitations', body: { supplierIds: picked.map((s) => s.id) } },
              { onSuccess: onClose },
            )
          }
        >
          Invite {picked.length || ''}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

InviteDialog.propTypes = { tender: tenderShape, onClose: PropTypes.func.isRequired };

// ------------------------------------------------------------------- record bid
export function RecordBidDialog({ tender, onClose }) {
  const { data: suppliers } = useSuppliers();
  const [supplier, setSupplier] = useState(null);
  const [note, setNote] = useState('');
  const [files, setFiles] = useState({ single: [], technical: [], financial: [] });
  const record = useRecordBid(tender.id);
  const alreadyBid = new Set(tender.bids.filter((b) => b.status === 'RECEIVED').map((b) => b.supplier.id));
  const invited = new Set(tender.invitedSuppliers.map((s) => s.id));
  const options = (suppliers ?? []).filter(
    (s) =>
      s.status === 'ACTIVE' &&
      !alreadyBid.has(s.id) &&
      (!tender.invitedOnly || invited.has(s.id)) &&
      eligibleFor(tender.reservedGroup, s),
  );
  const envelopes = tender.twoEnvelope ? ['technical', 'financial'] : ['single'];
  const total = envelopes.reduce((n, e) => n + files[e].length, 0);
  const tooBig = filesTooBig(envelopes.map((e) => files[e]));

  const submit = (e) => {
    e.preventDefault();
    record.mutate(
      { supplierId: supplier.id, deliveryNote: note.trim(), files },
      { onSuccess: onClose },
    );
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md" component="form" onSubmit={submit}>
      <DialogTitle>Record a bid</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Files are encrypted as they are uploaded and nobody can read them until the opening. The bid counts only
          if the upload finishes before {formatDateTime(tender.closingAt)}. For a paper bid, describe what was
          received; its pages are scanned in after the opening.
        </DialogContentText>
        <ErrorBox error={record.error} />
        <Stack spacing={2}>
          <Autocomplete
            options={options}
            value={supplier}
            onChange={(_e, v) => setSupplier(v)}
            getOptionLabel={(s) => `${s.name} (${s.kraPin})`}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            renderInput={(params) => (
              <TextField
                {...params}
                label="Supplier"
                required
                helperText={tender.invitedOnly ? 'Only invited suppliers can bid on this tender' : undefined}
              />
            )}
          />
          <TextField
            label="Delivery note"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="e.g. Received by email from bids@supplier.co.ke; or 2 sealed envelopes, hand delivered"
            fullWidth
          />
          {envelopes.map((env) => (
            <FilePicker
              key={env}
              label={`${ENVELOPE_LABEL[env.toUpperCase()]} files`}
              files={files[env]}
              onChange={(list) => setFiles((f) => ({ ...f, [env]: list }))}
              helperText={env === 'financial' ? 'Sealed until the financial opening' : 'Sealed until the opening'}
            />
          ))}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          type="submit"
          variant="contained"
          disabled={!supplier || tooBig || (!total && !note.trim())}
          loading={record.isPending}
        >
          Seal and record bid
        </Button>
      </DialogActions>
    </Dialog>
  );
}

RecordBidDialog.propTypes = { tender: tenderShape, onClose: PropTypes.func.isRequired };

// ------------------------------------------------------------------- open bids
export function OpenBidsDialog({ tender, onClose }) {
  const { data: users } = useUsers();
  const { data: me } = useMe();
  const [attendees, setAttendees] = useState(() => new Set(me ? [me.id] : []));
  const [venue, setVenue] = useState('');
  const [notes, setNotes] = useState('');
  const run = useTenderAction(tender.id);
  const envelope = tender.nextEnvelopeToOpen;
  const toggle = (id) =>
    setAttendees((s) => {
      const next = new Set(s);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  const liveBids = tender.bids.filter((b) => b.status === 'RECEIVED').length;
  const label = envelope === 'SINGLE' ? 'bids' : `${ENVELOPE_LABEL[envelope].toLowerCase()} envelopes`;

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Open {label}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          {liveBids} bid{liveBids === 1 ? '' : 's'} will be unsealed. Record everyone present; at least{' '}
          {tender.openingQuorum} people are required. This cannot be undone.
        </DialogContentText>
        <ErrorBox error={run.error} />
        <Typography variant="subtitle2">Present ({attendees.size} of at least {tender.openingQuorum})</Typography>
        <FormGroup sx={{ mb: 2 }}>
          {(users ?? []).map((u) => (
            <FormControlLabel
              key={u.id}
              control={<Checkbox checked={attendees.has(u.id)} onChange={() => toggle(u.id)} />}
              label={u.displayName}
            />
          ))}
        </FormGroup>
        <Grid container spacing={2}>
          <Grid size={12}>
            <TextField label="Venue" value={venue} onChange={(e) => setVenue(e.target.value)} fullWidth />
          </Grid>
          <Grid size={12}>
            <TextField
              label="Notes"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="e.g. Bidders' representatives present"
              multiline
              minRows={2}
              fullWidth
            />
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          color="warning"
          disabled={attendees.size < tender.openingQuorum}
          loading={run.isPending}
          onClick={() =>
            run.mutate(
              {
                action: 'openings',
                body: { envelope, attendeeIds: [...attendees], venue: venue || null, notes: notes || null },
              },
              { onSuccess: onClose },
            )
          }
        >
          Open {label}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

OpenBidsDialog.propTypes = { tender: tenderShape, onClose: PropTypes.func.isRequired };

// ------------------------------------------------------------------- read-out price
export function ReadOutDialog({ tender, bid, onClose }) {
  const [price, setPrice] = useState(minorToInput(bid.readOutPriceMinor));
  const [note, setNote] = useState(bid.readOutNote ?? '');
  const [error, setError] = useState('');
  const run = useReadOut(tender.id);

  const submit = (e) => {
    e.preventDefault();
    const minor = inputToMinor(price);
    if (Number.isNaN(minor)) {
      setError('Use digits, e.g. 95000000 or 95,000,000.00');
      return;
    }
    run.mutate({ bidId: bid.id, priceMinor: minor, note: note.trim() || null }, { onSuccess: onClose });
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs" component="form" onSubmit={submit}>
      <DialogTitle>Read-out price</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          {bid.supplier.name}, {bid.receiptNo}. Record the total bid price as read out at the opening.
        </DialogContentText>
        <ErrorBox error={run.error} />
        <Stack spacing={2}>
          <TextField
            label={`Price (${tender.currency})`}
            value={price}
            onChange={(e) => setPrice(e.target.value)}
            error={Boolean(error)}
            helperText={error || (price ? formatMoney(inputToMinor(price), tender.currency) : ' ')}
            inputMode="decimal"
            autoFocus
            fullWidth
          />
          <TextField
            label="Note"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="e.g. Discount of 2% offered; bid security attached"
            fullWidth
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" loading={run.isPending}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

ReadOutDialog.propTypes = { tender: tenderShape, bid: PropTypes.object.isRequired, onClose: PropTypes.func.isRequired };

// ------------------------------------------------------------------- scans
export function ScansDialog({ tender, bid, onClose }) {
  const openEnvelopes = tender.twoEnvelope
    ? ['TECHNICAL', ...(tender.status === 'OPENED' ? ['FINANCIAL'] : [])]
    : ['SINGLE'];
  const [envelope, setEnvelope] = useState(openEnvelopes[0]);
  const [files, setFiles] = useState([]);
  const run = useAddScans(tender.id);

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Add scans to {bid.receiptNo}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          For paper bids: upload the scanned pages of an envelope that has been opened. They are marked as added
          after the opening.
        </DialogContentText>
        <ErrorBox error={run.error} />
        <Stack spacing={2}>
          {openEnvelopes.length > 1 && (
            <TextField select label="Envelope" value={envelope} onChange={(e) => setEnvelope(e.target.value)} fullWidth>
              {openEnvelopes.map((e) => (
                <MenuItem key={e} value={e}>
                  {ENVELOPE_LABEL[e]}
                </MenuItem>
              ))}
            </TextField>
          )}
          <FilePicker label="Choose scans" files={files} onChange={setFiles} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={!files.length || filesTooBig([files])}
          loading={run.isPending}
          onClick={() => run.mutate({ bidId: bid.id, envelope, files }, { onSuccess: onClose })}
        >
          Upload
        </Button>
      </DialogActions>
    </Dialog>
  );
}

ScansDialog.propTypes = { tender: tenderShape, bid: PropTypes.object.isRequired, onClose: PropTypes.func.isRequired };

// ------------------------------------------------------------------- reason-based actions
const REASON_ACTIONS = {
  publish: {
    title: 'Publish tender?',
    text: 'Bids can be recorded from now until the closing time. Title, description and closing time can no longer be edited (the closing time can only be extended).',
    button: 'Publish',
    color: 'primary',
    reason: false,
  },
  cancel: {
    title: 'Cancel tender?',
    text: 'The tender is closed without opening any bids. The requisition can then be tendered again.',
    button: 'Cancel tender',
    color: 'error',
    reason: true,
  },
  extend: {
    title: 'Extend closing time',
    text: 'Use this after an addendum or clarification. The closing time can only move later.',
    button: 'Extend',
    color: 'primary',
    reason: true,
  },
  withdraw: {
    title: 'Withdraw bid?',
    text: "Record the supplier's written request to withdraw. The sealed files are kept for the record. The supplier can then submit a new bid before the closing time.",
    button: 'Withdraw bid',
    color: 'error',
    reason: true,
  },
};

export function ReasonDialog({ tender, action, bid, onClose }) {
  const cfg = REASON_ACTIONS[action];
  const [reason, setReason] = useState('');
  const [closing, setClosing] = useState(toLocalInput(tender.closingAt));
  const tenderAction = useTenderAction(tender.id);
  const withdraw = useWithdrawBid(tender.id);
  const run = action === 'withdraw' ? withdraw : tenderAction;

  const go = () => {
    if (action === 'withdraw') {
      withdraw.mutate({ bidId: bid.id, reason: reason.trim() }, { onSuccess: onClose });
    } else if (action === 'extend') {
      tenderAction.mutate(
        { action: 'extend', body: { closingAt: fromLocalInput(closing), reason: reason.trim() } },
        { onSuccess: onClose },
      );
    } else {
      tenderAction.mutate(
        { action, body: cfg.reason ? { reason: reason.trim() } : undefined },
        { onSuccess: onClose },
      );
    }
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{cfg.title}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>{cfg.text}</DialogContentText>
        <ErrorBox error={run.error} />
        <Stack spacing={2}>
          {action === 'extend' && (
            <TextField
              label="New closing date and time"
              type="datetime-local"
              value={closing}
              onChange={(e) => setClosing(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
              fullWidth
            />
          )}
          {cfg.reason && (
            <TextField
              label="Reason (required)"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              multiline
              minRows={2}
              autoFocus
              fullWidth
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Back</Button>
        <Button
          variant="contained"
          color={cfg.color}
          disabled={cfg.reason && !reason.trim()}
          loading={run.isPending}
          onClick={go}
        >
          {cfg.button}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

ReasonDialog.propTypes = {
  tender: tenderShape,
  action: PropTypes.oneOf(Object.keys(REASON_ACTIONS)).isRequired,
  bid: PropTypes.object,
  onClose: PropTypes.func.isRequired,
};
