import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  IconButton,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import { useUsers } from '../../api/me.js';
import { useCloseoutAction } from '../../api/closeout.js';
import { assetsToRequest } from '../../domain/closeout.js';
import { formatDate } from '../../domain/contracts.js';
import { formatDateTime } from '../../domain/tenders.js';
import { formatMoney, toMinorUnits } from '../../utils/money.js';
import { minorToInput } from '../../utils/amountField.js';
import { NoteDialog } from '../procurement-plan/PlanDialogs.jsx';
import { FileList, UploadButton } from './CloseoutParts.jsx';

let rowKey = 0;
const emptyAsset = () => ({ key: `a-${++rowKey}`, description: '', quantity: '1', unit: 'each', tag: '', location: '', value: '' });

function HandoverDialog({ closeout, pending, error, onClose, onSave }) {
  const h = closeout.handover;
  const { data: users } = useUsers();
  const [f, setF] = useState({
    receivingUnit: h.receivingUnit ?? '',
    receivingOfficerId: h.receivingOfficer?.id ?? '',
    handoverDate: h.handoverDate ?? '',
    note: h.note ?? '',
  });
  const [rows, setRows] = useState(() =>
    h.assets.length
      ? h.assets.map((a) => ({
          key: a.id,
          description: a.description,
          quantity: String(a.quantity),
          unit: a.unit,
          tag: a.tag ?? '',
          location: a.location ?? '',
          value: minorToInput(a.valueMinor),
        }))
      : [],
  );
  const [rowErrors, setRowErrors] = useState({});
  const set = (k) => (e) => setF((s) => ({ ...s, [k]: e.target.value }));
  const setRow = (key, k) => (e) => setRows((rs) => rs.map((r) => (r.key === key ? { ...r, [k]: e.target.value } : r)));

  const save = () => {
    const { assets, errors } = assetsToRequest(rows, toMinorUnits);
    setRowErrors(errors);
    if (Object.keys(errors).length) return;
    onSave({
      receivingUnit: f.receivingUnit.trim() || null,
      receivingOfficerId: f.receivingOfficerId || null,
      handoverDate: f.handoverDate || null,
      note: f.note.trim() || null,
      assets,
    });
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="lg">
      <DialogTitle>Prepare the handover</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error.message}
          </Alert>
        )}
        <Grid container spacing={2} sx={{ mt: 0.5, mb: 2 }}>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField
              label="Receiving unit"
              value={f.receivingUnit}
              onChange={set('receivingUnit')}
              placeholder="e.g. Network Operations, Nairobi South"
              fullWidth
              slotProps={{ htmlInput: { maxLength: 120 } }}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField
              select
              label="Receiving officer"
              value={f.receivingOfficerId}
              onChange={set('receivingOfficerId')}
              helperText="Accepts or rejects the handover"
              fullWidth
            >
              <MenuItem value="">Not yet named</MenuItem>
              {(users ?? []).map((u) => (
                <MenuItem key={u.id} value={u.id}>
                  {u.displayName}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField
              label="Handover date"
              type="date"
              value={f.handoverDate}
              onChange={set('handoverDate')}
              slotProps={{ inputLabel: { shrink: true } }}
              fullWidth
            />
          </Grid>
          <Grid size={12}>
            <TextField
              label="Notes for the receiving unit"
              value={f.note}
              onChange={set('note')}
              helperText="What is being handed over, outstanding items, warranties, who to call"
              multiline
              minRows={2}
              fullWidth
              slotProps={{ htmlInput: { maxLength: 4000 } }}
            />
          </Grid>
        </Grid>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Typography variant="subtitle1">Assets handed over</Typography>
          <Button startIcon={<AddIcon />} onClick={() => setRows((rs) => [...rs, emptyAsset()])}>
            Add asset
          </Button>
        </Stack>
        <TableContainer>
          <Table size="small" sx={{ minWidth: 900 }}>
            <TableHead>
              <TableRow>
                <TableCell>Description</TableCell>
                <TableCell sx={{ width: 90 }}>Qty</TableCell>
                <TableCell sx={{ width: 100 }}>Unit</TableCell>
                <TableCell sx={{ width: 180 }}>Tag / serial numbers</TableCell>
                <TableCell sx={{ width: 160 }}>Location</TableCell>
                <TableCell sx={{ width: 150 }}>Value (KES)</TableCell>
                <TableCell sx={{ width: 48 }} />
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7}>
                    <Typography variant="body2" color="text.secondary">
                      No assets listed. Add the equipment and installations for the asset register.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {rows.map((r, i) => (
                <TableRow key={r.key}>
                  <TableCell>
                    <TextField
                      size="small"
                      value={r.description}
                      onChange={setRow(r.key, 'description')}
                      error={Boolean(rowErrors[i])}
                      helperText={rowErrors[i]}
                      fullWidth
                    />
                  </TableCell>
                  <TableCell>
                    <TextField size="small" value={r.quantity} onChange={setRow(r.key, 'quantity')} inputMode="decimal" />
                  </TableCell>
                  <TableCell>
                    <TextField size="small" value={r.unit} onChange={setRow(r.key, 'unit')} />
                  </TableCell>
                  <TableCell>
                    <TextField size="small" value={r.tag} onChange={setRow(r.key, 'tag')} fullWidth />
                  </TableCell>
                  <TableCell>
                    <TextField size="small" value={r.location} onChange={setRow(r.key, 'location')} fullWidth />
                  </TableCell>
                  <TableCell>
                    <TextField size="small" value={r.value} onChange={setRow(r.key, 'value')} inputMode="decimal" />
                  </TableCell>
                  <TableCell>
                    <IconButton
                      size="small"
                      aria-label={`Remove asset ${i + 1}`}
                      onClick={() => setRows((rs) => rs.filter((x) => x.key !== r.key))}
                    >
                      <DeleteOutline fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" loading={pending} onClick={save}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

HandoverDialog.propTypes = {
  closeout: PropTypes.object.isRequired,
  pending: PropTypes.bool,
  error: PropTypes.instanceOf(Error),
  onClose: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
};

function Field({ label, children }) {
  return (
    <Box sx={{ mb: 1.5 }}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body1">{children || '—'}</Typography>
    </Box>
  );
}

Field.propTypes = { label: PropTypes.string.isRequired, children: PropTypes.node };

export default function HandoverSection({ closeout }) {
  const projectId = closeout.projectId;
  const action = useCloseoutAction(projectId);
  const [dialog, setDialog] = useState(null); // 'edit' | 'accept' | 'reject'
  const h = closeout.handover;
  const can = closeout.permissions;
  const close = () => {
    action.reset();
    setDialog(null);
  };
  const run = (vars) => action.mutate(vars, { onSuccess: () => setDialog(null) });

  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1, flexWrap: 'wrap', gap: 1 }}>
          <Typography variant="h6">Handover to operations</Typography>
          <Stack direction="row" spacing={1}>
            {can.canEdit && (
              <Button onClick={() => setDialog('edit')}>Edit handover</Button>
            )}
            {closeout.status === 'OPEN' && can.canEdit && (
              <Button
                variant="contained"
                disabled={!can.canSubmitHandover}
                loading={action.isPending && !dialog}
                onClick={() => run({ path: '/handover/submit' })}
              >
                Submit handover
              </Button>
            )}
            {can.canDecideHandover && (
              <>
                <Button color="warning" variant="outlined" onClick={() => setDialog('reject')}>
                  Reject
                </Button>
                <Button color="success" variant="contained" onClick={() => setDialog('accept')}>
                  Accept handover
                </Button>
              </>
            )}
          </Stack>
        </Stack>
        {action.error && !dialog && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {action.error.message}
          </Alert>
        )}
        {closeout.status === 'OPEN' && closeout.handoverBlockers.length > 0 && (
          <Alert severity="info" sx={{ mb: 2 }}>
            Before the handover can be submitted:
            <List dense disablePadding>
              {closeout.handoverBlockers.map((b) => (
                <ListItem key={b} disableGutters sx={{ py: 0 }}>
                  <ListItemText primary={`• ${b}`} />
                </ListItem>
              ))}
            </List>
          </Alert>
        )}
        {closeout.status === 'OPEN' && h.decisionNote && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            Rejected by {h.decidedBy?.name} on {formatDateTime(h.decidedAt)}: {h.decisionNote}
          </Alert>
        )}
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 4 }}>
            <Field label="Receiving unit">{h.receivingUnit}</Field>
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <Field label="Receiving officer">{h.receivingOfficer?.name}</Field>
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <Field label="Handover date">{h.handoverDate && formatDate(h.handoverDate)}</Field>
          </Grid>
          {h.note && (
            <Grid size={12}>
              <Field label="Notes">{h.note}</Field>
            </Grid>
          )}
          {h.submittedBy && (
            <Grid size={{ xs: 12, sm: 6 }}>
              <Field label="Submitted">
                {h.submittedBy.name}, {formatDateTime(h.submittedAt)}
              </Field>
            </Grid>
          )}
          {closeout.status !== 'OPEN' && h.decidedBy && (
            <Grid size={{ xs: 12, sm: 6 }}>
              <Field label="Accepted">
                {h.decidedBy.name}, {formatDateTime(h.decidedAt)}
                {h.decisionNote ? ` · ${h.decisionNote}` : ''}
              </Field>
            </Grid>
          )}
        </Grid>

        <Typography variant="subtitle2" sx={{ mt: 1 }}>
          Assets ({h.assets.length}) · {formatMoney(h.assetValueMinor)}
        </Typography>
        {h.assets.length > 0 && (
          <TableContainer sx={{ mb: 2 }}>
            <Table size="small" sx={{ minWidth: 700 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Description</TableCell>
                  <TableCell align="right">Qty</TableCell>
                  <TableCell>Tag / serial</TableCell>
                  <TableCell>Location</TableCell>
                  <TableCell align="right">Value</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {h.assets.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell>{a.description}</TableCell>
                    <TableCell align="right">
                      {a.quantity} {a.unit}
                    </TableCell>
                    <TableCell>{a.tag}</TableCell>
                    <TableCell>{a.location}</TableCell>
                    <TableCell align="right">{a.valueMinor === null ? '—' : formatMoney(a.valueMinor)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mt: 1 }}>
          <Typography variant="subtitle2">Handover documents</Typography>
          {can.canEdit && <UploadButton projectId={projectId} kind="HANDOVER" label="Add document" />}
        </Stack>
        <Typography variant="caption" color="text.secondary">
          As-built drawings, manuals, test certificates, warranties and the signed handover certificate.
        </Typography>
        <FileList projectId={projectId} files={h.documents} />
      </CardContent>

      {dialog === 'edit' && (
        <HandoverDialog
          closeout={closeout}
          pending={action.isPending}
          error={action.error}
          onClose={close}
          onSave={(body) => run({ path: '/handover', method: 'PUT', body })}
        />
      )}
      <NoteDialog
        key={`accept-${dialog}`}
        open={dialog === 'accept'}
        title="Accept the handover"
        text="Accepting confirms the unit has received the assets and documents. The final figures are frozen and the project can pass gate G4."
        label="Note (optional)"
        confirm="Accept"
        pending={action.isPending}
        error={action.error}
        onClose={close}
        onConfirm={(note) => run({ path: '/handover/accept', body: { note } })}
      />
      <NoteDialog
        key={`reject-${dialog}`}
        open={dialog === 'reject'}
        title="Reject the handover"
        text="The project team is told what must be put right and can submit again."
        label="What must be put right"
        required
        confirm="Reject"
        pending={action.isPending}
        error={action.error}
        onClose={close}
        onConfirm={(note) => run({ path: '/handover/reject', body: { note } })}
      />
    </Card>
  );
}

HandoverSection.propTypes = { closeout: PropTypes.object.isRequired };
