import { Navigate, Route, Routes } from "react-router-dom";
import { JobAnalysisPage } from "./pages/JobAnalysisPage";
import JobCreationPage from "./pages/JobCreationPage";
import PricingPage from "./pages/PricingPage";
import ProjectSettingsPage from "./pages/ProjectSettingsPage";
import ReportPrintPage from "./pages/ReportPrintPage";

export default function App(): JSX.Element {
  return (
    <Routes>
      <Route path="/" element={<JobCreationPage />} />
      <Route path="/job/:jobId" element={<JobAnalysisPage />} />
      <Route path="/reports/print/:jobId" element={<ReportPrintPage />} />
      <Route path="/projects/:projectId/settings" element={<ProjectSettingsPage />} />
      <Route path="/pricing" element={<PricingPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
