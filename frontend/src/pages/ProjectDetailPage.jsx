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
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Grid,
  Link,
  List,
  ListItem,
  ListItemText,
  Skeleton,
  Step,
  StepLabel,
  Stepper,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import PageHeader from '../components/PageHeader.jsx';
import ErrorAlert from '../components/ErrorAlert.jsx';
import PhaseChip from '../components/PhaseChip.jsx';
import { useAdvancePhase, useProject, useProjectHistory } from '../api/projects.js';
import { useRequisitions } from '../api/requisitions.js';
import RequisitionStatusChip from './requisitions/RequisitionStatusChip.jsx';
import ProjectContracts from './contracts/ProjectContracts.jsx';
import ScheduleTab from './planning/ScheduleTab.jsx';
import RisksTab from './execution/RisksTab.jsx';
import IssuesTab from './execution/IssuesTab.jsx';
import ReportsTab from './execution/ReportsTab.jsx';
import CloseoutTab from './closeout/CloseoutTab.jsx';
import BenefitsTab from './benefits/BenefitsTab.jsx';
import ObjectivesCard from './ObjectivesCard.jsx';

const PROJECT_TABS = [
  ['overview', 'Overview'],
  ['schedule', 'Schedule'],
  ['risks', 'Risks'],
  ['issues', 'Issues'],
  ['reports', 'Status reports'],
  ['closeout', 'Closeout'],
  ['benefits', 'Benefits'],
];
import { PHASES, phaseIndex } from '../domain/lifecycle.js';
import { formatMoney } from '../utils/money.js';

const ACTION_LABELS = {
  'project.created': 'Project created',
  'project.gate.passed': 'Gate passed',
  'project.objectives_set': 'Objectives listed',
  'project.objective_added': 'Objective added',
  'project.objective_changed': 'Objective changed',
  'project.objective_withdrawn': 'Objective withdrawn',
  'project.objective_reinstated': 'Objective reinstated',
  'project.objective_deleted': 'Objective deleted',
};

function Field({ label, children }) {
  return (
    <Box sx={{ mb: 2 }}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body1">{children || '—'}</Typography>
    </Box>
  );
}

Field.propTypes = { label: PropTypes.string.isRequired, children: PropTypes.node };

function GateDialog({ project, open, onClose }) {
  const [note, setNote] = useState('');
  const advance = useAdvancePhase(project.id);

  const close = () => {
    setNote('');
    advance.reset();
    onClose();
  };

  const submit = (e) => {
    e.preventDefault();
    advance.mutate(note.trim(), { onSuccess: close });
  };

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="sm" component="form" onSubmit={submit}>
      <DialogTitle>
        Record gate {project.nextGate}: {project.nextGateName}
      </DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Approving this gate moves the project out of its current phase. The decision is written
          to the tamper-evident audit log and cannot be edited later.
        </DialogContentText>
        {advance.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {advance.error.message}
          </Alert>
        )}
        <TextField
          label="Decision and conditions"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="e.g. Approved by the investment committee, minute 14/2026"
          multiline
          minRows={3}
          required
          fullWidth
          autoFocus
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={close}>Cancel</Button>
        <Button type="submit" variant="contained" disabled={!note.trim()} loading={advance.isPending}>
          Approve gate
        </Button>
      </DialogActions>
    </Dialog>
  );
}

GateDialog.propTypes = {
  project: PropTypes.shape({
    id: PropTypes.string.isRequired,
    nextGate: PropTypes.string,
    nextGateName: PropTypes.string,
  }).isRequired,
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
};

export default function ProjectDetailPage() {
  const { id } = useParams();
  const [params, setParams] = useSearchParams();
  const tab = PROJECT_TABS.some(([k]) => k === params.get('tab')) ? params.get('tab') : 'overview';
  const { data: project, isLoading, error } = useProject(id);
  const { data: history } = useProjectHistory(id);
  const [gateOpen, setGateOpen] = useState(false);

  if (error) return <ErrorAlert error={error} title="Could not load project" />;
  if (isLoading || !project) {
    return (
      <>
        <Skeleton variant="text" width={320} height={56} />
        <Skeleton variant="rounded" height={120} sx={{ mt: 2 }} />
      </>
    );
  }

  return (
    <>
      <Breadcrumbs sx={{ mb: 1 }}>
        <Link component={RouterLink} to="/projects" underline="hover" color="inherit">
          Projects
        </Link>
        <Typography color="text.primary">{project.code}</Typography>
      </Breadcrumbs>
      <PageHeader
        title={project.name}
        subtitle={<PhaseChip phase={project.phase} />}
        actions={
          project.status === 'ACTIVE' ? (
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              <Button component={RouterLink} to={`/requisitions/new?projectId=${project.id}`}>
                Raise requisition
              </Button>
              {project.nextGate && (
                <Button variant="contained" onClick={() => setGateOpen(true)}>
                  Record gate {project.nextGate}
                </Button>
              )}
            </Box>
          ) : null
        }
      />

      <Card sx={{ mb: 3 }}>
        <CardContent sx={{ overflowX: 'auto' }}>
          <Stepper activeStep={phaseIndex(project.phase)} alternativeLabel sx={{ minWidth: 640 }}>
            {PHASES.map((p) => (
              <Step key={p.key}>
                <StepLabel optional={p.gate && <Typography variant="caption">{p.gate}</Typography>}>
                  {p.label}
                </StepLabel>
              </Step>
            ))}
          </Stepper>
        </CardContent>
      </Card>

      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs
          value={tab}
          onChange={(_e, v) => setParams(v === 'overview' ? {} : { tab: v }, { replace: true })}
          variant="scrollable"
          allowScrollButtonsMobile
        >
          {PROJECT_TABS.map(([k, label]) => (
            <Tab key={k} value={k} label={label} />
          ))}
        </Tabs>
      </Box>
      {tab === 'schedule' && <ScheduleTab projectId={project.id} />}
      {tab === 'risks' && <RisksTab projectId={project.id} />}
      {tab === 'issues' && <IssuesTab projectId={project.id} />}
      {tab === 'reports' && <ReportsTab project={project} />}
      {tab === 'closeout' && <CloseoutTab projectId={project.id} />}
      {tab === 'benefits' && <BenefitsTab projectId={project.id} />}
      {tab === 'overview' && (
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 7 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 2 }}>
                Details
              </Typography>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Field label="Code">{project.code}</Field>
                  <Field label="Sponsor">{project.sponsor}</Field>
                  <Field label="Status">{project.status}</Field>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Field label="Approved budget">
                    {formatMoney(project.budgetMinor, project.currency)}
                  </Field>
                  <Field label="Next gate">
                    {project.nextGate ? `${project.nextGate} · ${project.nextGateName}` : null}
                  </Field>
                  <Field label="Last updated">
                    {new Date(project.updatedAt).toLocaleString('en-KE')}
                  </Field>
                </Grid>
              </Grid>
              <Field label="Description">{project.description}</Field>
            </CardContent>
          </Card>
          <ObjectivesCard projectId={project.id} />
          <ProjectRequisitions projectId={project.id} />
          <ProjectContracts project={project} />
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          <Card>
            <CardContent>
              <Typography variant="h6">Audit history</Typography>
              {!history?.length && (
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                  No recorded events yet.
                </Typography>
              )}
              <List dense>
                {history?.map((h) => {
                  const detail = safeParse(h.payload);
                  return (
                    <ListItem key={h.hash} disableGutters divider>
                      <ListItemText
                        primary={`${ACTION_LABELS[h.action] ?? h.action}${detail.gate ? ` ${detail.gate}` : ''}${detail.objective ? ` ${detail.objective}` : ''}`}
                        secondary={
                          <>
                            {new Date(h.occurredAt).toLocaleString('en-KE')} · {h.actor}
                            {(detail.note || detail.reason) && (
                              <Box component="span" sx={{ display: 'block' }}>
                                {detail.note || detail.reason}
                              </Box>
                            )}
                          </>
                        }
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

      <GateDialog project={project} open={gateOpen} onClose={() => setGateOpen(false)} />
    </>
  );
}

function ProjectRequisitions({ projectId }) {
  const { data } = useRequisitions('ALL', projectId);
  return (
    <Card sx={{ mt: 3 }}>
      <CardContent>
        <Typography variant="h6">Requisitions</Typography>
        {!data?.length && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            None yet.
          </Typography>
        )}
        <List dense>
          {data?.map((r) => (
            <ListItem
              key={r.id}
              disableGutters
              divider
              secondaryAction={<RequisitionStatusChip status={r.status} />}
            >
              <ListItemText
                primary={
                  <Link component={RouterLink} to={`/requisitions/${r.id}`}>
                    {r.number} · {r.title}
                  </Link>
                }
                secondary={formatMoney(r.estimatedTotalMinor, r.currency)}
              />
            </ListItem>
          ))}
        </List>
      </CardContent>
    </Card>
  );
}

ProjectRequisitions.propTypes = { projectId: PropTypes.string.isRequired };

function safeParse(json) {
  try {
    return JSON.parse(json) ?? {};
  } catch {
    return {};
  }
}
