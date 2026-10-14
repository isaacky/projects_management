import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Grid,
  List,
  ListItem,
  ListItemText,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import { useCloseout, useCloseoutAction } from '../../api/closeout.js';
import { CLOSEOUT_STATUS } from '../../domain/closeout.js';
import { formatDate } from '../../domain/contracts.js';
import { formatDateTime } from '../../domain/tenders.js';
import { formatMoney } from '../../utils/money.js';
import { NoteDialog } from '../procurement-plan/PlanDialogs.jsx';
import ChecklistSection from './ChecklistSection.jsx';
import DefectsSection from './DefectsSection.jsx';
import HandoverSection from './HandoverSection.jsx';
import LessonsSection from './LessonsSection.jsx';
import { MetaChip } from './CloseoutParts.jsx';

function Figure({ label, value, note, color }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="h6" sx={{ color }}>
        {value ?? '—'}
      </Typography>
      {note && (
        <Typography variant="caption" color="text.secondary">
          {note}
        </Typography>
      )}
    </Box>
  );
}

Figure.propTypes = { label: PropTypes.string.isRequired, value: PropTypes.node, note: PropTypes.node, color: PropTypes.string };

function FiguresCard({ figures }) {
  const f = figures;
  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Typography variant="h6">
          Final figures{' '}
          <Typography component="span" variant="body2" color="text.secondary">
            {f.frozen ? '(frozen when the handover was accepted)' : '(live until the handover is accepted)'}
          </Typography>
        </Typography>
        <Grid container spacing={2} sx={{ mt: 0.5 }}>
          <Grid size={{ xs: 6, md: 2 }}>
            <Figure label="Budget" value={formatMoney(f.budgetMinor, f.currency, { compact: true })} />
          </Grid>
          <Grid size={{ xs: 6, md: 2 }}>
            <Figure label="Contract value" value={formatMoney(f.contractValueMinor, f.currency, { compact: true })} />
          </Grid>
          <Grid size={{ xs: 6, md: 2 }}>
            <Figure
              label="Certified"
              value={formatMoney(f.certifiedMinor, f.currency, { compact: true })}
              note={`${formatMoney(f.paidMinor, f.currency, { compact: true })} paid`}
            />
          </Grid>
          <Grid size={{ xs: 6, md: 2 }}>
            <Figure
              label={f.varianceMinor >= 0 ? 'Under budget' : 'Over budget'}
              value={formatMoney(Math.abs(f.varianceMinor), f.currency, { compact: true })}
              color={f.varianceMinor >= 0 ? 'success.main' : 'error.main'}
            />
          </Grid>
          <Grid size={{ xs: 6, md: 2 }}>
            <Figure
              label="Finish"
              value={f.actualFinish ? formatDate(f.actualFinish) : null}
              note={f.baselineFinish ? `baseline ${formatDate(f.baselineFinish)}` : 'no baseline'}
            />
          </Grid>
          <Grid size={{ xs: 6, md: 2 }}>
            <Figure
              label="Slip"
              value={f.slipDays === null ? null : `${f.slipDays} days`}
              color={f.slipDays > 0 ? 'warning.main' : undefined}
              note={f.earnedPct === null ? null : `${Number(f.earnedPct)}% complete`}
            />
          </Grid>
        </Grid>
        {f.contracts.length > 0 && (
          <TableContainer sx={{ mt: 2 }}>
            <Table size="small" sx={{ minWidth: 700 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Contract</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Value</TableCell>
                  <TableCell align="right">Paid</TableCell>
                  <TableCell align="right">Retention held</TableCell>
                  <TableCell>Defects liability ends</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {f.contracts.map((c) => (
                  <TableRow key={c.id}>
                    <TableCell>
                      {c.number} · {c.title}
                    </TableCell>
                    <TableCell>{c.status.toLowerCase()}</TableCell>
                    <TableCell align="right">{formatMoney(c.valueMinor)}</TableCell>
                    <TableCell align="right">{formatMoney(c.paidMinor)}</TableCell>
                    <TableCell align="right">{formatMoney(c.retentionHeldMinor)}</TableCell>
                    <TableCell>{c.defectsLiabilityEnds ? formatDate(c.defectsLiabilityEnds) : '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </CardContent>
    </Card>
  );
}

FiguresCard.propTypes = { figures: PropTypes.object.isRequired };

export default function CloseoutTab({ projectId }) {
  const { data: c, isLoading, error } = useCloseout(projectId);
  const action = useCloseoutAction(projectId);
  const [closing, setClosing] = useState(false);

  if (error) return <ErrorAlert error={error} title="Could not load closeout" />;
  if (isLoading || !c) return <Skeleton variant="rounded" height={300} />;

  if (!c.exists) {
    return (
      <>
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6">Closeout and handover</Typography>
            <Typography variant="body2" sx={{ my: 1 }}>
              When the works are complete, start closeout to work through the completion checklist, record defects for
              the defects liability period, and hand the finished assets over to the unit that will operate them. Gate
              G4 opens once the receiving officer accepts the handover.
            </Typography>
            {action.error && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {action.error.message}
              </Alert>
            )}
            {c.permissions.canStart ? (
              <Button variant="contained" loading={action.isPending} onClick={() => action.mutate({ path: '/start' })}>
                Start closeout
              </Button>
            ) : (
              <Typography variant="body2" color="text.secondary">
                The project manager or the PMO starts closeout once the project is in execution.
              </Typography>
            )}
          </CardContent>
        </Card>
        <LessonsSection closeout={c} />
      </>
    );
  }

  return (
    <>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2, justifyContent: 'space-between' }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <MetaChip map={CLOSEOUT_STATUS} value={c.status} />
          <Typography variant="body2" color="text.secondary">
            Started by {c.startedBy?.name}, {formatDateTime(c.startedAt)}
            {c.closedAt && ` · closed by ${c.closedBy?.name}, ${formatDateTime(c.closedAt)}`}
          </Typography>
        </Stack>
        {c.status === 'HANDED_OVER' && c.closeBlockers.length === 0 && c.permissions.canClose && (
          <Button variant="contained" onClick={() => setClosing(true)}>
            Close project
          </Button>
        )}
      </Stack>
      {c.warnings.map((w) => (
        <Alert key={w} severity="warning" sx={{ mb: 1 }}>
          {w}
        </Alert>
      ))}
      {c.status === 'HANDED_OVER' && c.closeBlockers.length > 0 && (
        <Alert severity="info" sx={{ mb: 2 }}>
          Before the PMO can close the project:
          <List dense disablePadding>
            {c.closeBlockers.map((b) => (
              <ListItem key={b} disableGutters sx={{ py: 0 }}>
                <ListItemText primary={`• ${b}`} />
              </ListItem>
            ))}
          </List>
        </Alert>
      )}
      {c.closureNote && (
        <Alert severity="success" sx={{ mb: 2 }}>
          Closed: {c.closureNote}
        </Alert>
      )}
      <FiguresCard figures={c.figures} />
      <ChecklistSection closeout={c} />
      <HandoverSection closeout={c} />
      <DefectsSection closeout={c} />
      <LessonsSection closeout={c} />
      <NoteDialog
        key={`close-${closing}`}
        open={closing}
        title="Close the project"
        text="The project is marked closed and leaves the active portfolio. Lessons can still be added afterwards."
        label="Closure note (optional)"
        confirm="Close project"
        pending={action.isPending}
        error={action.error}
        onClose={() => {
          action.reset();
          setClosing(false);
        }}
        onConfirm={(note) => action.mutate({ path: '/close', body: { note } }, { onSuccess: () => setClosing(false) })}
      />
    </>
  );
}

CloseoutTab.propTypes = { projectId: PropTypes.string.isRequired };
