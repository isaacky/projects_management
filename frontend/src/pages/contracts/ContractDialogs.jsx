import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  Grid,
  IconButton,
  InputAdornment,
  MenuItem,
  Radio,
  RadioGroup,
  Rating,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import AddOutlined from '@mui/icons-material/AddOutlined';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import FilePicker, { filesTooBig } from '../tenders/FilePicker.jsx';
import {
  useAcceptMilestone,
  useAddSecurity,
  useContractAction,
  useDecideVariation,
  useEditContract,
  useInvoiceAction,
  useRecordInvoice,
  useSaveSchedule,
  useSecurityAction,
  useUploadContractDocument,
} from '../../api/contracts.js';
import { useUsers } from '../../api/me.js';
import {
  DOCUMENT_KIND,
  REVIEW_STATUS,
  SECURITY_FORM,
  SECURITY_KIND,
  bpToPercent,
  formatDate,
  percentToBp,
  retention,
  todayInput,
} from '../../domain/contracts.js';
import { inputToMinor, minorToInput } from '../../utils/amountField.js';
import { formatMoney } from '../../utils/money.js';

const contractShape = PropTypes.object.isRequired;

function ErrorBox({ error }) {
  return error ? (
    <Alert severity="error" sx={{ mb: 2 }}>
      {error.message}
    </Alert>
  ) : null;
}

ErrorBox.propTypes = { error: PropTypes.instanceOf(Error) };

/** A dialog with a form, error box and Cancel / submit buttons. */
function FormDialog({ title, intro, error, onClose, onSubmit, submitLabel, disabled, loading, color, maxWidth, children }) {
  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth={maxWidth ?? 'sm'}
      component="form"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit();
      }}
    >
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        {intro && <DialogContentText sx={{ mb: 2 }}>{intro}</DialogContentText>}
        <ErrorBox error={error} />
        <Stack spacing={2} sx={{ mt: intro ? 0 : 1 }}>
          {children}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" color={color ?? 'primary'} disabled={disabled} loading={loading}>
          {submitLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

FormDialog.propTypes = {
  title: PropTypes.node.isRequired,
  intro: PropTypes.node,
  error: PropTypes.instanceOf(Error),
  onClose: PropTypes.func.isRequired,
  onSubmit: PropTypes.func.isRequired,
  submitLabel: PropTypes.string.isRequired,
  disabled: PropTypes.bool,
  loading: PropTypes.bool,
  color: PropTypes.string,
  maxWidth: PropTypes.string,
  children: PropTypes.node,
};

function MoneyField({ label, value, onChange, currency, required, helperText }) {
  const minor = inputToMinor(value);
  return (
    <TextField
      label={label}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      required={required}
      error={Number.isNaN(minor)}
      helperText={Number.isNaN(minor) ? 'Enter an amount such as 1,250,000.50' : helperText}
      slotProps={{
        htmlInput: { inputMode: 'decimal' },
        input: { startAdornment: <InputAdornment position="start">{currency}</InputAdornment> },
      }}
      fullWidth
    />
  );
}

MoneyField.propTypes = {
  label: PropTypes.string.isRequired,
  value: PropTypes.string.isRequired,
  onChange: PropTypes.func.isRequired,
  currency: PropTypes.string.isRequired,
  required: PropTypes.bool,
  helperText: PropTypes.node,
};

function DateField({ label, value, onChange, required, helperText }) {
  return (
    <TextField
      type="date"
      label={label}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      required={required}
      helperText={helperText}
      slotProps={{ inputLabel: { shrink: true } }}
      fullWidth
    />
  );
}

DateField.propTypes = {
  label: PropTypes.string.isRequired,
  value: PropTypes.string.isRequired,
  onChange: PropTypes.func.isRequired,
  required: PropTypes.bool,
  helperText: PropTypes.node,
};

// ------------------------------------------------------------------- draft details
export function EditContractDialog({ contract: c, onClose }) {
  const { data: users } = useUsers();
  const [title, setTitle] = useState(c.title);
  const [start, setStart] = useState(c.startDate ?? '');
  const [end, setEnd] = useState(c.endDate ?? '');
  const [retentionPct, setRetentionPct] = useState(String(c.retentionBp / 100));
  const [dlp, setDlp] = useState(String(c.defectsLiabilityDays));
  const [security, setSecurity] = useState(minorToInput(c.performanceSecurityMinor));
  const [manager, setManager] = useState(c.contractManager?.id ?? '');
  const edit = useEditContract(c.id);
  const bp = percentToBp(retentionPct);
  const securityMinor = inputToMinor(security) ?? 0;
  const invalid = !title.trim() || Number.isNaN(bp) || bp > 2000 || Number.isNaN(securityMinor) || !/^\d+$/.test(dlp);

  return (
    <FormDialog
      title="Contract details"
      intro="Take these from the tender document and the letter of award."
      error={edit.error}
      onClose={onClose}
      submitLabel="Save"
      disabled={invalid}
      loading={edit.isPending}
      onSubmit={() =>
        edit.mutate(
          {
            title: title.trim(),
            startDate: start || null,
            endDate: end || null,
            retentionBp: bp,
            defectsLiabilityDays: Number(dlp),
            performanceSecurityMinor: securityMinor,
            contractManagerId: manager || null,
          },
          { onSuccess: onClose },
        )
      }
    >
      <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required fullWidth />
      <Grid container spacing={2}>
        <Grid size={6}>
          <DateField label="Start date" value={start} onChange={setStart} />
        </Grid>
        <Grid size={6}>
          <DateField label="End date" value={end} onChange={setEnd} />
        </Grid>
        <Grid size={6}>
          <TextField
            label="Retention"
            value={retentionPct}
            onChange={(e) => setRetentionPct(e.target.value)}
            error={Number.isNaN(bp) || bp > 2000}
            helperText="Withheld from each payment (0 for most supplies)"
            slotProps={{ input: { endAdornment: <InputAdornment position="end">%</InputAdornment> } }}
            fullWidth
          />
        </Grid>
        <Grid size={6}>
          <TextField
            label="Defects liability period"
            value={dlp}
            onChange={(e) => setDlp(e.target.value.replace(/\D/g, ''))}
            helperText="Days after completion before retention is released"
            slotProps={{ input: { endAdornment: <InputAdornment position="end">days</InputAdornment> } }}
            fullWidth
          />
        </Grid>
      </Grid>
      <MoneyField
        label="Performance security required"
        value={security}
        onChange={setSecurity}
        currency={c.currency}
        helperText={`Contract value ${formatMoney(c.currentValueMinor, c.currency)}; 0 if none is required`}
      />
      <TextField
        select
        label="Contract manager"
        value={manager}
        onChange={(e) => setManager(e.target.value)}
        helperText="Accepts deliverables and certifies invoices"
        fullWidth
      >
        <MenuItem value="">Not assigned</MenuItem>
        {(users ?? []).map((u) => (
          <MenuItem key={u.id} value={u.id}>
            {u.displayName}
          </MenuItem>
        ))}
      </TextField>
    </FormDialog>
  );
}

EditContractDialog.propTypes = { contract: contractShape, onClose: PropTypes.func.isRequired };

// ------------------------------------------------------------------- payment schedule
let key = 0;
const row = (m) => ({
  key: `m${(key += 1)}`,
  id: m.id ?? null,
  title: m.title ?? '',
  dueDate: m.dueDate ?? '',
  amount: m.amountMinor == null ? '' : minorToInput(m.amountMinor),
});

export function ScheduleDialog({ contract: c, onClose }) {
  const fixed = c.milestones.filter((m) => m.status !== 'PLANNED');
  const [rows, setRows] = useState(() => {
    const planned = c.milestones.filter((m) => m.status === 'PLANNED').map(row);
    return planned.length || fixed.length ? planned : [row({})];
  });
  const save = useSaveSchedule(c.id);
  const fixedTotal = fixed.filter((m) => m.status === 'ACCEPTED').reduce((s, m) => s + m.amountMinor, 0);
  const amounts = rows.map((r) => inputToMinor(r.amount));
  const total = fixedTotal + amounts.reduce((s, a) => s + (Number.isNaN(a) || a == null ? 0 : a), 0);
  const invalid = rows.some((r, i) => !r.title.trim() || amounts[i] == null || Number.isNaN(amounts[i]));
  const update = (k, patch) => setRows((rs) => rs.map((r) => (r.key === k ? { ...r, ...patch } : r)));

  return (
    <FormDialog
      title="Payment schedule"
      intro={
        c.status === 'DRAFT'
          ? 'The deliverables and what each is worth. They must add up to the contract value before signing.'
          : 'Planned milestones can be changed; removed ones are cancelled. The schedule cannot exceed the contract value.'
      }
      error={save.error}
      onClose={onClose}
      submitLabel="Save schedule"
      disabled={invalid}
      loading={save.isPending}
      maxWidth="md"
      onSubmit={() =>
        save.mutate(
          rows.map((r, i) => ({ id: r.id, title: r.title.trim(), dueDate: r.dueDate || null, amountMinor: amounts[i] })),
          { onSuccess: onClose },
        )
      }
    >
      {fixed.map((m) => (
        <Typography key={m.id} variant="body2" color="text.secondary">
          {m.title} · {formatMoney(m.amountMinor, c.currency)} · {m.status.toLowerCase()} (not editable)
        </Typography>
      ))}
      {rows.map((r) => (
        <Stack key={r.key} direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: 'flex-start' }}>
          <TextField size="small" label="Deliverable" value={r.title} onChange={(e) => update(r.key, { title: e.target.value })} required sx={{ flex: 2 }} />
          <TextField
            size="small"
            type="date"
            label="Due"
            value={r.dueDate}
            onChange={(e) => update(r.key, { dueDate: e.target.value })}
            slotProps={{ inputLabel: { shrink: true } }}
            sx={{ width: 170 }}
          />
          <TextField
            size="small"
            label={`Amount (${c.currency})`}
            value={r.amount}
            onChange={(e) => update(r.key, { amount: e.target.value })}
            required
            slotProps={{ htmlInput: { inputMode: 'decimal' } }}
            sx={{ width: 180 }}
          />
          <IconButton aria-label="Remove" onClick={() => setRows((rs) => rs.filter((x) => x.key !== r.key))}>
            <DeleteOutline fontSize="small" />
          </IconButton>
        </Stack>
      ))}
      <Box>
        <Button size="small" startIcon={<AddOutlined />} onClick={() => setRows((rs) => [...rs, row({})])}>
          Add milestone
        </Button>
      </Box>
      <Alert severity={total === c.currentValueMinor ? 'success' : 'info'}>
        Schedule {formatMoney(total, c.currency)} of contract value {formatMoney(c.currentValueMinor, c.currency)}
      </Alert>
    </FormDialog>
  );
}

ScheduleDialog.propTypes = { contract: contractShape, onClose: PropTypes.func.isRequired };

// ------------------------------------------------------------------- securities and documents
export function SecurityDialog({ contract: c, onClose }) {
  const [kind, setKind] = useState('PERFORMANCE');
  const [formType, setFormType] = useState('BANK_GUARANTEE');
  const [amount, setAmount] = useState(minorToInput(c.performanceSecurityMinor || null));
  const [issuer, setIssuer] = useState('');
  const [reference, setReference] = useState('');
  const [expiry, setExpiry] = useState('');
  const [received, setReceived] = useState(todayInput());
  const [files, setFiles] = useState([]);
  const add = useAddSecurity(c.id);
  const minor = inputToMinor(amount);
  const invalid = !(minor > 0) || !issuer.trim() || !reference.trim() || !expiry || !received || filesTooBig([files]);

  return (
    <FormDialog
      title="Record a security"
      intro="Check the guarantee with the issuing bank or insurer before recording it."
      error={add.error}
      onClose={onClose}
      submitLabel="Record security"
      disabled={invalid}
      loading={add.isPending}
      onSubmit={() =>
        add.mutate(
          {
            kind,
            form: formType,
            amountMinor: minor,
            issuer: issuer.trim(),
            reference: reference.trim(),
            expiryDate: expiry,
            receivedOn: received,
            file: files[0],
          },
          { onSuccess: onClose },
        )
      }
    >
      <Grid container spacing={2}>
        <Grid size={6}>
          <TextField select label="Type" value={kind} onChange={(e) => setKind(e.target.value)} fullWidth>
            {Object.entries(SECURITY_KIND).map(([k, v]) => (
              <MenuItem key={k} value={k}>
                {v}
              </MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid size={6}>
          <TextField select label="Form" value={formType} onChange={(e) => setFormType(e.target.value)} fullWidth>
            {Object.entries(SECURITY_FORM).map(([k, v]) => (
              <MenuItem key={k} value={k}>
                {v}
              </MenuItem>
            ))}
          </TextField>
        </Grid>
      </Grid>
      <MoneyField label="Amount" value={amount} onChange={setAmount} currency={c.currency} required />
      <Grid container spacing={2}>
        <Grid size={6}>
          <TextField label="Issued by" value={issuer} onChange={(e) => setIssuer(e.target.value)} required fullWidth />
        </Grid>
        <Grid size={6}>
          <TextField label="Reference number" value={reference} onChange={(e) => setReference(e.target.value)} required fullWidth />
        </Grid>
        <Grid size={6}>
          <DateField label="Received on" value={received} onChange={setReceived} required />
        </Grid>
        <Grid size={6}>
          <DateField
            label="Expires on"
            value={expiry}
            onChange={setExpiry}
            required
            helperText={c.endDate ? `Should cover past the end date (${formatDate(c.endDate)})` : undefined}
          />
        </Grid>
      </Grid>
      <FilePicker label="Copy of the security (optional)" files={files} onChange={(f) => setFiles(f.slice(-1))} />
    </FormDialog>
  );
}

SecurityDialog.propTypes = { contract: contractShape, onClose: PropTypes.func.isRequired };

export function SecurityActionDialog({ contract: c, security: s, onClose }) {
  const [mode, setMode] = useState('extend');
  const [expiry, setExpiry] = useState('');
  const [note, setNote] = useState('');
  const run = useSecurityAction(c.id);
  const invalid = mode === 'extend' ? !expiry : !note.trim();

  return (
    <FormDialog
      title={`${SECURITY_KIND[s.kind]} ${s.reference}`}
      intro={`${formatMoney(s.amountMinor, c.currency)} from ${s.issuer}, expires ${formatDate(s.expiryDate)}.`}
      error={run.error}
      onClose={onClose}
      submitLabel="Record"
      disabled={invalid}
      loading={run.isPending}
      color={mode === 'CALLED' ? 'error' : 'primary'}
      onSubmit={() =>
        run.mutate(
          mode === 'extend'
            ? { securityId: s.id, action: 'extend', body: { expiryDate: expiry } }
            : { securityId: s.id, action: 'close', body: { outcome: mode, note: note.trim() } },
          { onSuccess: onClose },
        )
      }
    >
      <RadioGroup value={mode} onChange={(e) => setMode(e.target.value)}>
        <FormControlLabel value="extend" control={<Radio />} label="The issuer extended it" />
        <FormControlLabel value="RELEASED" control={<Radio />} label="Release it to the supplier" />
        <FormControlLabel value="CALLED" control={<Radio />} label="Call it (claim payment; accounting officer only)" />
      </RadioGroup>
      {mode === 'extend' ? (
        <DateField label="New expiry date" value={expiry} onChange={setExpiry} required />
      ) : (
        <TextField label="Reason" value={note} onChange={(e) => setNote(e.target.value)} required multiline minRows={2} fullWidth />
      )}
    </FormDialog>
  );
}

SecurityActionDialog.propTypes = {
  contract: contractShape,
  security: PropTypes.object.isRequired,
  onClose: PropTypes.func.isRequired,
};

export function DocumentDialog({ contract: c, defaultKind, onClose }) {
  const [kind, setKind] = useState(defaultKind ?? 'OTHER');
  const [files, setFiles] = useState([]);
  const upload = useUploadContractDocument(c.id);
  return (
    <FormDialog
      title="Add a document"
      error={upload.error}
      onClose={onClose}
      submitLabel="Upload"
      disabled={!files.length || filesTooBig([files])}
      loading={upload.isPending}
      onSubmit={() => upload.mutate({ kind, file: files[0] }, { onSuccess: onClose })}
    >
      <TextField select label="Document" value={kind} onChange={(e) => setKind(e.target.value)} fullWidth>
        {Object.entries(DOCUMENT_KIND).map(([k, v]) => (
          <MenuItem key={k} value={k}>
            {v}
          </MenuItem>
        ))}
      </TextField>
      <FilePicker label="File" files={files} onChange={(f) => setFiles(f.slice(-1))} />
    </FormDialog>
  );
}

DocumentDialog.propTypes = { contract: contractShape, defaultKind: PropTypes.string, onClose: PropTypes.func.isRequired };

// ------------------------------------------------------------------- signing, review, cancel, terminate
export function SignDialog({ contract: c, onClose }) {
  const [signedOn, setSignedOn] = useState(todayInput());
  const [supplier, setSupplier] = useState('');
  const [entity, setEntity] = useState('');
  const run = useContractAction(c.id);
  return (
    <FormDialog
      title={`Record signature of ${c.number}`}
      intro="Record the contract as signed by both parties. The signed copy must already be uploaded."
      error={run.error}
      onClose={onClose}
      submitLabel="Record signature"
      disabled={!signedOn || !supplier.trim() || !entity.trim()}
      loading={run.isPending}
      onSubmit={() =>
        run.mutate(
          { action: 'sign', body: { signedOn, supplierSignatory: supplier.trim(), entitySignatory: entity.trim() } },
          { onSuccess: onClose },
        )
      }
    >
      <DateField label="Signed on" value={signedOn} onChange={setSignedOn} required />
      <TextField label="Signed for the supplier by" value={supplier} onChange={(e) => setSupplier(e.target.value)} required helperText="Name and title" fullWidth />
      <TextField label="Signed for the organisation by" value={entity} onChange={(e) => setEntity(e.target.value)} required helperText="Name and title" fullWidth />
    </FormDialog>
  );
}

SignDialog.propTypes = { contract: contractShape, onClose: PropTypes.func.isRequired };

export function ReviewDialog({ contract: c, onClose }) {
  const [status, setStatus] = useState(c.reviewStatus === 'LODGED' ? 'DISMISSED' : 'LODGED');
  const [note, setNote] = useState('');
  const run = useContractAction(c.id);
  return (
    <FormDialog
      title="Request for review"
      intro="A request for review to the Review Board stops signature until it is decided. If it is upheld, this draft is cancelled."
      error={run.error}
      onClose={onClose}
      submitLabel="Record"
      disabled={!note.trim()}
      loading={run.isPending}
      color={status === 'UPHELD' ? 'error' : 'primary'}
      onSubmit={() => run.mutate({ action: 'review', body: { status, note: note.trim() } }, { onSuccess: onClose })}
    >
      <TextField select label="Status" value={status} onChange={(e) => setStatus(e.target.value)} fullWidth>
        {['LODGED', 'DISMISSED', 'UPHELD'].map((k) => (
          <MenuItem key={k} value={k}>
            {REVIEW_STATUS[k]}
          </MenuItem>
        ))}
      </TextField>
      <TextField label="Case reference and details" value={note} onChange={(e) => setNote(e.target.value)} required multiline minRows={2} fullWidth />
    </FormDialog>
  );
}

ReviewDialog.propTypes = { contract: contractShape, onClose: PropTypes.func.isRequired };

export function ReasonDialog({ contract: c, action, onClose }) {
  const [reason, setReason] = useState('');
  const run = useContractAction(c.id);
  const terminate = action === 'terminate';
  return (
    <FormDialog
      title={terminate ? `Terminate ${c.number}` : `Cancel draft ${c.number}`}
      intro={
        terminate
          ? 'Termination ends the contract. Decide separately whether to call the performance security.'
          : 'The draft is cancelled and the tender is free for a new contract.'
      }
      error={run.error}
      onClose={onClose}
      submitLabel={terminate ? 'Terminate contract' : 'Cancel draft'}
      color="error"
      disabled={!reason.trim()}
      loading={run.isPending}
      onSubmit={() => run.mutate({ action, body: { reason: reason.trim() } }, { onSuccess: onClose })}
    >
      <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} required multiline minRows={3} fullWidth />
    </FormDialog>
  );
}

ReasonDialog.propTypes = { contract: contractShape, action: PropTypes.oneOf(['cancel', 'terminate']).isRequired, onClose: PropTypes.func.isRequired };

// ------------------------------------------------------------------- delivery
export function AcceptMilestoneDialog({ contract: c, milestone: m, onClose }) {
  const [date, setDate] = useState(todayInput());
  const [note, setNote] = useState('');
  const [files, setFiles] = useState([]);
  const accept = useAcceptMilestone(c.id);
  return (
    <FormDialog
      title={`Accept: ${m.title}`}
      intro={`Worth ${formatMoney(m.amountMinor, c.currency)}. Accept only after inspection: payment for this milestone can then be certified.`}
      error={accept.error}
      onClose={onClose}
      submitLabel="Accept milestone"
      disabled={!date || !note.trim() || filesTooBig([files])}
      loading={accept.isPending}
      onSubmit={() =>
        accept.mutate({ milestoneId: m.id, acceptedOn: date, note: note.trim(), file: files[0] }, { onSuccess: onClose })
      }
    >
      <DateField label="Accepted on" value={date} onChange={setDate} required />
      <TextField
        label="Inspection and acceptance details"
        value={note}
        onChange={(e) => setNote(e.target.value)}
        required
        multiline
        minRows={3}
        helperText="For example the goods received note, inspection committee report or test results"
        fullWidth
      />
      <FilePicker label="Inspection report (optional)" files={files} onChange={(f) => setFiles(f.slice(-1))} />
    </FormDialog>
  );
}

AcceptMilestoneDialog.propTypes = { contract: contractShape, milestone: PropTypes.object.isRequired, onClose: PropTypes.func.isRequired };

export function InvoiceDialog({ contract: c, onClose }) {
  const [ref, setRef] = useState('');
  const [date, setDate] = useState(todayInput());
  const [milestoneId, setMilestoneId] = useState('');
  const [amount, setAmount] = useState('');
  const [files, setFiles] = useState([]);
  const record = useRecordInvoice(c.id);
  const minor = inputToMinor(amount);
  const options = c.milestones.filter((m) => m.status !== 'CANCELLED' && m.invoicedMinor < m.amountMinor);
  const chosen = c.milestones.find((m) => m.id === milestoneId);

  return (
    <FormDialog
      title="Record a supplier invoice"
      intro="A payment certificate number is assigned. The contract manager certifies it, then finance approves and pays it."
      error={record.error}
      onClose={onClose}
      submitLabel="Record invoice"
      disabled={!(minor > 0) || !date || filesTooBig([files])}
      loading={record.isPending}
      onSubmit={() =>
        record.mutate(
          { supplierRef: ref.trim(), invoiceDate: date, milestoneId: milestoneId || null, grossMinor: minor, file: files[0] },
          { onSuccess: onClose },
        )
      }
    >
      <Grid container spacing={2}>
        <Grid size={6}>
          <TextField label="Supplier's invoice number" value={ref} onChange={(e) => setRef(e.target.value)} fullWidth />
        </Grid>
        <Grid size={6}>
          <DateField label="Invoice date" value={date} onChange={setDate} required />
        </Grid>
      </Grid>
      <TextField select label="For milestone" value={milestoneId} onChange={(e) => setMilestoneId(e.target.value)} fullWidth>
        <MenuItem value="">Not tied to a milestone</MenuItem>
        {options.map((m) => (
          <MenuItem key={m.id} value={m.id}>
            {m.title} · {formatMoney(m.amountMinor - m.invoicedMinor, c.currency)} left
            {m.status === 'PLANNED' ? ' (not yet accepted)' : ''}
          </MenuItem>
        ))}
      </TextField>
      <MoneyField
        label="Invoice amount (gross)"
        value={amount}
        onChange={setAmount}
        currency={c.currency}
        required
        helperText={
          minor > 0 && c.retentionBp > 0
            ? `Retention ${bpToPercent(c.retentionBp)}: ${formatMoney(retention(minor, c.retentionBp), c.currency)} withheld; payable ${formatMoney(minor - retention(minor, c.retentionBp), c.currency)}`
            : chosen
              ? `Milestone value ${formatMoney(chosen.amountMinor, c.currency)}`
              : undefined
        }
      />
      <FilePicker label="Invoice scan (optional)" files={files} onChange={(f) => setFiles(f.slice(-1))} />
    </FormDialog>
  );
}

InvoiceDialog.propTypes = { contract: contractShape, onClose: PropTypes.func.isRequired };

const INVOICE_ACTION = {
  certify: { title: 'Certify payment certificate', submit: 'Certify' },
  approve: { title: 'Approve payment', submit: 'Approve for payment' },
  pay: { title: 'Record payment', submit: 'Record payment' },
  reject: { title: 'Reject invoice', submit: 'Reject' },
};

export function InvoiceActionDialog({ contract: c, invoice: i, action, onClose }) {
  const [note, setNote] = useState('');
  const [paidOn, setPaidOn] = useState(todayInput());
  const [ref, setRef] = useState('');
  const run = useInvoiceAction(c.id);
  const meta = INVOICE_ACTION[action];
  const body =
    action === 'certify'
      ? { note: note.trim() }
      : action === 'pay'
        ? { paidOn, paymentRef: ref.trim() }
        : action === 'reject'
          ? { reason: note.trim() }
          : {};
  const invalid = (action === 'certify' || action === 'reject') && !note.trim() ? true : action === 'pay' && (!ref.trim() || !paidOn);

  return (
    <FormDialog
      title={`${meta.title} ${i.certificateNo}`}
      intro={`${i.kind === 'RETENTION_RELEASE' ? 'Retention release' : `Invoice ${i.supplierRef ?? ''}`} · gross ${formatMoney(i.grossMinor, c.currency)} · retention ${formatMoney(i.retentionMinor, c.currency)} · payable ${formatMoney(i.netMinor, c.currency)}${i.milestoneTitle ? ` · ${i.milestoneTitle}` : ''}`}
      error={run.error}
      onClose={onClose}
      submitLabel={meta.submit}
      color={action === 'reject' ? 'error' : 'primary'}
      disabled={invalid}
      loading={run.isPending}
      onSubmit={() => run.mutate({ invoiceId: i.id, action, body }, { onSuccess: onClose })}
    >
      {action === 'certify' && (
        <TextField
          label="What was checked"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          required
          multiline
          minRows={3}
          helperText="Work done or goods received against the contract, quantities and prices"
          fullWidth
        />
      )}
      {action === 'approve' && (
        <Typography variant="body2">
          Certified by {i.certifiedBy?.name}: {i.certificateNote}
        </Typography>
      )}
      {action === 'pay' && (
        <>
          <DateField label="Paid on" value={paidOn} onChange={setPaidOn} required />
          <TextField label="Payment reference" value={ref} onChange={(e) => setRef(e.target.value)} required helperText="For example the EFT or cheque number" fullWidth />
        </>
      )}
      {action === 'reject' && (
        <TextField label="Reason" value={note} onChange={(e) => setNote(e.target.value)} required multiline minRows={2} fullWidth />
      )}
    </FormDialog>
  );
}

InvoiceActionDialog.propTypes = {
  contract: contractShape,
  invoice: PropTypes.object.isRequired,
  action: PropTypes.oneOf(['certify', 'approve', 'pay', 'reject']).isRequired,
  onClose: PropTypes.func.isRequired,
};

// ------------------------------------------------------------------- variations and completion
export function VariationDialog({ contract: c, onClose }) {
  const [direction, setDirection] = useState('increase');
  const [amount, setAmount] = useState('');
  const [end, setEnd] = useState('');
  const [reason, setReason] = useState('');
  const run = useContractAction(c.id);
  const minor = inputToMinor(amount) ?? 0;
  const change = direction === 'increase' ? minor : -minor;
  const overCap = change > c.variationHeadroomMinor;
  const invalid = Number.isNaN(minor) || (minor === 0 && !end) || !reason.trim() || overCap;

  return (
    <FormDialog
      title="Request a variation"
      intro={`Increases may add up to 25% of the original price at most: ${formatMoney(c.variationHeadroomMinor, c.currency)} is left. The approvers come from the contract variation limits in the procurement settings.`}
      error={run.error}
      onClose={onClose}
      submitLabel="Send for approval"
      disabled={invalid}
      loading={run.isPending}
      onSubmit={() =>
        run.mutate(
          { action: 'variations', body: { valueChangeMinor: change, newEndDate: end || null, reason: reason.trim() } },
          { onSuccess: onClose },
        )
      }
    >
      <Stack direction="row" spacing={2}>
        <TextField select label="Value" value={direction} onChange={(e) => setDirection(e.target.value)} sx={{ width: 160 }}>
          <MenuItem value="increase">Increase</MenuItem>
          <MenuItem value="decrease">Decrease</MenuItem>
        </TextField>
        <MoneyField
          label="By"
          value={amount}
          onChange={setAmount}
          currency={c.currency}
          helperText={overCap ? 'Above the 25% limit' : 'Leave empty for a time extension only'}
        />
      </Stack>
      <DateField label="New end date (optional)" value={end} onChange={setEnd} helperText={`Now ${formatDate(c.endDate)}`} />
      <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} required multiline minRows={3} fullWidth />
    </FormDialog>
  );
}

VariationDialog.propTypes = { contract: contractShape, onClose: PropTypes.func.isRequired };

export function VariationDecisionDialog({ contract: c, variation: v, onClose }) {
  const [approve, setApprove] = useState(true);
  const [note, setNote] = useState('');
  const decide = useDecideVariation(c.id);
  return (
    <FormDialog
      title={`Variation ${v.number}`}
      intro={`${v.valueChangeMinor ? `${v.valueChangeMinor > 0 ? '+' : ''}${formatMoney(v.valueChangeMinor, c.currency)}` : 'No value change'}${v.newEndDate ? ` · new end date ${formatDate(v.newEndDate)}` : ''}. ${v.reason}`}
      error={decide.error}
      onClose={onClose}
      submitLabel="Record decision"
      color={approve ? 'primary' : 'error'}
      disabled={!approve && !note.trim()}
      loading={decide.isPending}
      onSubmit={() => decide.mutate({ variationId: v.id, approve, note: note.trim() || null }, { onSuccess: onClose })}
    >
      <RadioGroup value={approve ? 'yes' : 'no'} onChange={(e) => setApprove(e.target.value === 'yes')}>
        <FormControlLabel value="yes" control={<Radio />} label="Approve" />
        <FormControlLabel value="no" control={<Radio />} label="Reject" />
      </RadioGroup>
      <TextField label={approve ? 'Note (optional)' : 'Reason'} value={note} onChange={(e) => setNote(e.target.value)} required={!approve} multiline minRows={2} fullWidth />
    </FormDialog>
  );
}

VariationDecisionDialog.propTypes = { contract: contractShape, variation: PropTypes.object.isRequired, onClose: PropTypes.func.isRequired };

export function CompleteDialog({ contract: c, onClose }) {
  const [date, setDate] = useState(todayInput());
  const [quality, setQuality] = useState(0);
  const [timeliness, setTimeliness] = useState(0);
  const [compliance, setCompliance] = useState(0);
  const [comment, setComment] = useState('');
  const run = useContractAction(c.id);
  const rating = (label, value, set) => (
    <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
      <Typography>{label}</Typography>
      <Rating value={value} onChange={(_e, v) => set(v ?? 0)} />
    </Stack>
  );
  return (
    <FormDialog
      title={`Complete ${c.number}`}
      intro={`All milestones are accepted. Rate the supplier's performance: it is kept in the supplier register for future evaluations. ${c.retentionBp > 0 ? `Retention is released after the defects liability period (${c.defectsLiabilityDays} days).` : ''}`}
      error={run.error}
      onClose={onClose}
      submitLabel="Complete contract"
      disabled={!date || !quality || !timeliness || !compliance}
      loading={run.isPending}
      onSubmit={() =>
        run.mutate(
          { action: 'complete', body: { completedOn: date, quality, timeliness, compliance, comment: comment.trim() || null } },
          { onSuccess: onClose },
        )
      }
    >
      <DateField label="Completed on" value={date} onChange={setDate} required />
      {rating('Quality of goods, works or services', quality, setQuality)}
      {rating('Timeliness', timeliness, setTimeliness)}
      {rating('Compliance with contract terms', compliance, setCompliance)}
      <TextField label="Comment" value={comment} onChange={(e) => setComment(e.target.value)} multiline minRows={2} fullWidth />
    </FormDialog>
  );
}

CompleteDialog.propTypes = { contract: contractShape, onClose: PropTypes.func.isRequired };
