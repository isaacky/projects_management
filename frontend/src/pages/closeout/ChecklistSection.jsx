import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  LinearProgress,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import CheckCircleOutline from '@mui/icons-material/CheckCircleOutline';
import ErrorOutline from '@mui/icons-material/ErrorOutline';
import { useCloseoutAction } from '../../api/closeout.js';
import { ITEM_STATUS, checklistProgress } from '../../domain/closeout.js';
import { roleLabel } from '../../domain/procurement.js';
import { formatDateTime } from '../../domain/tenders.js';
import RoleField from '../procurement-settings/RoleField.jsx';
import { NoteDialog } from '../procurement-plan/PlanDialogs.jsx';
import { FileList, MetaChip, UploadButton } from './CloseoutParts.jsx';

/** Add or edit a checklist item (also used for the standard checklist). */
export function ItemDialog({ item, pending, error, onClose, onSave }) {
  const [f, setF] = useState({
    title: item?.title ?? '',
    description: item?.description ?? '',
    responsibleRole: item?.responsibleRole ?? '',
    evidenceRequired: item?.evidenceRequired ?? false,
  });
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{item ? 'Edit checklist item' : 'Add a checklist item'}</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error.message}
          </Alert>
        )}
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Item"
            value={f.title}
            onChange={(e) => setF({ ...f, title: e.target.value })}
            required
            slotProps={{ htmlInput: { maxLength: 200 } }}
          />
          <TextField
            label="Description"
            value={f.description}
            onChange={(e) => setF({ ...f, description: e.target.value })}
            multiline
            minRows={2}
            slotProps={{ htmlInput: { maxLength: 1000 } }}
          />
          <RoleField
            label="Responsible role (optional)"
            value={f.responsibleRole}
            onChange={(v) => setF((s) => ({ ...s, responsibleRole: v }))}
            helperText="Besides the project manager and the PMO, people with this role can mark the item"
          />
          <FormControlLabel
            control={<Checkbox checked={f.evidenceRequired} onChange={(e) => setF({ ...f, evidenceRequired: e.target.checked })} />}
            label="Evidence must be attached before it is marked done"
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={!f.title.trim()}
          loading={pending}
          onClick={() =>
            onSave({
              title: f.title.trim(),
              description: f.description.trim() || null,
              responsibleRole: f.responsibleRole || null,
              evidenceRequired: f.evidenceRequired,
            })
          }
        >
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

ItemDialog.propTypes = {
  item: PropTypes.object,
  pending: PropTypes.bool,
  error: PropTypes.instanceOf(Error),
  onClose: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
};

export default function ChecklistSection({ closeout }) {
  const projectId = closeout.projectId;
  const action = useCloseoutAction(projectId);
  const [editing, setEditing] = useState(null); // item, or {} for a new one
  const [na, setNa] = useState(null);
  const progress = checklistProgress(closeout.checklist);
  const run = (vars, onDone) => action.mutate(vars, { onSuccess: onDone });
  const close = (setter) => () => {
    action.reset();
    setter(null);
  };

  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Typography variant="h6">Completion checklist</Typography>
          {closeout.permissions.canEdit && (
            <Button startIcon={<AddIcon />} onClick={() => setEditing({})}>
              Add item
            </Button>
          )}
        </Stack>
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 2 }}>
          <LinearProgress
            variant="determinate"
            value={progress.pct}
            sx={{ flexGrow: 1, height: 8, borderRadius: 4 }}
            aria-label="Checklist progress"
          />
          <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
            {progress.done + progress.na} of {progress.total} finished
          </Typography>
        </Stack>

        <Typography variant="subtitle2">Checked by the platform</Typography>
        <List dense disablePadding sx={{ mb: 2 }}>
          {closeout.autoChecks.map((a) => (
            <ListItem key={a.key} disableGutters>
              <ListItemIcon sx={{ minWidth: 32 }}>
                {a.passed ? <CheckCircleOutline color="success" fontSize="small" /> : <ErrorOutline color="error" fontSize="small" />}
              </ListItemIcon>
              <ListItemText primary={a.label} secondary={a.detail} />
            </ListItem>
          ))}
        </List>

        {action.error && !editing && !na && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {action.error.message}
          </Alert>
        )}
        <Stack spacing={1.5}>
          {closeout.checklist.map((i) => (
            <Box key={i.id} sx={{ borderTop: 1, borderColor: 'divider', pt: 1.5 }}>
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ justifyContent: 'space-between' }}>
                <Box sx={{ minWidth: 0 }}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                    <MetaChip map={ITEM_STATUS} value={i.status} />
                    <Typography variant="body1" sx={{ fontWeight: 500 }}>
                      {i.title}
                    </Typography>
                    {i.evidenceRequired && <Typography variant="caption" color="text.secondary">evidence required</Typography>}
                  </Stack>
                  {i.description && (
                    <Typography variant="body2" color="text.secondary">
                      {i.description}
                    </Typography>
                  )}
                  {i.responsibleRole && (
                    <Typography variant="caption" color="text.secondary">
                      Responsible: {roleLabel(i.responsibleRole)}
                    </Typography>
                  )}
                  {i.doneBy && (
                    <Typography variant="body2">
                      {i.status === 'DONE' ? 'Done' : 'Not applicable'} · {i.doneBy.name}, {formatDateTime(i.doneAt)}
                      {i.note ? ` · ${i.note}` : ''}
                    </Typography>
                  )}
                  <FileList projectId={projectId} files={i.evidence} />
                </Box>
                <Stack direction="row" spacing={0.5} sx={{ flexShrink: 0, alignItems: 'flex-start', flexWrap: 'wrap' }}>
                  {i.canMark && i.status === 'OPEN' && (
                    <>
                      <UploadButton projectId={projectId} kind="EVIDENCE" refId={i.id} label="Evidence" />
                      <Button size="small" variant="contained" onClick={() => run({ path: `/checklist/${i.id}/done` })}>
                        Done
                      </Button>
                      <Button size="small" onClick={() => setNa(i)}>
                        N/A
                      </Button>
                    </>
                  )}
                  {i.canMark && i.status !== 'OPEN' && (
                    <Button size="small" onClick={() => run({ path: `/checklist/${i.id}/reopen` })}>
                      Reopen
                    </Button>
                  )}
                  {i.canEdit && (
                    <>
                      <Button size="small" onClick={() => setEditing(i)}>
                        Edit
                      </Button>
                      <Button size="small" color="error" onClick={() => run({ path: `/checklist/${i.id}`, method: 'DELETE' })}>
                        Remove
                      </Button>
                    </>
                  )}
                </Stack>
              </Stack>
            </Box>
          ))}
        </Stack>
      </CardContent>

      {editing && (
        <ItemDialog
          item={editing.id ? editing : null}
          pending={action.isPending}
          error={action.error}
          onClose={close(setEditing)}
          onSave={(body) =>
            run(
              editing.id ? { path: `/checklist/${editing.id}`, method: 'PUT', body } : { path: '/checklist', body },
              () => setEditing(null),
            )
          }
        />
      )}
      {na && (
        <NoteDialog
          open
          title={`Not applicable: ${na.title}`}
          label="Why it does not apply to this project"
          required
          confirm="Mark not applicable"
          pending={action.isPending}
          error={action.error}
          onClose={close(setNa)}
          onConfirm={(note) => run({ path: `/checklist/${na.id}/not-applicable`, body: { note } }, () => setNa(null))}
        />
      )}
    </Card>
  );
}

ChecklistSection.propTypes = { closeout: PropTypes.object.isRequired };
