import { useState } from 'react';
import { Alert, Box, Card, Skeleton, Tab, Tabs } from '@mui/material';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import { useProcurementSettings } from '../../api/procurementSettings.js';
import { hasRole, useMe } from '../../api/me.js';
import MethodsTab from './MethodsTab.jsx';
import ThresholdsTab from './ThresholdsTab.jsx';
import CommitteesTab from './CommitteesTab.jsx';
import CheckTab from './CheckTab.jsx';
import TemplatesTab from './TemplatesTab.jsx';

const TABS = [
  'Procurement methods',
  'Approval thresholds',
  'Evaluation committees',
  'Evaluation criteria',
  'Check a purchase',
];

export default function ProcurementSettingsPage() {
  const [tab, setTab] = useState(0);
  const { data, isLoading, error } = useProcurementSettings();
  const { data: me } = useMe();
  const canEdit = hasRole(me, 'HEAD_OF_PROCUREMENT');

  return (
    <>
      <PageHeader
        title="Procurement settings"
        subtitle="Methods, approval limits, committee rules and evaluation criteria for this organisation. Every change is recorded in the audit log."
      />
      {me && !canEdit && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          You can view these settings. Only the head of procurement can change them.
        </Alert>
      )}
      <ErrorAlert error={error} title="Could not load settings" />
      <Card>
        <Box sx={{ borderBottom: 1, borderColor: 'divider', px: 2 }}>
          <Tabs value={tab} onChange={(_e, v) => setTab(v)} variant="scrollable" allowScrollButtonsMobile>
            {TABS.map((label) => (
              <Tab key={label} label={label} />
            ))}
          </Tabs>
        </Box>
        <Box sx={{ p: { xs: 2, md: 3 } }}>
          {isLoading || !data ? (
            <Skeleton variant="rounded" height={240} />
          ) : (
            <>
              {tab === 0 && <MethodsTab methods={data.methods} />}
              {tab === 1 && (
                <ThresholdsTab thresholds={data.approvalThresholds} methods={data.methods} />
              )}
              {tab === 2 && <CommitteesTab rules={data.committeeRules} methods={data.methods} />}
              {tab === 3 && <TemplatesTab templates={data.evaluationTemplates ?? []} methods={data.methods} />}
              {tab === 4 && <CheckTab methods={data.methods} />}
            </>
          )}
        </Box>
      </Card>
    </>
  );
}
