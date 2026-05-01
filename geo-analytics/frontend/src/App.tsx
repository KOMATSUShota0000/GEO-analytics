import { Navigate, Route, Routes } from "react-router-dom";
import RequireAuthLayout from "./auth/RequireAuthLayout";
import { JobAnalysisPage } from "./pages/JobAnalysisPage";
import JobCreationPage from "./pages/JobCreationPage";
import LoginPage from "./pages/LoginPage";
import PricingPage from "./pages/PricingPage";
import QueryProposalPage from "./pages/QueryProposalPage";
import GeoOnboardingView from "./pages/GeoOnboardingView";
import ProjectSettingsPage from "./pages/ProjectSettingsPage";
import ReportPrintPage from "./pages/ReportPrintPage";

export default function App(): JSX.Element {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuthLayout />}>
        <Route path="/" element={<JobCreationPage />} />
        <Route path="/job/:jobId" element={<JobAnalysisPage />} />
        <Route path="/query-proposal" element={<QueryProposalPage />} />
        <Route path="/reports/print/:jobId" element={<ReportPrintPage />} />
        <Route path="/projects/:projectId/settings" element={<ProjectSettingsPage />} />
        <Route path="/projects/:projectId/onboarding" element={<GeoOnboardingView />} />
        <Route path="/pricing" element={<PricingPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
