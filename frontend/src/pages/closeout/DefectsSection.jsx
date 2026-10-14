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
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useCloseoutAction } from '../../api/closeout.js';
import { DEFECT_SEVERITY, DEFECT_STATUS, defectCounts } from '../../domain/closeout.js';
import { formatDate } from '../../domain/contracts.js';
import { formatDateTime } from '../../domain/tenders.js';
import { NoteDialog } from '../procurement-plan/PlanDialogs.jsx';
import { FileList, MetaChip, UploadButton } from './CloseoutParts.jsx';

function DefectDialog({ defect, contracts, pending, error, onClose, onSave }) {
  const [f, setF] = useState({
    contractId: defect?.contractId ?? '',
    title: defect?.title ?? '',
    description: defect?.description ?? '',
    location: defect?.location ?? '',
    severity: defect?.severity ?? 'MINOR',
    dueDate: defect?.dueDate ?? '',
  });
  const set = (k) => (e) => setF((s) => ({ ...s, [k]: e.target.value }));
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{defect ? `Edit D${defect.number}` : 'Report a defect'}</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error.message}
          </Alert>
        )}
        <Grid container spacing={2} sx={{ mt: 0.5 }}>
          <Grid size={12}>
            <TextField label="Defect" value={f.title} onChange={set('title')} required fullWidth slotProps={{ htmlInput: { maxLength: 200 } }} />
          </Grid>
          <Grid size={12}>
            <TextField label="Description" value={f.description} onChange={set('description')} multiline minRows={2} fullWidth slotProps={{ htmlInput: { maxLength: 4000 } }} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField label="Location" value={f.location} onChange={set('location')} fullWidth slotProps={{ htmlInput: { maxLength: 200 } }} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              select
              label="Severity"
              value={f.severity}
              onChange={set('severity')}
              helperText={f.severity === 'CRITICAL' ? 'Blocks the handover while open' : ' '}
              fullWidth
            >
              {Object.entries(DEFECT_SEVERITY).map(([k, v]) => (
                <MenuItem key={k} value={k}>
                  {v.label}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField select label="Contract" value={f.contractId} onChange={set('contractId')} helperText="Whose work it is" fullWidth>
              <MenuItem value="">Not linked to a contract</MenuItem>
              {contracts.map((c) => (
                <MenuItem key={c.id} value={c.id}>
                  {c.number} · {c.title}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              label="Put right by"
              type="date"
              value={f.dueDate}
              onChange={set('dueDate')}
              slotProps={{ inputLabel: { shrink: true } }}
              fullWidth
            />
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={!f.title.trim()}
          loading={pending}
          onClick={() =>
            onSave({
              contractId: f.contractId || null,
              title: f.title.trim(),
              description: f.description.trim() || null,
              location: f.location.trim() || null,
              severity: f.severity,
              dueDate: f.dueDate || null,
            })
          }
        >
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

DefectDialog.propTypes = {
  defect: PropTypes.object,
  contracts: PropTypes.array.isRequired,
  pending: PropTypes.bool,
  error: PropTypes.instanceOf(Error),
  onClose: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
};

const NOTE_ACTIONS = {
  rectified: { title: 'Put right', label: 'What was done', required: true, confirm: 'Mark put right' },
  verify: { title: 'Accept the fix', label: 'Note (optional)', required: false, confirm: 'Close defect' },
  reopen: { title: 'Reopen', label: 'Why the fix is not accepted', required: true, confirm: 'Reopen' },
  waive: { title: 'Waive', label: 'Why it is waived', required: true, confirm: 'Waive' },
};

export default function DefectsSection({ closeout }) {
  const projectId = closeout.projectId;
  const action = useCloseoutAction(projectId);
  const [editing, setEditing] = useState(null);
  const [noting, setNoting] = useState(null); // { defect, kind }
  const counts = defectCounts(closeout.defects);
  const contracts = closeout.figures?.contracts ?? [];
  const run = (vars, onDone) => action.mutate(vars, { onSuccess: onDone });
  const close = (setter) => () => {
    action.reset();
    setter(null);
  };

  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Box>
            <Typography variant="h6">Defects</Typography>
            <Typography variant="body2" color="text.secondary">
              {counts.outstanding} outstanding
              {counts.critical ? ` · ${counts.critical} critical` : ''}
              {counts.overdue ? ` · ${counts.overdue} overdue` : ''}
              {counts.toCheck ? ` · ${counts.toCheck} to check` : ''}
              {closeout.figures?.dlpEnd ? ` · defects liability period ends ${formatDate(closeout.figures.dlpEnd)}` : ''}
            </Typography>
          </Box>
          {closeout.permissions.canReportDefect && (
            <Button startIcon={<AddIcon />} onClick={() => setEditing({})}>
              Report defect
            </Button>
          )}
        </Stack>
        {action.error && !editing && !noting && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {action.error.message}
          </Alert>
        )}
        {closeout.defects.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            No defects reported. Anyone, including the receiving unit, can report one during the defects liability
            period; open defects hold back the contractor&apos;s retention.
          </Typography>
        )}
        <Stack spacing={1.5}>
          {closeout.defects.map((d) => {
            const outstanding = d.status === 'OPEN' || d.status === 'RECTIFIED';
            return (
              <Box key={d.id} sx={{ borderTop: 1, borderColor: 'divider', pt: 1.5 }}>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ justifyContent: 'space-between' }}>
                  <Box sx={{ minWidth: 0 }}>
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                      <Typography variant="body1" sx={{ fontWeight: 600 }}>
                        D{d.number}
                      </Typography>
                      <MetaChip map={DEFECT_SEVERITY} value={d.severity} variant="outlined" />
                      <MetaChip map={DEFECT_STATUS} value={d.status} />
                      <Typography variant="body1">{d.title}</Typography>
                    </Stack>
                    <Typography variant="body2" color="text.secondary">
                      {[d.location, d.contractNumber, `reported by ${d.reportedBy?.name} ${formatDateTime(d.reportedAt)}`]
                        .filter(Boolean)
                        .join(' · ')}
                      {d.dueDate && (
                        <Typography component="span" variant="body2" color={d.overdue ? 'error' : 'text.secondary'}>
                          {' '}
                          · put right by {formatDate(d.dueDate)}
                        </Typography>
                      )}
                    </Typography>
                    {d.description && <Typography variant="body2">{d.description}</Typography>}
                    {d.rectifiedNote && (
                      <Typography variant="body2">
                        Put right: {d.rectifiedNote} ({d.rectifiedBy?.name}, {formatDateTime(d.rectifiedAt)})
                      </Typography>
                    )}
                    {d.closedNote && (
                      <Typography variant="body2" color="text.secondary">
                        {d.status === 'OPEN' ? 'Reopened' : d.status === 'WAIVED' ? 'Waived' : 'Closed'}: {d.closedNote}
                      </Typography>
                    )}
                    <FileList projectId={projectId} files={d.files} />
                  </Box>
                  <Stack direction="row" spacing={0.5} sx={{ flexShrink: 0, alignItems: 'flex-start', flexWrap: 'wrap' }}>
                    {outstanding && <UploadButton projectId={projectId} kind="DEFECT" refId={d.id} label="Photo" />}
                    {d.canRectify && (
                      <Button size="small" variant="contained" onClick={() => setNoting({ defect: d, kind: 'rectified' })}>
                        Put right
                      </Button>
                    )}
                    {d.canVerify && (
                      <>
                        <Button size="small" variant="contained" color="success" onClick={() => setNoting({ defect: d, kind: 'verify' })}>
                          Accept fix
                        </Button>
                        <Button size="small" onClick={() => setNoting({ defect: d, kind: 'reopen' })}>
                          Reopen
                        </Button>
                      </>
                    )}
                    {d.canEdit && (
                      <Button size="small" onClick={() => setEditing(d)}>
                        Edit
                      </Button>
                    )}
                    {d.canWaive && (
                      <Button size="small" color="warning" onClick={() => setNoting({ defect: d, kind: 'waive' })}>
                        Waive
                      </Button>
                    )}
                  </Stack>
                </Stack>
              </Box>
            );
          })}
        </Stack>
      </CardContent>

      {editing && (
        <DefectDialog
          defect={editing.id ? editing : null}
          contracts={contracts}
          pending={action.isPending}
          error={action.error}
          onClose={close(setEditing)}
          onSave={(body) =>
            run(
              editing.id ? { path: `/defects/${editing.id}`, method: 'PUT', body } : { path: '/defects', body },
              () => setEditing(null),
            )
          }
        />
      )}
      {noting && (
        <NoteDialog
          open
          title={`${NOTE_ACTIONS[noting.kind].title}: D${noting.defect.number}`}
          text={noting.defect.title}
          label={NOTE_ACTIONS[noting.kind].label}
          required={NOTE_ACTIONS[noting.kind].required}
          confirm={NOTE_ACTIONS[noting.kind].confirm}
          pending={action.isPending}
          error={action.error}
          onClose={close(setNoting)}
          onConfirm={(note) =>
            run({ path: `/defects/${noting.defect.id}/${noting.kind}`, body: { note } }, () => setNoting(null))
          }
        />
      )}
    </Card>
  );
}

DefectsSection.propTypes = { closeout: PropTypes.object.isRequired };
