import type { QueryProposalRequest } from "../types/queryProposal";
import { Button, Paper, Stack, TextField } from "@mui/material";
import type { FormEvent } from "react";
import { useState } from "react";

export type ProposalFormProps = {
  onSubmit: (request: QueryProposalRequest) => void | Promise<void>;
  disabled?: boolean;
  /** サーバー validation_failed 時のフィールド別メッセージ（キーは url 等に正規化済み） */
  serverFieldErrors?: Record<string, string>;
};

type FieldErrors = {
  url?: string;
  businessDescription?: string;
  targetAudience?: string;
  strategicFocus?: string;
};

function validateUrl(raw: string): string | undefined {
  const trimmed = raw.trim();
  if (trimmed.length === 0) {
    return "URLを入力してください。";
  }
  try {
    const parsed = new URL(trimmed);
    const protocol = parsed.protocol.toLowerCase();
    if (protocol !== "http:" && protocol !== "https:") {
      return "http または https のURLを入力してください。";
    }
    return undefined;
  } catch {
    return "有効なURL形式で入力してください。";
  }
}

function validateRequiredText(raw: string, label: string): string | undefined {
  if (raw.trim().length === 0) {
    return `${label}を入力してください。`;
  }
  return undefined;
}

const MAX_FIELD_LENGTH = 50_000;

export function ProposalForm({ onSubmit, disabled = false, serverFieldErrors }: ProposalFormProps) {
  const [url, setUrl] = useState("");
  const [businessDescription, setBusinessDescription] = useState("");
  const [targetAudience, setTargetAudience] = useState("");
  const [strategicFocus, setStrategicFocus] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});

  const isLocked = disabled;

  const urlMessage = serverFieldErrors?.url ?? fieldErrors.url;
  const businessDescriptionMessage = serverFieldErrors?.businessDescription ?? fieldErrors.businessDescription;
  const targetAudienceMessage = serverFieldErrors?.targetAudience ?? fieldErrors.targetAudience;
  const strategicFocusMessage = serverFieldErrors?.strategicFocus ?? fieldErrors.strategicFocus;

  const handleSubmit = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    if (isLocked) {
      return;
    }

    const nextErrors: FieldErrors = {
      url: validateUrl(url),
      businessDescription: validateRequiredText(businessDescription, "事業概要"),
      targetAudience: validateRequiredText(targetAudience, "想定顧客"),
      strategicFocus: validateRequiredText(strategicFocus, "注力点"),
    };

    const hasError =
      nextErrors.url !== undefined
      || nextErrors.businessDescription !== undefined
      || nextErrors.targetAudience !== undefined
      || nextErrors.strategicFocus !== undefined;

    setFieldErrors(nextErrors);

    if (hasError) {
      return;
    }

    const trimmedUrl = url.trim();
    const request: QueryProposalRequest = {
      url: trimmedUrl,
      knowledge: {
        businessDescription: businessDescription.trim(),
        targetAudience: targetAudience.trim(),
        strategicFocus: strategicFocus.trim(),
      },
    };

    void onSubmit(request);
  };

  return (
    <Paper elevation={0} variant="outlined" sx={{ p: 2.5, borderColor: "divider" }}>
      <form onSubmit={handleSubmit} noValidate>
        <Stack direction="column" spacing={2.5}>
          <TextField
            label="WebサイトのURL"
            type="url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            fullWidth
            required
            disabled={isLocked}
            error={urlMessage !== undefined}
            helperText={urlMessage}
            autoComplete="url"
            inputProps={{ inputMode: "url", maxLength: MAX_FIELD_LENGTH }}
          />
          <TextField
            label="事業概要"
            value={businessDescription}
            onChange={(e) => setBusinessDescription(e.target.value)}
            fullWidth
            multiline
            minRows={3}
            required
            disabled={isLocked}
            error={businessDescriptionMessage !== undefined}
            helperText={businessDescriptionMessage}
            inputProps={{ maxLength: MAX_FIELD_LENGTH }}
          />
          <TextField
            label="想定顧客"
            value={targetAudience}
            onChange={(e) => setTargetAudience(e.target.value)}
            fullWidth
            multiline
            minRows={3}
            required
            disabled={isLocked}
            error={targetAudienceMessage !== undefined}
            helperText={targetAudienceMessage}
            inputProps={{ maxLength: MAX_FIELD_LENGTH }}
          />
          <TextField
            label="注力点"
            value={strategicFocus}
            onChange={(e) => setStrategicFocus(e.target.value)}
            fullWidth
            multiline
            minRows={3}
            required
            disabled={isLocked}
            error={strategicFocusMessage !== undefined}
            helperText={strategicFocusMessage}
            inputProps={{ maxLength: MAX_FIELD_LENGTH }}
          />
          <Button type="submit" variant="contained" disabled={isLocked} sx={{ alignSelf: "flex-start" }}>
            送信
          </Button>
        </Stack>
      </form>
    </Paper>
  );
}
