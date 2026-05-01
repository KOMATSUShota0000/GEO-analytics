import { postQueryProposal } from "../api/queryProposalApi";
import { AnalysisProgress } from "../components/AnalysisProgress";
import { ProposalForm } from "../components/ProposalForm";
import { ProposalResultView } from "../components/ProposalResultView";
import { GeoApiRequestError } from "../errors/GeoApiRequestError";
import type { QueryProposalRequest, QueryProposalResponse } from "../types/queryProposal";
import { Alert, AlertTitle, Container, Stack, Typography } from "@mui/material";
import { useState } from "react";

type ResolvedProposalAlert = {
  severity: "error" | "warning" | "info";
  title: string;
  description?: string;
};

const QUERY_PROPOSAL_FORM_KEYS = new Set(["url", "businessDescription", "targetAudience", "strategicFocus"]);

function mapSpringFieldToProposalFormKey(field: string): string | null {
  const f = field.trim();
  if (f === "url") {
    return "url";
  }
  const m = /^knowledge\.(businessDescription|targetAudience|strategicFocus)$/.exec(f);
  if (m !== null && QUERY_PROPOSAL_FORM_KEYS.has(m[1])) {
    return m[1];
  }
  return null;
}

function normalizeValidationFailedFields(
  details: Record<string, unknown> | undefined,
): Record<string, string> | null {
  if (details === undefined) {
    return null;
  }
  const raw = details.fields;
  if (raw === null || typeof raw !== "object" || Array.isArray(raw)) {
    return null;
  }
  const out: Record<string, string> = {};
  for (const [springKey, rawMessage] of Object.entries(raw as Record<string, unknown>)) {
    const formKey = mapSpringFieldToProposalFormKey(springKey);
    if (formKey === null) {
      continue;
    }
    const message =
      typeof rawMessage === "string"
        ? rawMessage.trim()
        : rawMessage !== null && rawMessage !== undefined
          ? String(rawMessage).trim()
          : "";
    if (message.length > 0) {
      out[formKey] = message;
    }
  }
  return Object.keys(out).length > 0 ? out : null;
}

function resolveServiceUnavailableAlert(err: GeoApiRequestError): ResolvedProposalAlert {
  switch (err.errorCode) {
    case "maintenance":
      return {
        severity: "error",
        title: "システムメンテナンス中です。終了後に再度お試しください。",
      };
    case "ai_analysis_timeout":
      return {
        severity: "error",
        title: "AIの解析が制限時間内に完了しませんでした。しばらく待ってから再試行してください。",
      };
    case "database_unavailable":
      return {
        severity: "error",
        title: "データベースへの接続が混み合っています。数分後に再度お試しください。",
      };
    case "serialization_unavailable":
      return {
        severity: "error",
        title: "応答の生成に失敗しました。しばらくしてから再度お試しください。",
      };
    case "query_proposal_ai_failed":
      return {
        severity: "error",
        title: "AIによるクエリ提案に失敗しました。混雑の可能性があります。時間をおいて再試行してください。",
        description:
          err.message.trim().length > 0 ? err.message.trim() : undefined,
      };
    case "system_busy":
      return {
        severity: "error",
        title: "ただいまシステムが混み合っています。しばらくしてから再度お試しください。",
      };
    default:
      return {
        severity: "error",
        title:
          err.message.trim().length > 0
            ? err.message.trim()
            : "サービスが一時的に利用できません。しばらくしてから再度お試しください。",
      };
  }
}

function resolveProposalErrorAlert(err: unknown): ResolvedProposalAlert {
  if (err instanceof GeoApiRequestError) {
    if (err.status === 503) {
      return resolveServiceUnavailableAlert(err);
    }
    if (err.status === 422) {
      const rawDetail = err.details?.detail;
      const description =
        typeof rawDetail === "string" && rawDetail.trim().length > 0 ? rawDetail.trim() : undefined;
      return {
        severity: "error",
        title: err.message.trim().length > 0 ? err.message : "入力内容を確認してください。",
        description,
      };
    }
    if (err.status === 400 && err.errorCode === "validation_failed") {
      return {
        severity: "error",
        title: err.message.trim().length > 0 ? err.message : "入力内容を確認してください。",
        description: "各入力欄のメッセージを確認してください。",
      };
    }
    return {
      severity: "error",
      title: err.message.trim().length > 0 ? err.message : "リクエストに失敗しました。",
    };
  }
  if (err instanceof Error) {
    if (err.name === "AbortError") {
      return {
        severity: "warning",
        title: "処理が中断されたか、時間がかかりすぎました。もう一度お試しください。",
      };
    }
    return {
      severity: "error",
      title: err.message.trim().length > 0 ? err.message : "予期しないエラーが発生しました。",
    };
  }
  return {
    severity: "error",
    title: "予期しないエラーが発生しました。",
  };
}

export default function QueryProposalPage(): JSX.Element {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<GeoApiRequestError | Error | null>(null);
  const [data, setData] = useState<QueryProposalResponse | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string> | null>(null);

  const handleSubmit = async (request: QueryProposalRequest): Promise<void> => {
    setData(null);
    setError(null);
    setFieldErrors(null);
    setIsLoading(true);
    try {
      const result = await postQueryProposal(request);
      setData(result);
    } catch (e: unknown) {
      setError(e instanceof Error ? e : new Error(String(e)));
      if (e instanceof GeoApiRequestError && e.status === 400 && e.errorCode === "validation_failed") {
        setFieldErrors(normalizeValidationFailedFields(e.details));
      } else {
        setFieldErrors(null);
      }
    } finally {
      setIsLoading(false);
    }
  };

  const alertContent = error && !isLoading ? resolveProposalErrorAlert(error) : null;

  return (
    <Container maxWidth="md" className="py-8">
      <Stack spacing={3}>
        <Typography variant="h4" component="h1" fontWeight={600} gutterBottom>
          AI戦略クエリ提案
        </Typography>

        <ProposalForm
          onSubmit={handleSubmit}
          disabled={isLoading}
          serverFieldErrors={fieldErrors ?? undefined}
        />

        {isLoading ? <AnalysisProgress /> : null}

        {alertContent !== null ? (
          <Alert severity={alertContent.severity}>
            <AlertTitle>{alertContent.title}</AlertTitle>
            {alertContent.description !== undefined ? (
              <Typography variant="body2" component="p" sx={{ m: 0, mt: 0.5 }}>
                {alertContent.description}
              </Typography>
            ) : null}
          </Alert>
        ) : null}

        {data !== null && !isLoading ? <ProposalResultView data={data} /> : null}
      </Stack>
    </Container>
  );
}
