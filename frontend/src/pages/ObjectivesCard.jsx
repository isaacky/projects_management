import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  List,
  ListItem,
  Menu,
  MenuItem,
  Skeleton,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import { useObjectiveAction, useObjectives } from '../api/objectives.js';
import { OUTCOME } from '../domain/objectives.js';
import { formatDate } from '../domain/contracts.js';

function ObjectiveDialog({ objective, reasonRequired, pending, error, onClose, onSave }) {
  const [f, setF] = useState({
    statement: objective?.statement ?? '',
    successMeasure: objective?.successMeasure ?? '',
    reason: '',
  });
  const set = (k) => (e) => setF((s) => ({ ...s, [k]: e.target.value }));
  const ready = f.statement.trim() && (!reasonRequired || f.reason.trim());
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{objective ? `Change O${objective.number}` : 'Add an objective'}</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error.message}
          </Alert>
        )}
        {reasonRequired && (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            The project has passed gate G0, so its objectives are its baseline. Say why this change is
            being made; the reason goes on the project&apos;s record.
          </Typography>
        )}
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Objective"
            value={f.statement}
            onChange={set('statement')}
            required
            multiline
            fullWidth
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
          <TextField
            label="How success will be judged"
            value={f.successMeasure}
            onChange={set('successMeasure')}
            placeholder="e.g. 12,000 customers connected by June 2028"
            fullWidth
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
          {reasonRequired && (
            <TextField
              label="Reason for the change"
              value={f.reason}
              onChange={set('reason')}
              required
              multiline
              minRows={2}
              fullWidth
              slotProps={{ htmlInput: { maxLength: 2000 } }}
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={!ready}
          loading={pending}
          onClick={() =>
            onSave({
              statement: f.statement.trim(),
              successMeasure: f.successMeasure.trim() || null,
              reason: f.reason.trim() || null,
              version: objective?.version,
            })
          }
        >
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

ObjectiveDialog.propTypes = {
  objective: PropTypes.object,
  reasonRequired: PropTypes.bool,
  pending: PropTypes.bool,
  error: PropTypes.instanceOf(Error),
  onClose: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
};

function WithdrawDialog({ objective, pending, error, onClose, onSave }) {
  const [reason, setReason] = useState('');
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Withdraw O{objective.number}</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error.message}
          </Alert>
        )}
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          A withdrawn objective stays on the record but is no longer assessed in reviews.
        </Typography>
        <TextField
          label="Reason"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          required
          multiline
          minRows={2}
          fullWidth
          slotProps={{ htmlInput: { maxLength: 2000 } }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!reason.trim()} loading={pending} onClick={() => onSave({ reason: reason.trim() })}>
          Withdraw
        </Button>
      </DialogActions>
    </Dialog>
  );
}

WithdrawDialog.propTypes = {
  objective: PropTypes.object.isRequired,
  pending: PropTypes.bool,
  error: PropTypes.instanceOf(Error),
  onClose: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
};

function ObjectiveRow({ objective: o, onAction }) {
  const [anchor, setAnchor] = useState(null);
  const actions = [
    o.canEdit && ['edit', 'Change'],
    o.canWithdraw && ['withdraw', 'Withdraw'],
    o.canReinstate && ['reinstate', 'Reinstate'],
    o.canDelete && ['delete', 'Delete'],
  ].filter(Boolean);
  const withdrawn = o.status === 'WITHDRAWN';
  const outcome = OUTCOME[o.latestOutcome];
  return (
    <ListItem
      disableGutters
      divider
      sx={{ alignItems: 'flex-start' }}
      secondaryAction={
        actions.length > 0 && (
          <>
            <IconButton edge="end" aria-label={`Actions for O${o.number}`} onClick={(e) => setAnchor(e.currentTarget)}>
              <MoreVertIcon />
            </IconButton>
            <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}>
              {actions.map(([k, label]) => (
                <MenuItem
                  key={k}
                  onClick={() => {
                    setAnchor(null);
                    onAction(k, o);
                  }}
                >
                  {label}
                </MenuItem>
              ))}
            </Menu>
          </>
        )
      }
    >
      <Box sx={{ pr: 5, opacity: withdrawn ? 0.6 : 1 }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography variant="body2" sx={{ fontWeight: 600, textDecoration: withdrawn ? 'line-through' : 'none' }}>
            O{o.number} · {o.statement}
          </Typography>
          {withdrawn && <Chip size="small" label="Withdrawn" variant="outlined" />}
          {!withdrawn && outcome && (
            <Tooltip title={`${o.latestReview}, ${formatDate(o.latestReviewOn)}${o.latestEvidence ? `: ${o.latestEvidence}` : ''}`}>
              <Chip size="small" label={outcome.label} color={outcome.color} variant={o.latestOutcome === 'TOO_EARLY' ? 'outlined' : 'filled'} />
            </Tooltip>
          )}
        </Stack>
        {o.successMeasure && (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
            Success: {o.successMeasure}
          </Typography>
        )}
        {withdrawn && o.withdrawnReason && (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
            Withdrawn: {o.withdrawnReason}
          </Typography>
        )}
      </Box>
    </ListItem>
  );
}

ObjectiveRow.propTypes = { objective: PropTypes.object.isRequired, onAction: PropTypes.func.isRequired };

/** The project's objectives on its overview, with what the latest post-implementation review found. */
export default function ObjectivesCard({ projectId }) {
  const { data: v, isLoading, error: loadError } = useObjectives(projectId);
  const action = useObjectiveAction(projectId);
  const [dialog, setDialog] = useState(null);

  const close = () => {
    setDialog(null);
    action.reset();
  };
  const run = (vars) => action.mutate(vars, { onSuccess: close });
  const onAction = (kind, o) => {
    if (kind === 'reinstate') action.mutate({ path: `/${o.id}/reinstate` });
    else if (kind === 'delete') action.mutate({ path: `/${o.id}`, method: 'DELETE' });
    else setDialog({ kind, item: o });
  };

  return (
    <Card sx={{ mt: 3 }}>
      <CardContent>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h6">Objectives</Typography>
          {v?.canAdd && (
            <Button size="small" startIcon={<AddIcon />} onClick={() => setDialog({ kind: 'add' })}>
              Add
            </Button>
          )}
        </Stack>
        {loadError && <Alert severity="error" sx={{ mt: 1 }}>{loadError.message}</Alert>}
        {!dialog && action.error && (
          <Alert severity="error" sx={{ mt: 1 }} onClose={() => action.reset()}>
            {action.error.message}
          </Alert>
        )}
        {isLoading && <Skeleton height={80} />}
        {v && v.objectives.length === 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            No objectives recorded. List what the project must achieve so the post-implementation review
            can say whether each was met.
          </Typography>
        )}
        {v && v.objectives.length > 0 && (
          <List dense>
            {v.objectives.map((o) => (
              <ObjectiveRow key={o.id} objective={o} onAction={onAction} />
            ))}
          </List>
        )}
        {v?.reasonRequired && v.objectives.length > 0 && (
          <Typography variant="caption" color="text.secondary">
            Baselined at gate G0: the PMO changes these, with a reason. Outcomes come from the
            post-implementation reviews on the Benefits tab.
          </Typography>
        )}
      </CardContent>
      {(dialog?.kind === 'add' || dialog?.kind === 'edit') && (
        <ObjectiveDialog
          objective={dialog.item}
          reasonRequired={v?.reasonRequired}
          pending={action.isPending}
          error={action.error}
          onClose={close}
          onSave={(body) =>
            run(dialog.item ? { path: `/${dialog.item.id}`, method: 'PUT', body } : { body })
          }
        />
      )}
      {dialog?.kind === 'withdraw' && (
        <WithdrawDialog
          objective={dialog.item}
          pending={action.isPending}
          error={action.error}
          onClose={close}
          onSave={(body) => run({ path: `/${dialog.item.id}/withdraw`, body })}
        />
      )}
    </Card>
  );
}

ObjectivesCard.propTypes = { projectId: PropTypes.string.isRequired };
