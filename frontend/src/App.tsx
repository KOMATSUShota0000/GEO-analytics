import { Navigate, Route, Routes } from "react-router-dom";
import RequireAuthLayout from "./auth/RequireAuthLayout";
import { JobAnalysisPage } from "./pages/JobAnalysisPage";
import JobCreationPage from "./pages/JobCreationPage";
import LoginPage from "./pages/LoginPage";
import PricingPage from "./pages/PricingPage";
import PublicDemoPage from "./pages/PublicDemoPage";
import PublicPlansPage from "./pages/PublicPlansPage";
import GeoOnboardingView from "./pages/GeoOnboardingView";
import ProjectSettingsPage from "./pages/ProjectSettingsPage";
import ReportPrintPage from "./pages/ReportPrintPage";
import StrategyDashboardPage from "./pages/StrategyDashboardPage";

export default function App(): JSX.Element {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      {/* 公開（未ログイン）マーケティング導線。認証レイアウトの外に置く。 */}
      <Route path="/demo" element={<PublicDemoPage />} />
      <Route path="/plans" element={<PublicPlansPage />} />
      <Route element={<RequireAuthLayout />}>
        <Route path="/" element={<JobCreationPage />} />
        <Route path="/job/:jobId" element={<JobAnalysisPage />} />
        <Route path="/reports/print/:jobId" element={<ReportPrintPage />} />
        <Route path="/projects/:projectId/settings" element={<ProjectSettingsPage />} />
        <Route path="/projects/:projectId/onboarding" element={<GeoOnboardingView />} />
        <Route path="/projects/:projectId/strategy" element={<StrategyDashboardPage />} />
        <Route path="/pricing" element={<PricingPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
