import { useState } from 'react';
import PropTypes from 'prop-types';
import { Link as RouterLink, useParams } from 'react-router';
import {
  Alert,
  AlertTitle,
  Box,
  Breadcrumbs,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  IconButton,
  LinearProgress,
  Link,
  List,
  ListItem,
  ListItemText,
  Rating,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import DownloadOutlined from '@mui/icons-material/DownloadOutlined';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import ContractStatusChip from './ContractStatusChip.jsx';
import {
  AcceptMilestoneDialog,
  CompleteDialog,
  DocumentDialog,
  EditContractDialog,
  InvoiceActionDialog,
  InvoiceDialog,
  ReasonDialog,
  ReviewDialog,
  ScheduleDialog,
  SecurityActionDialog,
  SecurityDialog,
  SignDialog,
  VariationDecisionDialog,
  VariationDialog,
} from './ContractDialogs.jsx';
import { contractDocumentUrl, useContract, useContractAction, useContractHistory } from '../../api/contracts.js';
import { downloadFile } from '../../api/client.js';
import {
  DOCUMENT_KIND,
  INVOICE_STATUS,
  MILESTONE_STATUS,
  REVIEW_STATUS,
  SECURITY_FORM,
  SECURITY_KIND,
  SECURITY_STATUS,
  bpToPercent,
  formatDate,
  share,
} from '../../domain/contracts.js';
import { formatBytes, formatDateTime } from '../../domain/tenders.js';
import { ROLE_LABEL } from '../../domain/evaluation.js';
import { formatMoney } from '../../utils/money.js';

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

function Section({ title, action, children }) {
  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1, gap: 1 }}>
          <Typography variant="h6">{title}</Typography>
          {action}
        </Stack>
        {children}
      </CardContent>
    </Card>
  );
}

Section.propTypes = { title: PropTypes.string.isRequired, action: PropTypes.node, children: PropTypes.node };

const HISTORY_LABELS = {
  'contract.created': 'Contract drafted',
  'contract.updated': 'Details changed',
  'contract.schedule_changed': 'Payment schedule changed',
  'contract.review_lodged': 'Request for review lodged',
  'contract.review_dismissed': 'Review dismissed',
  'contract.review_upheld': 'Review upheld',
  'contract.cancelled': 'Draft cancelled',
  'contract.signed': 'Signed',
  'contract.security_received': 'Security received',
  'contract.security_extended': 'Security extended',
  'contract.security_released': 'Security released',
  'contract.security_called': 'Security called',
  'contract.document_added': 'Document added',
  'contract.milestone_accepted': 'Milestone accepted',
  'contract.invoice_received': 'Invoice received',
  'contract.invoice_certified': 'Payment certified',
  'contract.payment_approved': 'Payment approved',
  'contract.payment_made': 'Payment made',
  'contract.invoice_rejected': 'Invoice rejected',
  'contract.retention_release': 'Retention release requested',
  'contract.variation_requested': 'Variation requested',
  'contract.variation_decision': 'Variation decision',
  'contract.completed': 'Completed',
  'contract.terminated': 'Terminated',
};

function safeParse(json) {
  try {
    return JSON.parse(json) ?? {};
  } catch {
    return {};
  }
}

function MoneyBar({ label, value, total, currency }) {
  return (
    <Box sx={{ mb: 1.5 }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
        <Typography variant="body2">{label}</Typography>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          {formatMoney(value, currency)} · {share(value, total)}%
        </Typography>
      </Stack>
      <LinearProgress variant="determinate" value={Math.min(100, share(value, total))} />
    </Box>
  );
}

MoneyBar.propTypes = {
  label: PropTypes.string.isRequired,
  value: PropTypes.number.isRequired,
  total: PropTypes.number.isRequired,
  currency: PropTypes.string.isRequired,
};

export default function ContractDetailPage() {
  const { id } = useParams();
  const { data: c, isLoading, error } = useContract(id);
  const { data: history } = useContractHistory(id);
  const release = useContractAction(id);
  const [dialog, setDialog] = useState(null);
  const [downloadError, setDownloadError] = useState(null);
  const close = () => setDialog(null);

  if (error) return <ErrorAlert error={error} title="Could not load contract" />;
  if (isLoading || !c) return <Skeleton variant="rounded" height={320} />;

  const can = c.permissions;
  const t = c.totals;
  const download = (doc) => {
    setDownloadError(null);
    downloadFile(contractDocumentUrl(c.id, doc.id), doc.fileName).catch(setDownloadError);
  };
  const docsFor = (refId) => c.documents.filter((d) => d.refId === refId);
  const docLinks = (refId) =>
    docsFor(refId).map((d) => (
      <Link key={d.id} component="button" type="button" variant="caption" onClick={() => download(d)} sx={{ display: 'block' }}>
        {d.fileName}
      </Link>
    ));

  return (
    <>
      <Breadcrumbs sx={{ mb: 1 }}>
        <Link component={RouterLink} to="/contracts" underline="hover" color="inherit">
          Contracts
        </Link>
        <Typography color="text.primary">{c.number}</Typography>
      </Breadcrumbs>
      <PageHeader
        title={c.title}
        subtitle={
          <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography variant="body2" color="text.secondary">
              {c.number} · {c.supplier.name}
            </Typography>
            <ContractStatusChip status={c.status} />
            {c.reviewStatus !== 'NONE' && <Chip size="small" variant="outlined" label={REVIEW_STATUS[c.reviewStatus]} />}
          </Stack>
        }
        actions={
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
            {can.canEdit && <Button onClick={() => setDialog({ type: 'edit' })}>Edit details</Button>}
            {can.canRecordReview && <Button onClick={() => setDialog({ type: 'review' })}>Review request</Button>}
            {can.canCancel && (
              <Button color="error" onClick={() => setDialog({ type: 'cancel' })}>
                Cancel draft
              </Button>
            )}
            {can.canSign && (
              <Button variant="contained" disabled={c.signingProblems.length > 0} onClick={() => setDialog({ type: 'sign' })}>
                Record signature
              </Button>
            )}
            {can.canRecordInvoice && (
              <Button variant="contained" onClick={() => setDialog({ type: 'invoice' })}>
                Record invoice
              </Button>
            )}
            {can.canRequestVariation && <Button onClick={() => setDialog({ type: 'variation' })}>Request variation</Button>}
            {can.canComplete && <Button onClick={() => setDialog({ type: 'complete' })}>Complete</Button>}
            {can.canReleaseRetention && (
              <Button onClick={() => release.mutate({ action: 'retention-release' })} loading={release.isPending}>
                Release retention
              </Button>
            )}
            {can.canTerminate && (
              <Button color="error" onClick={() => setDialog({ type: 'terminate' })}>
                Terminate
              </Button>
            )}
          </Stack>
        }
      />

      {c.status === 'DRAFT' && c.signingProblems.length > 0 && (
        <Alert severity="info" sx={{ mb: 2 }}>
          <AlertTitle>Before signing</AlertTitle>
          <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
            {c.signingProblems.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </Box>
        </Alert>
      )}
      {c.status === 'DRAFT' && c.signingProblems.length === 0 && can.canSign && (
        <Alert severity="success" sx={{ mb: 2 }}>
          Ready to sign.
        </Alert>
      )}
      {c.warnings.map((w) => (
        <Alert key={w} severity="warning" sx={{ mb: 2 }}>
          {w}
        </Alert>
      ))}
      {c.endedReason && (
        <Alert severity={c.status === 'TERMINATED' ? 'error' : 'warning'} sx={{ mb: 2 }}>
          {c.endedReason}
        </Alert>
      )}
      {(release.error || downloadError) && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {(release.error || downloadError).message}
        </Alert>
      )}

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 8 }}>
          <Section title="Contract">
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Field label="Supplier">
                  {c.supplier.name}
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    {c.supplierKraPin}
                  </Typography>
                </Field>
                <Field label="Tender">
                  <Link component={RouterLink} to={`/tenders/${c.tenderId}?tab=evaluation`}>
                    {c.tenderNumber}
                  </Link>
                </Field>
                <Field label="Project">
                  <Link component={RouterLink} to={`/projects/${c.project.id}`}>
                    {c.projectCode} · {c.project.name}
                  </Link>
                </Field>
                <Field label="Contract manager">{c.contractManager?.name}</Field>
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Field label="Value">
                  {formatMoney(c.currentValueMinor, c.currency)}
                  {c.currentValueMinor !== c.originalValueMinor && (
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                      Original {formatMoney(c.originalValueMinor, c.currency)}
                    </Typography>
                  )}
                </Field>
                <Field label="Period">
                  {formatDate(c.startDate)} to {formatDate(c.endDate)}
                  {c.originalEndDate && c.endDate !== c.originalEndDate && (
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                      Originally to {formatDate(c.originalEndDate)}
                    </Typography>
                  )}
                </Field>
                <Field label="Terms">
                  Retention {bpToPercent(c.retentionBp)} · defects liability {c.defectsLiabilityDays} days · performance
                  security {formatMoney(c.performanceSecurityMinor, c.currency)}
                </Field>
                <Field label="Signed">
                  {c.signedAt
                    ? `${formatDate(c.signedAt)} by ${c.supplierSignatory} and ${c.entitySignatory}`
                    : `Not before ${formatDateTime(c.standstillEndsAt)} (standstill)`}
                </Field>
              </Grid>
            </Grid>
            {c.rating && (
              <Box sx={{ mt: 1 }}>
                <Typography variant="subtitle2">Supplier performance</Typography>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3}>
                  {[
                    ['Quality', c.rating.quality],
                    ['Timeliness', c.rating.timeliness],
                    ['Compliance', c.rating.compliance],
                  ].map(([label, v]) => (
                    <Stack key={label} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <Typography variant="body2">{label}</Typography>
                      <Rating size="small" value={v} readOnly />
                    </Stack>
                  ))}
                </Stack>
                {c.rating.comment && (
                  <Typography variant="body2" color="text.secondary">
                    {c.rating.comment} ({c.rating.ratedBy?.name}, completed {formatDate(c.completedAt)})
                  </Typography>
                )}
              </Box>
            )}
          </Section>

          <Section
            title="Payment schedule"
            action={
              can.canEditSchedule && (
                <Button size="small" onClick={() => setDialog({ type: 'schedule' })}>
                  Edit
                </Button>
              )
            }
          >
            <TableContainer>
              <Table size="small" sx={{ minWidth: 600 }}>
                <TableHead>
                  <TableRow>
                    <TableCell>Milestone</TableCell>
                    <TableCell>Due</TableCell>
                    <TableCell align="right">Value</TableCell>
                    <TableCell align="right">Invoiced</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {c.milestones.map((m) => (
                    <TableRow key={m.id} sx={{ verticalAlign: 'top', opacity: m.status === 'CANCELLED' ? 0.5 : 1 }}>
                      <TableCell>
                        {m.title}
                        {m.acceptanceNote && (
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                            Accepted {formatDate(m.acceptedAt)} by {m.acceptedBy?.name}: {m.acceptanceNote}
                          </Typography>
                        )}
                        {docLinks(m.id)}
                      </TableCell>
                      <TableCell sx={{ color: m.overdue ? 'error.main' : undefined }}>{formatDate(m.dueDate)}</TableCell>
                      <TableCell align="right">{formatMoney(m.amountMinor, c.currency)}</TableCell>
                      <TableCell align="right">{formatMoney(m.invoicedMinor, c.currency)}</TableCell>
                      <TableCell>
                        <Chip size="small" label={MILESTONE_STATUS[m.status].label} color={MILESTONE_STATUS[m.status].color} />
                      </TableCell>
                      <TableCell align="right">
                        {can.canAcceptMilestones && m.status === 'PLANNED' && (
                          <Button size="small" onClick={() => setDialog({ type: 'accept', milestone: m })}>
                            Accept
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            {!c.milestones.length && (
              <Typography variant="body2" color="text.secondary">
                No milestones yet.
              </Typography>
            )}
            <Typography variant="caption" color="text.secondary">
              Scheduled {formatMoney(c.scheduledMinor, c.currency)} of {formatMoney(c.currentValueMinor, c.currency)}
            </Typography>
          </Section>

          <Section title="Invoices and payment certificates">
            <TableContainer>
              <Table size="small" sx={{ minWidth: 720 }}>
                <TableHead>
                  <TableRow>
                    <TableCell>Certificate</TableCell>
                    <TableCell align="right">Gross</TableCell>
                    <TableCell align="right">Retention</TableCell>
                    <TableCell align="right">Payable</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {c.invoices.map((i) => (
                    <TableRow key={i.id} sx={{ verticalAlign: 'top', opacity: i.status === 'REJECTED' ? 0.55 : 1 }}>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {i.kind === 'RETENTION_RELEASE' ? `${i.certificateNo} · retention release` : `${i.certificateNo} · ${i.supplierRef ?? 'invoice'}`}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                          {formatDate(i.invoiceDate)}
                          {i.milestoneTitle ? ` · ${i.milestoneTitle}` : ''}
                        </Typography>
                        {i.certifiedBy && (
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                            Certified by {i.certifiedBy.name}: {i.certificateNote}
                          </Typography>
                        )}
                        {i.approvedBy && (
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                            Approved by {i.approvedBy.name}
                          </Typography>
                        )}
                        {i.paidOn && (
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                            Paid {formatDate(i.paidOn)} · {i.paymentRef}
                          </Typography>
                        )}
                        {i.rejectedReason && (
                          <Typography variant="caption" color="error" sx={{ display: 'block' }}>
                            Rejected: {i.rejectedReason}
                          </Typography>
                        )}
                        {docLinks(i.id)}
                      </TableCell>
                      <TableCell align="right">{formatMoney(i.grossMinor, c.currency)}</TableCell>
                      <TableCell align="right">{formatMoney(i.retentionMinor, c.currency)}</TableCell>
                      <TableCell align="right">{formatMoney(i.netMinor, c.currency)}</TableCell>
                      <TableCell>
                        <Chip size="small" label={INVOICE_STATUS[i.status].label} color={INVOICE_STATUS[i.status].color} />
                      </TableCell>
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                        {i.canCertify && (
                          <Button size="small" onClick={() => setDialog({ type: 'invoiceAction', invoice: i, action: 'certify' })}>
                            Certify
                          </Button>
                        )}
                        {i.canApprove && (
                          <Button size="small" onClick={() => setDialog({ type: 'invoiceAction', invoice: i, action: 'approve' })}>
                            Approve
                          </Button>
                        )}
                        {i.canPay && (
                          <Button size="small" onClick={() => setDialog({ type: 'invoiceAction', invoice: i, action: 'pay' })}>
                            Record payment
                          </Button>
                        )}
                        {i.canReject && (
                          <Button size="small" color="error" onClick={() => setDialog({ type: 'invoiceAction', invoice: i, action: 'reject' })}>
                            Reject
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            {!c.invoices.length && (
              <Typography variant="body2" color="text.secondary">
                No invoices yet.
              </Typography>
            )}
          </Section>

          <Section title="Variations">
            {!c.variations.length && (
              <Typography variant="body2" color="text.secondary">
                No variations. Increases may total {formatMoney(c.variationHeadroomMinor, c.currency)} more (25% of the
                original price).
              </Typography>
            )}
            <List dense>
              {c.variations.map((v) => (
                <ListItem
                  key={v.id}
                  disableGutters
                  divider
                  secondaryAction={
                    v.canDecide && (
                      <Button size="small" variant="contained" onClick={() => setDialog({ type: 'decideVariation', variation: v })}>
                        Decide
                      </Button>
                    )
                  }
                >
                  <ListItemText
                    primary={
                      <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                        <span>
                          Variation {v.number}:{' '}
                          {v.valueChangeMinor ? `${v.valueChangeMinor > 0 ? '+' : ''}${formatMoney(v.valueChangeMinor, c.currency)}` : 'time only'}
                          {v.newEndDate ? ` · to ${formatDate(v.newEndDate)}` : ''}
                        </span>
                        <Chip
                          size="small"
                          label={v.status.toLowerCase()}
                          color={v.status === 'APPROVED' ? 'success' : v.status === 'PENDING' ? 'warning' : 'default'}
                        />
                      </Stack>
                    }
                    secondary={
                      <>
                        {v.reason} · requested by {v.requestedBy?.name}
                        {v.steps.map((s) => (
                          <Box component="span" key={s.stepOrder} sx={{ display: 'block' }}>
                            {s.stepOrder}. {ROLE_LABEL[s.approverRole] ?? s.approverRole}: {s.status.toLowerCase()}
                            {s.decidedBy ? ` by ${s.decidedBy.name}` : ''}
                            {s.note ? `: ${s.note}` : ''}
                          </Box>
                        ))}
                      </>
                    }
                  />
                </ListItem>
              ))}
            </List>
          </Section>
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          <Section title="Payments">
            <MoneyBar label="Invoiced" value={t.invoicedMinor} total={c.currentValueMinor} currency={c.currency} />
            <MoneyBar label="Certified" value={t.certifiedMinor} total={c.currentValueMinor} currency={c.currency} />
            <MoneyBar label="Paid to supplier" value={t.paidMinor} total={c.currentValueMinor} currency={c.currency} />
            <Typography variant="body2">
              Retention held: <strong>{formatMoney(t.retentionHeldMinor, c.currency)}</strong>
            </Typography>
            <Typography variant="body2">
              Approved, awaiting payment: <strong>{formatMoney(t.awaitingPaymentMinor, c.currency)}</strong>
            </Typography>
            {c.defectsLiabilityEnds && (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                Defects liability period ends {formatDate(c.defectsLiabilityEnds)}
              </Typography>
            )}
          </Section>

          <Section
            title="Securities"
            action={
              can.canAddSecurity && (
                <Button size="small" onClick={() => setDialog({ type: 'security' })}>
                  Add
                </Button>
              )
            }
          >
            {!c.securities.length && (
              <Typography variant="body2" color="text.secondary">
                {c.performanceSecurityMinor > 0
                  ? `A performance security of ${formatMoney(c.performanceSecurityMinor, c.currency)} is required before signing.`
                  : 'None recorded.'}
              </Typography>
            )}
            <List dense>
              {c.securities.map((s) => (
                <ListItem
                  key={s.id}
                  disableGutters
                  divider
                  secondaryAction={
                    can.canManageSecurities && s.status === 'HELD' ? (
                      <Button size="small" onClick={() => setDialog({ type: 'securityAction', security: s })}>
                        Update
                      </Button>
                    ) : null
                  }
                >
                  <ListItemText
                    primary={
                      <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                        <span>{SECURITY_KIND[s.kind]}</span>
                        <Chip
                          size="small"
                          label={SECURITY_STATUS[s.status]}
                          color={s.expiringSoon ? 'warning' : s.status === 'HELD' ? 'success' : 'default'}
                        />
                      </Stack>
                    }
                    secondary={
                      <>
                        {formatMoney(s.amountMinor, c.currency)} · {SECURITY_FORM[s.form]} {s.reference} from {s.issuer} ·
                        expires {formatDate(s.expiryDate)}
                        {s.closedNote ? ` · ${s.closedNote}` : ''}
                        {docLinks(s.id)}
                      </>
                    }
                  />
                </ListItem>
              ))}
            </List>
          </Section>

          <Section
            title="Documents"
            action={
              can.canUploadDocuments && (
                <Button size="small" onClick={() => setDialog({ type: 'document' })}>
                  Add
                </Button>
              )
            }
          >
            {!c.documents.length && (
              <Typography variant="body2" color="text.secondary">
                {c.status === 'DRAFT' ? 'Upload the signed contract before recording the signature.' : 'No documents.'}
              </Typography>
            )}
            <List dense>
              {c.documents.map((d) => (
                <ListItem
                  key={d.id}
                  disableGutters
                  secondaryAction={
                    <Tooltip title="Download">
                      <IconButton edge="end" size="small" aria-label={`Download ${d.fileName}`} onClick={() => download(d)}>
                        <DownloadOutlined fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  }
                >
                  <ListItemText
                    primary={d.fileName}
                    secondary={`${DOCUMENT_KIND[d.kind]} · ${formatBytes(d.sizeBytes)} · ${formatDateTime(d.uploadedAt)}`}
                  />
                </ListItem>
              ))}
            </List>
          </Section>

          <Section title="History">
            <List dense>
              {history?.map((h) => {
                const p = safeParse(h.payload);
                const detail = p.milestone ?? p.reason ?? p.reference ?? (p.certificate ? `certificate ${p.certificate}` : null);
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
          </Section>
        </Grid>
      </Grid>

      {dialog?.type === 'edit' && <EditContractDialog contract={c} onClose={close} />}
      {dialog?.type === 'schedule' && <ScheduleDialog contract={c} onClose={close} />}
      {dialog?.type === 'review' && <ReviewDialog contract={c} onClose={close} />}
      {dialog?.type === 'cancel' && <ReasonDialog contract={c} action="cancel" onClose={close} />}
      {dialog?.type === 'terminate' && <ReasonDialog contract={c} action="terminate" onClose={close} />}
      {dialog?.type === 'sign' && <SignDialog contract={c} onClose={close} />}
      {dialog?.type === 'security' && <SecurityDialog contract={c} onClose={close} />}
      {dialog?.type === 'securityAction' && <SecurityActionDialog contract={c} security={dialog.security} onClose={close} />}
      {dialog?.type === 'document' && (
        <DocumentDialog contract={c} defaultKind={c.status === 'DRAFT' ? 'SIGNED_CONTRACT' : 'OTHER'} onClose={close} />
      )}
      {dialog?.type === 'accept' && <AcceptMilestoneDialog contract={c} milestone={dialog.milestone} onClose={close} />}
      {dialog?.type === 'invoice' && <InvoiceDialog contract={c} onClose={close} />}
      {dialog?.type === 'invoiceAction' && (
        <InvoiceActionDialog contract={c} invoice={dialog.invoice} action={dialog.action} onClose={close} />
      )}
      {dialog?.type === 'variation' && <VariationDialog contract={c} onClose={close} />}
      {dialog?.type === 'decideVariation' && <VariationDecisionDialog contract={c} variation={dialog.variation} onClose={close} />}
      {dialog?.type === 'complete' && <CompleteDialog contract={c} onClose={close} />}
    </>
  );
}
