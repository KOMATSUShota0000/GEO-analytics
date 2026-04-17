import { apiFetch } from "./apiFetch";
import { getAccessToken, tryRestoreSession } from "../auth/authSession";

function parseDownloadFilename(contentDisposition: string | null): string {
  if (contentDisposition == null || contentDisposition.length === 0) {
    return "report.pdf";
  }
  const utf8 = /filename\*=(?:UTF-8'')?([^;\s]+)/i.exec(contentDisposition);
  if (utf8) {
    return decodeURIComponent(utf8[1].trim().replace(/^"+|"+$/g, ""));
  }
  const quoted = /filename="([^"]+)"/i.exec(contentDisposition);
  if (quoted) {
    return quoted[1];
  }
  const plain = /filename=([^;\s]+)/i.exec(contentDisposition);
  if (plain) {
    return plain[1].trim().replace(/^"+|"+$/g, "");
  }
  return "report.pdf";
}

/**
 * ブラウザのナビゲーション（{@code <a href>} / {@code window.open}）では Authorization を付けられないため、
 * {@link apiFetch} で取得した Blob から一時 URL を切り出してダウンロードする。
 */
export async function downloadJobPdfWithAuth(jobId: string): Promise<void> {
  const id = jobId.trim();
  if (id.length === 0) {
    return;
  }

  const token = getAccessToken();
  if (token === null || token.length === 0) {
    await tryRestoreSession({});
  }

  const path = `/api/v1/jobs/${encodeURIComponent(id)}/pdf/download`;
  const res = await apiFetch(path, {
    method: "GET",
    headers: {
      Accept: "application/pdf",
    },
    cache: "no-store",
  });

  if (!res.ok) {
    const detail = await res.text().catch(() => "");
    throw new Error(`PDF download failed: HTTP ${res.status}${detail ? ` ${detail}` : ""}`);
  }

  const blob = await res.blob();
  const filename = parseDownloadFilename(res.headers.get("Content-Disposition"));
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = objectUrl;
  anchor.download = filename;
  anchor.rel = "noopener";
  anchor.style.display = "none";
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();

  // 同期直後の revoke はブラウザによってはダウンロードが中断されるため、短い遅延のうえ解放する
  window.setTimeout(() => {
    URL.revokeObjectURL(objectUrl);
  }, 2_500);
}
