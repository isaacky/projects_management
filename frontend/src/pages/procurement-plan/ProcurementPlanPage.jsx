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
  Link,
  Skeleton,
  Stack,
  Tab,
  Tabs,
  Typography,
} from '@mui/material';
import FileDownloadOutlined from '@mui/icons-material/FileDownloadOutlined';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import { usePlanAction, useProcurementPlan } from '../../api/procurementPlans.js';
import { PLAN_STATUS, planRows, saveText, toCsv } from '../../domain/procurementPlan.js';
import { formatDate } from '../../domain/contracts.js';
import { formatMoney } from '../../utils/money.js';
import PlanLinesTab from './PlanLinesTab.jsx';
import PlanDepartmentsTab from './PlanDepartmentsTab.jsx';
import PlanSummaryTab, { ApprovalsTab, UnplannedTab } from './PlanSummaryTab.jsx';
import { NoteDialog } from './PlanDialogs.jsx';

const TABS = [
  ['lines', 'Lines'],
  ['departments', 'Departments'],
  ['summary', 'Summary'],
  ['unplanned', 'Unplanned requisitions'],
  ['approvals', 'Approvals'],
];

function Stat({ label, value, note, color }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="overline" color="text.secondary">
          {label}
        </Typography>
        <Typography variant="h5" sx={{ color }}>
          {value}
        </Typography>
        {note && (
          <Typography variant="caption" color="text.secondary">
            {note}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

Stat.propTypes = { label: PropTypes.string.isRequired, value: PropTypes.node, note: PropTypes.node, color: PropTypes.string };

function share(part, total) {
  return total > 0 ? `${Math.round((part * 100) / total)}%` : '—';
}

/** Submit, approve, return and revise. */
function PlanActions({ plan }) {
  const action = usePlanAction(plan.id);
  const [dialog, setDialog] = useState(null); // 'approve' | 'return' | 'revise'
  const can = plan.permissions;
  const pendingDepts = plan.departments.filter((d) => d.status === 'DRAFT').length;
  const close = () => {
    action.reset();
    setDialog(null);
  };
  const run = (path, body) => action.mutate({ path, body }, { onSuccess: () => setDialog(null) });

  return (
    <Stack spacing={1} sx={{ alignItems: { sm: 'flex-end' } }}>
      <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', justifyContent: 'flex-end' }}>
        <Button
          startIcon={<FileDownloadOutlined />}
          onClick={() => saveText(toCsv(planRows(plan)), `procurement-plan-${plan.financialYear.replace('/', '-')}.csv`)}
        >
          Export CSV
        </Button>
        {can.canSubmit && (
          <Button
            variant="contained"
            disabled={pendingDepts > 0 || plan.departments.length === 0}
            loading={action.isPending && !dialog}
            onClick={() => run('/submit')}
          >
            Submit to accounting officer
          </Button>
        )}
        {can.canDecide && (
          <>
            <Button variant="outlined" color="warning" onClick={() => setDialog('return')}>
              Return
            </Button>
            <Button variant="contained" color="success" onClick={() => setDialog('approve')}>
              Approve
            </Button>
          </>
        )}
        {can.canRevise && (
          <Button variant="outlined" onClick={() => setDialog('revise')}>
            Revise plan
          </Button>
        )}
      </Stack>
      {can.canSubmit && pendingDepts > 0 && (
        <Typography variant="caption" color="text.secondary">
          {pendingDepts} department{pendingDepts === 1 ? '' : 's'} still to submit
        </Typography>
      )}
      {action.error && !dialog && (
        <Typography variant="caption" color="error">
          {action.error.message}
        </Typography>
      )}
      <NoteDialog
        key={`approve-${dialog}`}
        open={dialog === 'approve'}
        title={`Approve the ${plan.financialYear} plan`}
        text={`${plan.summary.lineCount} lines, ${formatMoney(plan.summary.totalMinor)}. Requisitions can then draw on these lines.`}
        label="Note (optional)"
        confirm="Approve"
        pending={action.isPending}
        error={action.error}
        onClose={close}
        onConfirm={(note) => run('/approve', { note })}
      />
      <NoteDialog
        key={`return-${dialog}`}
        open={dialog === 'return'}
        title="Return the plan"
        text="The head of procurement is notified and can change the plan and submit it again."
        label="What needs to change"
        required
        confirm="Return"
        pending={action.isPending}
        error={action.error}
        onClose={close}
        onConfirm={(note) => run('/return', { note })}
      />
      <NoteDialog
        key={`revise-${dialog}`}
        open={dialog === 'revise'}
        title="Revise the plan"
        text="The plan opens for changes. Requisitions keep using the approved lines and amounts until the accounting officer approves the revision."
        label="Reason for the revision"
        required
        minLength={10}
        confirm="Open for revision"
        pending={action.isPending}
        error={action.error}
        onClose={close}
        onConfirm={(note) => run('/revise', { note })}
      />
    </Stack>
  );
}

PlanActions.propTypes = { plan: PropTypes.object.isRequired };

export default function ProcurementPlanPage() {
  const { id } = useParams();
  const [params, setParams] = useSearchParams();
  const { data: plan, isLoading, error } = useProcurementPlan(id);
  const tab = TABS.some(([k]) => k === params.get('tab')) ? params.get('tab') : 'lines';

  if (error) return <ErrorAlert error={error} title="Could not load the procurement plan" />;
  if (isLoading || !plan) return <Skeleton variant="rounded" height={400} />;

  const s = plan.summary;
  const status = PLAN_STATUS[plan.status];
  return (
    <>
      <Breadcrumbs sx={{ mb: 1 }}>
        <Link component={RouterLink} to="/procurement-plans" underline="hover" color="inherit">
          Procurement plans
        </Link>
        <Typography color="text.primary">{plan.financialYear}</Typography>
      </Breadcrumbs>
      <PageHeader
        title={`Procurement plan ${plan.financialYear}`}
        subtitle={
          <Stack direction="row" spacing={1} component="span" sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Chip size="small" label={status.label} color={status.color} component="span" />
            <span>
              {formatDate(plan.fyStart)} – {formatDate(plan.fyEnd)}
              {plan.revision > 0 && ` · approved ${plan.revision} time${plan.revision === 1 ? '' : 's'}`}
              {plan.currentQuarter > 0 && ` · now in Q${plan.currentQuarter}`}
            </span>
          </Stack>
        }
        actions={<PlanActions plan={plan} />}
      />

      {plan.returnNote && plan.status === 'DRAFT' && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Returned by the accounting officer: {plan.returnNote}
        </Alert>
      )}
      {plan.revisionReason && plan.status !== 'APPROVED' && (
        <Alert severity="info" sx={{ mb: 2 }}>
          Revision: {plan.revisionReason}
        </Alert>
      )}
      {plan.warnings.map((w) => (
        <Alert key={w} severity="warning" sx={{ mb: 1 }}>
          {w}
        </Alert>
      ))}

      <Grid container spacing={2} sx={{ my: 2 }}>
        <Grid size={{ xs: 6, md: 2 }}>
          <Stat label="Planned" value={formatMoney(s.totalMinor, 'KES', { compact: true })} note={`${s.lineCount} lines`} />
        </Grid>
        <Grid size={{ xs: 6, md: 2 }}>
          <Stat
            label="Approved"
            value={plan.revision > 0 ? formatMoney(s.approvedTotalMinor, 'KES', { compact: true }) : '—'}
            note={plan.revision > 0 ? null : 'Not yet approved'}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 2 }}>
          <Stat
            label="Requisitioned"
            value={formatMoney(s.committedMinor, 'KES', { compact: true })}
            note={`${share(s.committedMinor, s.approvedTotalMinor || s.totalMinor)} of the plan`}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 2 }}>
          <Stat label="Awarded" value={formatMoney(s.awardedMinor, 'KES', { compact: true })} />
        </Grid>
        <Grid size={{ xs: 6, md: 2 }}>
          <Stat
            label="AGPO reserved"
            value={`${s.agpoPct}%`}
            note={`Target ${Number(s.agpoTargetPct)}%`}
            color={Number(s.agpoPct) < Number(s.agpoTargetPct) ? 'warning.main' : 'success.main'}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 2 }}>
          <Stat
            label="Behind / unplanned"
            value={`${s.behindCount} / ${s.unplannedCount}`}
            note={s.unplannedCount ? `${formatMoney(s.unplannedMinor, 'KES', { compact: true })} unplanned` : 'lines / requisitions'}
            color={s.behindCount || s.unplannedCount ? 'warning.main' : undefined}
          />
        </Grid>
      </Grid>

      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}>
        <Tabs
          value={tab}
          onChange={(_e, v) => setParams(v === 'lines' ? {} : { tab: v }, { replace: true })}
          variant="scrollable"
          allowScrollButtonsMobile
        >
          {TABS.map(([k, label]) => (
            <Tab key={k} value={k} label={label} />
          ))}
        </Tabs>
      </Box>
      {tab === 'lines' && <PlanLinesTab plan={plan} />}
      {tab === 'departments' && <PlanDepartmentsTab plan={plan} />}
      {tab === 'summary' && <PlanSummaryTab plan={plan} />}
      {tab === 'unplanned' && <UnplannedTab plan={plan} />}
      {tab === 'approvals' && <ApprovalsTab plan={plan} />}
    </>
  );
}
