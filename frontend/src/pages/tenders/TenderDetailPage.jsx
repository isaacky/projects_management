import { useState } from 'react';
import PropTypes from 'prop-types';
import { Link as RouterLink, useParams, useSearchParams } from 'react-router';
import {
  Alert,
  Box,
  Breadcrumbs,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  IconButton,
  Link,
  List,
  ListItem,
  ListItemText,
  Skeleton,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  Tooltip,
  Typography,
} from '@mui/material';
import LockOutlined from '@mui/icons-material/LockOutlined';
import DownloadOutlined from '@mui/icons-material/DownloadOutlined';
import CloseOutlined from '@mui/icons-material/CloseOutlined';
import UploadFileOutlined from '@mui/icons-material/UploadFileOutlined';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import TenderStatusChip from './TenderStatusChip.jsx';
import EvaluationPanel from './evaluation/EvaluationPanel.jsx';
import TenderContractButton from '../contracts/TenderContractButton.jsx';
import {
  EditTenderDialog,
  InviteDialog,
  OpenBidsDialog,
  ReadOutDialog,
  ReasonDialog,
  RecordBidDialog,
  ScansDialog,
} from './TenderDialogs.jsx';
import { bidFileUrl, documentUrl, useTender, useTenderHistory, useUninvite, useUploadDocument } from '../../api/tenders.js';
import { downloadFile } from '../../api/client.js';
import { ENVELOPE_LABEL, formatBytes, formatDateTime, timeLeft } from '../../domain/tenders.js';
import { formatMoney } from '../../utils/money.js';
import { EVALUATION_STATUS, RESERVED_GROUP } from '../../domain/evaluation.js';

function Field({ label, children }) {
  return (
    <Box sx={{ mb: 2 }}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body1" component="div">
        {children || '—'}
      </Typography>
    </Box>
  );
}

Field.propTypes = { label: PropTypes.string.isRequired, children: PropTypes.node };

const HISTORY_LABELS = {
  'tender.created': 'Tender created',
  'tender.updated': 'Edited',
  'tender.document_added': 'Document added',
  'tender.addendum_added': 'Addendum added',
  'tender.suppliers_invited': 'Suppliers invited',
  'tender.supplier_uninvited': 'Invitation removed',
  'tender.published': 'Published',
  'tender.closing_extended': 'Closing extended',
  'tender.cancelled': 'Cancelled',
  'bid.received': 'Bid received',
  'bid.withdrawn': 'Bid withdrawn',
  'tender.bids_opened': 'Bids opened',
  'bid.price_read_out': 'Price recorded',
  'bid.scans_added': 'Scans added',
  'bid.file_downloaded': 'Bid file downloaded',
  'evaluation.set_up': 'Evaluation set up',
  'evaluation.template_loaded': 'Criteria loaded from template',
  'evaluation.criteria_changed': 'Evaluation criteria changed',
  'evaluation.committee_proposed': 'Committee proposed',
  'evaluation.committee_appointed': 'Committee appointed',
  'evaluation.committee_changed': 'Committee changed',
  'evaluation.declaration': 'Declaration made',
  'evaluation.marks_saved': 'Marks saved',
  'evaluation.preliminary_closed': 'Preliminary evaluation closed',
  'evaluation.technical_closed': 'Technical evaluation closed',
  'evaluation.price_evaluated': 'Evaluated price recorded',
  'evaluation.bid_rejected': 'Bid rejected',
  'evaluation.financial_closed': 'Financial evaluation closed',
  'evaluation.report_submitted': 'Evaluation report submitted',
  'evaluation.report_signed': 'Report signed',
  'evaluation.sent_for_approval': 'Sent for approval',
  'evaluation.decision': 'Approval decision',
  'tender.awarded': 'Awarded',
  'tender.terminated': 'Terminated',
};

function safeParse(json) {
  try {
    return JSON.parse(json) ?? {};
  } catch {
    return {};
  }
}

function useDownload() {
  const [error, setError] = useState(null);
  const run = (url, name) => {
    setError(null);
    downloadFile(url, name).catch(setError);
  };
  return [run, error];
}

/** The bid register: one row per bid, sealed parts shown as locked. */
function BidsTable({ tender, onAction }) {
  const [download, downloadError] = useDownload();
  const can = tender.permissions;
  if (!tender.bids.length) {
    return (
      <Typography variant="body2" color="text.secondary">
        No bids recorded yet.
      </Typography>
    );
  }
  return (
    <>
      {downloadError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {downloadError.message}
        </Alert>
      )}
      <TableContainer>
        <Table size="small" sx={{ minWidth: 760 }}>
          <TableHead>
            <TableRow>
              <TableCell>Receipt</TableCell>
              <TableCell>Supplier</TableCell>
              <TableCell>Received</TableCell>
              <TableCell>Envelopes and files</TableCell>
              <TableCell align="right">Read-out price</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {tender.bids.map((b) => {
              const withdrawn = b.status === 'WITHDRAWN';
              return (
                <TableRow key={b.id} sx={{ opacity: withdrawn ? 0.55 : 1, verticalAlign: 'top' }}>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {b.receiptNo}
                    </Typography>
                    <Tooltip title={`Receipt fingerprint (SHA-256): ${b.receiptHash}`}>
                      <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                        {b.receiptHash.slice(0, 12)}…
                      </Typography>
                    </Tooltip>
                    {withdrawn && (
                      <Chip size="small" label="Withdrawn" sx={{ display: 'flex', mt: 0.5, width: 'fit-content' }} />
                    )}
                    {b.disqualifiedStage && (
                      <Tooltip title={b.disqualifiedReason ?? ''}>
                        <Chip
                          size="small"
                          color="error"
                          variant="outlined"
                          label={`Out at ${b.disqualifiedStage.toLowerCase()}`}
                          sx={{ display: 'flex', mt: 0.5, width: 'fit-content' }}
                        />
                      </Tooltip>
                    )}
                    {tender.awardedBidId === b.id && (
                      <Chip size="small" color="success" label="Awarded" sx={{ display: 'flex', mt: 0.5, width: 'fit-content' }} />
                    )}
                  </TableCell>
                  <TableCell>
                    {b.supplier.name}
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                      {b.supplierKraPin}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    {formatDateTime(b.receivedAt)}
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                      by {b.receivedBy.name}
                    </Typography>
                    {b.deliveryNote && (
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                        {b.deliveryNote}
                      </Typography>
                    )}
                    {withdrawn && (
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                        Withdrawn {formatDateTime(b.withdrawnAt)}: {b.withdrawReason}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    {b.envelopes.map((env) => (
                      <Box key={env.envelope} sx={{ mb: 1 }}>
                        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                          {env.sealed && <LockOutlined fontSize="inherit" color="action" />}
                          <Typography variant="body2">
                            {ENVELOPE_LABEL[env.envelope]}: {env.fileCount} file{env.fileCount === 1 ? '' : 's'}
                            {env.sealed ? ' (sealed)' : ''}
                          </Typography>
                        </Stack>
                        {b.files
                          .filter((f) => f.envelope === env.envelope && !f.sealed)
                          .map((f) => (
                            <Stack key={f.id} direction="row" spacing={0.5} sx={{ alignItems: 'center', pl: 2 }}>
                              {can.canReadBidFiles ? (
                                <Link
                                  component="button"
                                  type="button"
                                  variant="body2"
                                  onClick={() => download(bidFileUrl(tender.id, b.id, f.id), f.fileName)}
                                >
                                  {f.fileName}
                                </Link>
                              ) : (
                                <Typography variant="body2">{f.fileName}</Typography>
                              )}
                              <Typography variant="caption" color="text.secondary">
                                {formatBytes(f.sizeBytes)}
                                {f.addedAfterOpening ? ' · scan added after opening' : ''}
                              </Typography>
                            </Stack>
                          ))}
                      </Box>
                    ))}
                  </TableCell>
                  <TableCell align="right">
                    {b.readOutPriceMinor != null ? (
                      <>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {formatMoney(b.readOutPriceMinor, tender.currency)}
                        </Typography>
                        {b.readOutNote && (
                          <Typography variant="caption" color="text.secondary">
                            {b.readOutNote}
                          </Typography>
                        )}
                      </>
                    ) : b.envelopes.every((env) => !env.sealed) ? (
                      '—'
                    ) : (
                      <Tooltip title="Sealed until the price is opened">
                        <LockOutlined fontSize="small" color="action" />
                      </Tooltip>
                    )}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {!withdrawn && can.canReadOut && !b.envelopes.some((env) => env.sealed) && (
                      <Button size="small" onClick={() => onAction({ type: 'readout', bid: b })}>
                        Price
                      </Button>
                    )}
                    {!withdrawn && can.canAddScans && (
                      <Tooltip title="Add scans of a paper bid">
                        <IconButton size="small" onClick={() => onAction({ type: 'scans', bid: b })}>
                          <UploadFileOutlined fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    )}
                    {!withdrawn && can.canRecordBids && (
                      <Button size="small" color="error" onClick={() => onAction({ type: 'withdraw', bid: b })}>
                        Withdraw
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
    </>
  );
}

BidsTable.propTypes = { tender: PropTypes.object.isRequired, onAction: PropTypes.func.isRequired };

export default function TenderDetailPage() {
  const { id } = useParams();
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') === 'evaluation' ? 'evaluation' : 'tender';
  const { data: t, isLoading, error } = useTender(id);
  const { data: history } = useTenderHistory(id);
  const upload = useUploadDocument(id);
  const uninvite = useUninvite(id);
  const [download, downloadError] = useDownload();
  const [dialog, setDialog] = useState(null);

  if (error) return <ErrorAlert error={error} title="Could not load tender" />;
  if (isLoading || !t) return <Skeleton variant="rounded" height={320} />;

  const can = t.permissions;
  const serverNow = new Date(t.serverTime).getTime();
  const remaining = t.closingAt ? timeLeft(t.closingAt, serverNow) : null;
  const liveBids = t.bids.filter((b) => b.status === 'RECEIVED').length;
  const openLabel =
    t.nextEnvelopeToOpen === 'SINGLE'
      ? 'Open bids'
      : t.nextEnvelopeToOpen
        ? `Open ${ENVELOPE_LABEL[t.nextEnvelopeToOpen].toLowerCase()} envelopes`
        : null;

  const chooseDocument = (e) => {
    const f = e.target.files?.[0];
    e.target.value = '';
    if (f) upload.mutate(f);
  };

  return (
    <>
      <Breadcrumbs sx={{ mb: 1 }}>
        <Link component={RouterLink} to="/tenders" underline="hover" color="inherit">
          Tenders
        </Link>
        <Typography color="text.primary">{t.number}</Typography>
      </Breadcrumbs>
      <PageHeader
        title={t.title}
        subtitle={
          <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography variant="body2" color="text.secondary">
              {t.number} · {t.methodName}
            </Typography>
            <TenderStatusChip tender={t} />
            {t.reservedGroup && (
              <Chip size="small" color="secondary" variant="outlined" label={`Reserved: ${RESERVED_GROUP[t.reservedGroup]}`} />
            )}
            {t.evaluationStatus && !['AWARDED', 'TERMINATED'].includes(t.evaluationStatus) && (
              <Chip size="small" variant="outlined" label={EVALUATION_STATUS[t.evaluationStatus].label} />
            )}
            {t.acceptingBids && remaining && <Chip size="small" label={`Closes in ${remaining}`} color="info" />}
          </Stack>
        }
        actions={
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
            {can.canEdit && <Button onClick={() => setDialog({ type: 'edit' })}>Edit</Button>}
            {can.canExtend && <Button onClick={() => setDialog({ type: 'extend' })}>Extend closing</Button>}
            {can.canCancel && (
              <Button color="error" onClick={() => setDialog({ type: 'cancel' })}>
                Cancel tender
              </Button>
            )}
            {can.canPublish && (
              <Button variant="contained" onClick={() => setDialog({ type: 'publish' })}>
                Publish
              </Button>
            )}
            {can.canRecordBids && (
              <Button variant="contained" onClick={() => setDialog({ type: 'bid' })}>
                Record bid
              </Button>
            )}
            {can.canOpen && openLabel && (
              <Button variant="contained" color="warning" onClick={() => setDialog({ type: 'open' })}>
                {openLabel}
              </Button>
            )}
          </Stack>
        }
      />

      {t.status === 'CANCELLED' && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          Cancelled: {t.cancelledReason}
        </Alert>
      )}
      {t.status === 'TERMINATED' && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          Terminated without award: {t.cancelledReason}
        </Alert>
      )}
      {t.status === 'AWARDED' && (
        <Alert
          severity="success"
          sx={{ mb: 3 }}
          action={
            <Stack direction="row" spacing={1}>
              <Button color="inherit" size="small" component={RouterLink} to={`/tenders/${t.id}/letters`}>
                Letters
              </Button>
              <TenderContractButton tender={t} />
            </Stack>
          }
        >
          Awarded to {t.bids.find((b) => b.id === t.awardedBidId)?.supplier.name} at{' '}
          {formatMoney(t.awardedAmountMinor, t.currency)} on {formatDateTime(t.awardedAt)}. Standstill period ends{' '}
          {formatDateTime(t.standstillEndsAt)}.
        </Alert>
      )}
      {t.openingBlockedReason && t.nextEnvelopeToOpen === 'FINANCIAL' && (
        <Alert severity="info" icon={<LockOutlined />} sx={{ mb: 3 }}>
          {t.openingBlockedReason}
        </Alert>
      )}
      {t.closed && t.status === 'PUBLISHED' && (
        <Alert severity="info" icon={<LockOutlined />} sx={{ mb: 3 }}>
          Bidding closed {formatDateTime(t.closingAt)}. {liveBids} bid{liveBids === 1 ? '' : 's'} remain sealed until
          they are opened with at least {t.openingQuorum} people present.
        </Alert>
      )}
      {t.status === 'TECHNICAL_OPENED' && !t.openingBlockedReason && (
        <Alert severity="info" icon={<LockOutlined />} sx={{ mb: 3 }}>
          The technical evaluation is closed. Open the financial envelopes of the bids that passed it.
        </Alert>
      )}
      {upload.error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {upload.error.message}
        </Alert>
      )}
      {(downloadError || uninvite.error) && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {(downloadError || uninvite.error).message}
        </Alert>
      )}

      {['TECHNICAL_OPENED', 'OPENED', 'AWARDED', 'TERMINATED'].includes(t.status) && (
        <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
          <Tabs
            value={tab}
            onChange={(_e, v) => setParams(v === 'evaluation' ? { tab: 'evaluation' } : {}, { replace: true })}
          >
            <Tab value="tender" label="Tender and bids" />
            <Tab value="evaluation" label="Evaluation" />
          </Tabs>
        </Box>
      )}

      {tab === 'evaluation' ? (
        <EvaluationPanel tender={t} />
      ) : (
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 8 }}>
          <Card sx={{ mb: 3 }}>
            <CardContent>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Field label="Requisition">
                    <Link component={RouterLink} to={`/requisitions/${t.requisition.id}`}>
                      {t.requisitionNumber} · {t.requisition.name}
                    </Link>
                  </Field>
                  <Field label="Project">
                    <Link component={RouterLink} to={`/projects/${t.project.id}`}>
                      {t.projectCode} · {t.project.name}
                    </Link>
                  </Field>
                  <Field label="Estimated value">{formatMoney(t.estimatedValueMinor, t.currency)}</Field>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Field label="Closing">{formatDateTime(t.closingAt)}</Field>
                  <Field label="Envelopes">
                    {t.twoEnvelope ? 'Two: technical, then financial' : 'One (technical and financial together)'}
                  </Field>
                  <Field label="Rules">
                    Bid period at least {t.minBidPeriodDays} days · opening quorum {t.openingQuorum} ·{' '}
                    {t.invitedOnly ? 'invited suppliers only' : 'open to all registered suppliers'}
                    {t.reservedGroup ? ` · reserved for ${RESERVED_GROUP[t.reservedGroup].toLowerCase()} (valid AGPO certificate)` : ''}
                  </Field>
                </Grid>
              </Grid>
              <Field label="Description">{t.description}</Field>
            </CardContent>
          </Card>

          <Card sx={{ mb: 3 }}>
            <CardContent>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="h6">Bids ({liveBids})</Typography>
              </Stack>
              <BidsTable tender={t} onAction={setDialog} />
            </CardContent>
          </Card>

          {t.openings.length > 0 && (
            <Card>
              <CardContent>
                <Typography variant="h6" sx={{ mb: 1 }}>
                  Opening record
                </Typography>
                {t.openings.map((o) => (
                  <Box key={o.id} sx={{ mb: 2 }}>
                    <Typography variant="subtitle2">
                      {o.envelope === 'SINGLE' ? 'Bids' : `${ENVELOPE_LABEL[o.envelope]} envelopes`} opened{' '}
                      {formatDateTime(o.openedAt)} by {o.openedBy.name}
                      {o.venue ? `, ${o.venue}` : ''}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Present: {o.attendees.map((a) => a.name).join(', ')}
                    </Typography>
                    {o.notes && (
                      <Typography variant="body2" color="text.secondary">
                        {o.notes}
                      </Typography>
                    )}
                  </Box>
                ))}
              </CardContent>
            </Card>
          )}
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          <Card sx={{ mb: 3 }}>
            <CardContent>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <Typography variant="h6">Documents</Typography>
                {(can.canEdit || can.canExtend) && (
                  <Button component="label" size="small" loading={upload.isPending}>
                    {t.status === 'DRAFT' ? 'Add' : 'Add addendum'}
                    <input hidden type="file" onChange={chooseDocument} />
                  </Button>
                )}
              </Stack>
              {!t.documents.length && (
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                  Attach the tender document before publishing.
                </Typography>
              )}
              <List dense>
                {t.documents.map((d) => (
                  <ListItem
                    key={d.id}
                    disableGutters
                    secondaryAction={
                      <IconButton
                        edge="end"
                        size="small"
                        aria-label={`Download ${d.fileName}`}
                        onClick={() => download(documentUrl(t.id, d.id), d.fileName)}
                      >
                        <DownloadOutlined fontSize="small" />
                      </IconButton>
                    }
                  >
                    <ListItemText
                      primary={d.fileName}
                      secondary={`${formatBytes(d.sizeBytes)} · ${formatDateTime(d.uploadedAt)}`}
                    />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>

          <Card sx={{ mb: 3 }}>
            <CardContent>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <Typography variant="h6">Invited suppliers</Typography>
                {can.canInvite && (
                  <Button size="small" onClick={() => setDialog({ type: 'invite' })}>
                    Invite
                  </Button>
                )}
              </Stack>
              {!t.invitedSuppliers.length && (
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                  {t.invitedOnly
                    ? 'This method is not advertised: invite the suppliers who may bid.'
                    : 'Open to all registered suppliers. Invitations are optional.'}
                </Typography>
              )}
              <List dense>
                {t.invitedSuppliers.map((s) => (
                  <ListItem
                    key={s.id}
                    disableGutters
                    secondaryAction={
                      can.canEdit ? (
                        <IconButton
                          edge="end"
                          size="small"
                          aria-label={`Remove ${s.name}`}
                          onClick={() => uninvite.mutate(s.id)}
                        >
                          <CloseOutlined fontSize="small" />
                        </IconButton>
                      ) : null
                    }
                  >
                    <ListItemText primary={s.name} />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <Typography variant="h6">History</Typography>
              <List dense>
                {history?.map((h) => {
                  const d = safeParse(h.payload);
                  const detail = d.receiptNo ?? d.file ?? d.reason ?? d.envelope ?? null;
                  return (
                    <ListItem key={h.hash} disableGutters divider>
                      <ListItemText
                        primary={`${HISTORY_LABELS[h.action] ?? h.action}${detail ? `: ${detail}` : ''}`}
                        secondary={`${formatDateTime(h.occurredAt)} · ${h.actor.replace(/\s*<.*>$/, '')}`}
                      />
                    </ListItem>
                  );
                })}
              </List>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
      )}

      {dialog?.type === 'edit' && <EditTenderDialog tender={t} onClose={() => setDialog(null)} />}
      {dialog?.type === 'invite' && <InviteDialog tender={t} onClose={() => setDialog(null)} />}
      {dialog?.type === 'bid' && <RecordBidDialog tender={t} onClose={() => setDialog(null)} />}
      {dialog?.type === 'open' && <OpenBidsDialog tender={t} onClose={() => setDialog(null)} />}
      {dialog?.type === 'readout' && <ReadOutDialog tender={t} bid={dialog.bid} onClose={() => setDialog(null)} />}
      {dialog?.type === 'scans' && <ScansDialog tender={t} bid={dialog.bid} onClose={() => setDialog(null)} />}
      {['publish', 'cancel', 'extend', 'withdraw'].includes(dialog?.type) && (
        <ReasonDialog tender={t} action={dialog.type} bid={dialog.bid} onClose={() => setDialog(null)} />
      )}
    </>
  );
}
