import { Navigate, Route, Routes } from "react-router-dom";
import { JobAnalysisPage } from "./pages/JobAnalysisPage";
import JobCreationPage from "./pages/JobCreationPage";

export default function App(): JSX.Element {
  return (
    <Routes>
      <Route path="/" element={<JobCreationPage />} />
      <Route path="/job/:jobId" element={<JobAnalysisPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
