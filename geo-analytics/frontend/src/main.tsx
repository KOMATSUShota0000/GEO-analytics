import React from "react";
import ReactDOM from "react-dom/client";
import "./index.css";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { JobAnalysisPage } from "./pages/JobAnalysisPage";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/job/:jobId" element={<JobAnalysisPage />} />
        <Route path="/" element={<JobAnalysisPage />} />
      </Routes>
    </BrowserRouter>
  </React.StrictMode>
);
