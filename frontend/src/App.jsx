import { Navigate, Route, Routes } from 'react-router';
import AppShell from './components/AppShell.jsx';
import AccessGate from './components/AccessGate.jsx';
import UsersAdminPage from './pages/admin/UsersAdminPage.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import ProjectsPage from './pages/ProjectsPage.jsx';
import ProjectDetailPage from './pages/ProjectDetailPage.jsx';
import ComingSoonPage from './pages/ComingSoonPage.jsx';
import ProcurementSettingsPage from './pages/procurement-settings/ProcurementSettingsPage.jsx';
import RequisitionsPage from './pages/requisitions/RequisitionsPage.jsx';
import RequisitionFormPage from './pages/requisitions/RequisitionFormPage.jsx';
import RequisitionDetailPage from './pages/requisitions/RequisitionDetailPage.jsx';
import TendersPage from './pages/tenders/TendersPage.jsx';
import TenderDetailPage from './pages/tenders/TenderDetailPage.jsx';
import LettersPage from './pages/tenders/LettersPage.jsx';
import ContractsPage from './pages/contracts/ContractsPage.jsx';
import ContractDetailPage from './pages/contracts/ContractDetailPage.jsx';
import SuppliersPage from './pages/suppliers/SuppliersPage.jsx';
import ProcurementPlansPage from './pages/procurement-plan/ProcurementPlansPage.jsx';
import ProcurementPlanPage from './pages/procurement-plan/ProcurementPlanPage.jsx';
import LessonsPage from './pages/closeout/LessonsPage.jsx';
import BenefitsPage from './pages/benefits/BenefitsPage.jsx';
import ReportsPage from './pages/reports/ReportsPage.jsx';
import ReportViewPage from './pages/reports/ReportViewPage.jsx';
import ChecklistTemplatePage from './pages/closeout/ChecklistTemplatePage.jsx';

export default function App() {
  return (
    <AccessGate>
      <AppShell>
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/projects" element={<ProjectsPage />} />
          <Route path="/projects/:id" element={<ProjectDetailPage />} />
          <Route path="/procurement-plans" element={<ProcurementPlansPage />} />
          <Route path="/procurement-plans/:id" element={<ProcurementPlanPage />} />
          <Route path="/requisitions" element={<RequisitionsPage />} />
          <Route path="/requisitions/new" element={<RequisitionFormPage />} />
          <Route path="/requisitions/:id" element={<RequisitionDetailPage />} />
          <Route path="/requisitions/:id/edit" element={<RequisitionFormPage />} />
          <Route path="/tenders" element={<TendersPage />} />
          <Route path="/tenders/:id" element={<TenderDetailPage />} />
          <Route path="/tenders/:id/letters" element={<LettersPage />} />
          <Route path="/suppliers" element={<SuppliersPage />} />
          <Route path="/procurement" element={<Navigate to="/tenders" replace />} />
          <Route path="/contracts" element={<ContractsPage />} />
          <Route path="/contracts/:id" element={<ContractDetailPage />} />
          <Route path="/reports" element={<ReportsPage />} />
          <Route path="/reports/:id" element={<ReportViewPage />} />
          <Route path="/settings/procurement" element={<ProcurementSettingsPage />} />
          <Route path="/settings/closeout-checklist" element={<ChecklistTemplatePage />} />
          <Route path="/settings/users" element={<UsersAdminPage />} />
          <Route path="/lessons" element={<LessonsPage />} />
          <Route path="/benefits" element={<BenefitsPage />} />
          <Route path="*" element={<ComingSoonPage module="Page not found" notFound />} />
        </Routes>
      </AppShell>
    </AccessGate>
  );
}
