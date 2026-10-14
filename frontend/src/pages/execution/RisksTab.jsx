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
  FormControlLabel,
  Grid,
  MenuItem,
  Skeleton,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import { useCloseRisk, useRisks, useSaveRisk } from '../../api/execution.js';
import { useUsers } from '../../api/me.js';
import { RISK_CATEGORY, RISK_LEVEL, RISK_RESPONSE, riskLevel } from '../../domain/planning.js';
import { formatDate } from '../../domain/contracts.js';

const LEVEL_BG = { LOW: '#c8e6c9', MEDIUM: '#fff9c4', HIGH: '#ffe0b2', CRITICAL: '#ffcdd2' };

/** 5x5 probability x impact grid with the number of open risks in each cell. */
function HeatMap({ risks }) {
  const open = risks.filter((r) => r.status === 'OPEN');
  return (
    <Box>
      <Typography variant="subtitle2" sx={{ mb: 1 }}>
        Open risks by probability and impact
      </Typography>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
        <Typography variant="caption" sx={{ writingMode: 'vertical-rl', transform: 'rotate(180deg)' }}>
          Probability
        </Typography>
        <Box>
          {[5, 4, 3, 2, 1].map((pr) => (
            <Stack key={pr} direction="row">
              {[1, 2, 3, 4, 5].map((im) => {
                const here = open.filter((r) => r.probability === pr && r.impact === im);
                return (
                  <Tooltip key={im} title={here.map((r) => `R${r.number} ${r.title}`).join(' · ') || `P${pr} × I${im}`}>
                    <Box
                      sx={{
                        width: 40,
                        height: 32,
                        m: '1px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        bgcolor: LEVEL_BG[riskLevel(pr, im)],
                        color: 'rgba(0,0,0,0.87)',
                        fontWeight: 600,
                        borderRadius: 0.5,
                      }}
                    >
                      {here.length || ''}
                    </Box>
                  </Tooltip>
                );
              })}
            </Stack>
          ))}
          <Typography variant="caption" sx={{ display: 'block', textAlign: 'center' }}>
            Impact
          </Typography>
        </Box>
      </Stack>
    </Box>
  );
}

HeatMap.propTypes = { risks: PropTypes.array.isRequired };

function RiskDialog({ projectId, risk, onClose }) {
  const { data: users } = useUsers();
  const [f, setF] = useState({
    title: risk?.title ?? '',
    description: risk?.description ?? '',
    category: risk?.category ?? 'TECHNICAL',
    probability: risk?.probability ?? 3,
    impact: risk?.impact ?? 3,
    response: risk?.response ?? 'MITIGATE',
    mitigation: risk?.mitigation ?? '',
    ownerId: risk?.owner?.id ?? '',
    dueDate: risk?.dueDate ?? '',
  });
  const [closeNote, setCloseNote] = useState('');
  const [closing, setClosing] = useState(false);
  const save = useSaveRisk(projectId);
  const close = useCloseRisk(projectId);
  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }));
  const level = riskLevel(f.probability, f.impact);
  const error = save.error ?? close.error;

  const submit = (e) => {
    e.preventDefault();
    if (closing) {
      close.mutate({ id: risk.id, note: closeNote.trim() }, { onSuccess: onClose });
      return;
    }
    save.mutate(
      {
        id: risk?.id,
        ...f,
        title: f.title.trim(),
        ownerId: f.ownerId || null,
        dueDate: f.dueDate || null,
        probability: Number(f.probability),
        impact: Number(f.impact),
      },
      { onSuccess: onClose },
    );
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md" component="form" onSubmit={submit}>
      <DialogTitle>{risk ? `Risk R${risk.number}` : 'Raise a risk'}</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error.message}
          </Alert>
        )}
        <Grid container spacing={2} sx={{ mt: 0 }}>
          <Grid size={12}>
            <TextField label="Risk" value={f.title} onChange={set('title')} required fullWidth helperText="What could happen, e.g. 'Landowners refuse wayleave consent on section 3'" />
          </Grid>
          <Grid size={12}>
            <TextField label="Cause and effect" value={f.description} onChange={set('description')} multiline minRows={2} fullWidth />
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField select label="Category" value={f.category} onChange={set('category')} fullWidth>
              {Object.entries(RISK_CATEGORY).map(([k, v]) => (
                <MenuItem key={k} value={k}>
                  {v}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 6, sm: 2 }}>
            <TextField select label="Probability" value={f.probability} onChange={set('probability')} fullWidth>
              {[1, 2, 3, 4, 5].map((n) => (
                <MenuItem key={n} value={n}>
                  {n}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 6, sm: 2 }}>
            <TextField select label="Impact" value={f.impact} onChange={set('impact')} fullWidth>
              {[1, 2, 3, 4, 5].map((n) => (
                <MenuItem key={n} value={n}>
                  {n}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }} sx={{ display: 'flex', alignItems: 'center' }}>
            <Chip label={`Score ${f.probability * f.impact} · ${RISK_LEVEL[level].label}`} color={RISK_LEVEL[level].color} />
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField select label="Response" value={f.response} onChange={set('response')} fullWidth>
              {Object.entries(RISK_RESPONSE).map(([k, v]) => (
                <MenuItem key={k} value={k}>
                  {v}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField select label="Owner" value={f.ownerId} onChange={set('ownerId')} fullWidth>
              <MenuItem value="">No owner</MenuItem>
              {(users ?? []).map((u) => (
                <MenuItem key={u.id} value={u.id}>
                  {u.displayName}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField type="date" label="Action due" value={f.dueDate} onChange={set('dueDate')} slotProps={{ inputLabel: { shrink: true } }} fullWidth />
          </Grid>
          <Grid size={12}>
            <TextField label="Mitigation actions" value={f.mitigation} onChange={set('mitigation')} multiline minRows={2} fullWidth />
          </Grid>
          {risk && risk.status === 'OPEN' && (
            <Grid size={12}>
              <FormControlLabel control={<Switch checked={closing} onChange={(e) => setClosing(e.target.checked)} />} label="Close this risk" />
              {closing && (
                <TextField label="Why it no longer applies" value={closeNote} onChange={(e) => setCloseNote(e.target.value)} required fullWidth sx={{ mt: 1 }} />
              )}
            </Grid>
          )}
        </Grid>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        {risk?.status === 'CLOSED' && (
          <Button onClick={() => close.mutate({ id: risk.id, reopen: true }, { onSuccess: onClose })}>Reopen</Button>
        )}
        <Button onClick={onClose}>Cancel</Button>
        <Button
          type="submit"
          variant="contained"
          disabled={risk?.status === 'CLOSED' || (closing ? !closeNote.trim() : !f.title.trim())}
          loading={save.isPending || close.isPending}
        >
          {closing ? 'Close risk' : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

RiskDialog.propTypes = { projectId: PropTypes.string.isRequired, risk: PropTypes.object, onClose: PropTypes.func.isRequired };

export default function RisksTab({ projectId }) {
  const { data, isLoading, error } = useRisks(projectId);
  const [editing, setEditing] = useState(null);
  const [showClosed, setShowClosed] = useState(false);
  if (error) return <ErrorAlert error={error} title="Could not load risks" />;
  if (isLoading || !data) return <Skeleton variant="rounded" height={200} />;
  const rows = data.filter((r) => showClosed || r.status === 'OPEN').sort((a, b) => b.score - a.score || a.number - b.number);

  return (
    <Grid container spacing={3}>
      <Grid size={{ xs: 12, md: 8 }}>
        <Card>
          <CardContent>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant="h6">Risk register</Typography>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <FormControlLabel control={<Switch size="small" checked={showClosed} onChange={(e) => setShowClosed(e.target.checked)} />} label="Show closed" />
                <Button variant="contained" onClick={() => setEditing({})}>
                  Raise risk
                </Button>
              </Stack>
            </Stack>
            <TableContainer>
              <Table size="small" sx={{ minWidth: 640 }}>
                <TableHead>
                  <TableRow>
                    <TableCell>#</TableCell>
                    <TableCell>Risk</TableCell>
                    <TableCell>Level</TableCell>
                    <TableCell>Response</TableCell>
                    <TableCell>Owner</TableCell>
                    <TableCell>Due</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((r) => (
                    <TableRow
                      key={r.id}
                      hover
                      onClick={() => r.canEdit && setEditing({ risk: r })}
                      sx={{ cursor: r.canEdit ? 'pointer' : 'default', opacity: r.status === 'CLOSED' ? 0.55 : 1, verticalAlign: 'top' }}
                    >
                      <TableCell>R{r.number}</TableCell>
                      <TableCell>
                        {r.title}
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                          {RISK_CATEGORY[r.category]}
                          {r.mitigation ? ` · ${r.mitigation}` : ''}
                          {r.closedNote ? ` · closed: ${r.closedNote}` : ''}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip size="small" color={RISK_LEVEL[r.level].color} label={`${r.score} ${RISK_LEVEL[r.level].label}`} />
                      </TableCell>
                      <TableCell>{RISK_RESPONSE[r.response]}</TableCell>
                      <TableCell>{r.owner?.name ?? '—'}</TableCell>
                      <TableCell sx={{ color: r.overdue ? 'error.main' : undefined }}>{formatDate(r.dueDate)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            {!rows.length && (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                No {showClosed ? '' : 'open '}risks.
              </Typography>
            )}
          </CardContent>
        </Card>
      </Grid>
      <Grid size={{ xs: 12, md: 4 }}>
        <Card>
          <CardContent>
            <HeatMap risks={data} />
          </CardContent>
        </Card>
      </Grid>
      {editing && <RiskDialog projectId={projectId} risk={editing.risk} onClose={() => setEditing(null)} />}
    </Grid>
  );
}

RisksTab.propTypes = { projectId: PropTypes.string.isRequired };
