import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  Skeleton,
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
import ErrorAlert from '../../components/ErrorAlert.jsx';
import { useIssues, useRisks, useSaveIssue } from '../../api/execution.js';
import { useUsers } from '../../api/me.js';
import { ISSUE_PRIORITY, ISSUE_STATUS } from '../../domain/planning.js';
import { formatDate } from '../../domain/contracts.js';

function IssueDialog({ projectId, issue, onClose }) {
  const { data: users } = useUsers();
  const { data: risks } = useRisks(projectId);
  const [f, setF] = useState({
    title: issue?.title ?? '',
    description: issue?.description ?? '',
    priority: issue?.priority ?? 'MEDIUM',
    status: issue?.status ?? 'OPEN',
    ownerId: issue?.owner?.id ?? '',
    dueDate: issue?.dueDate ?? '',
    resolution: issue?.resolution ?? '',
    riskId: issue?.riskId ?? '',
  });
  const save = useSaveIssue(projectId);
  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }));
  const resolving = f.status === 'RESOLVED' || f.status === 'CLOSED';

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="md"
      component="form"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate(
          { id: issue?.id, ...f, title: f.title.trim(), ownerId: f.ownerId || null, dueDate: f.dueDate || null, riskId: f.riskId || null },
          { onSuccess: onClose },
        );
      }}
    >
      <DialogTitle>{issue ? `Issue I${issue.number}` : 'Raise an issue'}</DialogTitle>
      <DialogContent>
        {save.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {save.error.message}
          </Alert>
        )}
        <Grid container spacing={2} sx={{ mt: 0 }}>
          <Grid size={12}>
            <TextField label="Issue" value={f.title} onChange={set('title')} required fullWidth />
          </Grid>
          <Grid size={12}>
            <TextField label="Details and impact" value={f.description} onChange={set('description')} multiline minRows={2} fullWidth />
          </Grid>
          <Grid size={{ xs: 6, sm: 3 }}>
            <TextField select label="Priority" value={f.priority} onChange={set('priority')} fullWidth>
              {Object.entries(ISSUE_PRIORITY).map(([k, v]) => (
                <MenuItem key={k} value={k}>
                  {v.label}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 6, sm: 3 }}>
            <TextField select label="Status" value={f.status} onChange={set('status')} fullWidth>
              {Object.entries(ISSUE_STATUS).map(([k, v]) => (
                <MenuItem key={k} value={k}>
                  {v}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 3 }}>
            <TextField select label="Owner" value={f.ownerId} onChange={set('ownerId')} fullWidth>
              <MenuItem value="">No owner</MenuItem>
              {(users ?? []).map((u) => (
                <MenuItem key={u.id} value={u.id}>
                  {u.displayName}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 3 }}>
            <TextField type="date" label="Due" value={f.dueDate} onChange={set('dueDate')} slotProps={{ inputLabel: { shrink: true } }} fullWidth />
          </Grid>
          <Grid size={12}>
            <TextField select label="From risk (if it materialised)" value={f.riskId} onChange={set('riskId')} fullWidth>
              <MenuItem value="">None</MenuItem>
              {(risks ?? []).map((r) => (
                <MenuItem key={r.id} value={r.id}>
                  R{r.number} {r.title}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          {(resolving || f.resolution) && (
            <Grid size={12}>
              <TextField label="Resolution" value={f.resolution} onChange={set('resolution')} required={resolving} multiline minRows={2} fullWidth />
            </Grid>
          )}
        </Grid>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" disabled={!f.title.trim() || (resolving && !f.resolution.trim())} loading={save.isPending}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

IssueDialog.propTypes = { projectId: PropTypes.string.isRequired, issue: PropTypes.object, onClose: PropTypes.func.isRequired };

export default function IssuesTab({ projectId }) {
  const { data, isLoading, error } = useIssues(projectId);
  const [editing, setEditing] = useState(null);
  if (error) return <ErrorAlert error={error} title="Could not load issues" />;
  if (isLoading || !data) return <Skeleton variant="rounded" height={200} />;
  const order = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };
  const rows = [...data].sort(
    (a, b) =>
      Number(['RESOLVED', 'CLOSED'].includes(a.status)) - Number(['RESOLVED', 'CLOSED'].includes(b.status)) ||
      order[a.priority] - order[b.priority] ||
      a.number - b.number,
  );
  return (
    <Card>
      <CardContent>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Typography variant="h6">Issue log</Typography>
          <Button variant="contained" onClick={() => setEditing({})}>
            Raise issue
          </Button>
        </Stack>
        <TableContainer>
          <Table size="small" sx={{ minWidth: 640 }}>
            <TableHead>
              <TableRow>
                <TableCell>#</TableCell>
                <TableCell>Issue</TableCell>
                <TableCell>Priority</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Owner</TableCell>
                <TableCell>Due</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((i) => (
                <TableRow
                  key={i.id}
                  hover
                  onClick={() => i.canEdit && setEditing({ issue: i })}
                  sx={{ cursor: i.canEdit ? 'pointer' : 'default', verticalAlign: 'top', opacity: ['RESOLVED', 'CLOSED'].includes(i.status) ? 0.6 : 1 }}
                >
                  <TableCell>I{i.number}</TableCell>
                  <TableCell>
                    {i.title}
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                      {i.riskNumber ? `From risk R${i.riskNumber}. ` : ''}
                      {i.resolution ? `Resolution: ${i.resolution}` : i.description ?? ''}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Chip size="small" color={ISSUE_PRIORITY[i.priority].color} label={ISSUE_PRIORITY[i.priority].label} />
                  </TableCell>
                  <TableCell>{ISSUE_STATUS[i.status]}</TableCell>
                  <TableCell>{i.owner?.name ?? '—'}</TableCell>
                  <TableCell sx={{ color: i.overdue ? 'error.main' : undefined }}>{formatDate(i.dueDate)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        {!rows.length && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            No issues logged.
          </Typography>
        )}
      </CardContent>
      {editing && <IssueDialog projectId={projectId} issue={editing.issue} onClose={() => setEditing(null)} />}
    </Card>
  );
}

IssuesTab.propTypes = { projectId: PropTypes.string.isRequired };
