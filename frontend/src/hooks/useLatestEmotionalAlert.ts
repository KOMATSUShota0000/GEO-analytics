import { useEffect, useState } from "react";
import { getJobAnalysis, getJobStatus } from "../api/jobsApi";
import { getBannerJobHintJobId } from "../lib/bannerJobHint";
import type { EmotionalAlertPayload } from "../types/analysis";

function isCompletedLike(status: string): boolean {
  return status === "COMPLETED" || status === "SUCCEEDED";
}

export type UseLatestEmotionalAlertResult = {
  emotionalAlert: EmotionalAlertPayload | null;
  loading: boolean;
};

export function useLatestEmotionalAlert(projectId: string): UseLatestEmotionalAlertResult {
  const [emotionalAlert, setEmotionalAlert] = useState<EmotionalAlertPayload | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const pid = projectId.trim();
    if (pid.length === 0) {
      setEmotionalAlert(null);
      setLoading(false);
      return;
    }
    const hintedJobId = getBannerJobHintJobId(pid);
    if (hintedJobId === null || hintedJobId.length === 0) {
      setEmotionalAlert(null);
      setLoading(false);
      return;
    }
    const controller = new AbortController();
    setLoading(true);

    void (async () => {
      try {
        const st = await getJobStatus(hintedJobId, controller.signal);
        if (st === null) {
          setEmotionalAlert(null);
          return;
        }
        const stPid = st.projectId != null ? st.projectId.trim() : "";
        if (stPid !== pid) {
          setEmotionalAlert(null);
          return;
        }
        if (!isCompletedLike(st.jobStatus.trim())) {
          setEmotionalAlert(null);
          return;
        }
        const detail = await getJobAnalysis(hintedJobId, controller.signal);
        const ea = detail?.emotionalAlert;
        setEmotionalAlert(ea ?? null);
      } catch {
        setEmotionalAlert(null);
      } finally {
        setLoading(false);
      }
    })();

    return (): void => controller.abort();
  }, [projectId]);

  return { emotionalAlert, loading };
}
