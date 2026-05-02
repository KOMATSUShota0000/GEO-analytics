import { apiFetch, responseJsonAsCamel } from "./apiFetch";

export type WorkspaceBrandingPayload = {
  toolName?: string | null;
  brandColor?: string | null;
  logoUrl?: string | null;
};

export async function fetchWorkspaceBranding(): Promise<WorkspaceBrandingPayload> {
  const res = await apiFetch("/api/v1/workspaces/current/branding");
  if (!res.ok) {
    throw new Error(`branding ${res.status}`);
  }
  const raw = await responseJsonAsCamel(res);
  return raw as WorkspaceBrandingPayload;
}

export async function fetchWorkspaceLogoBlob(): Promise<Blob> {
  const res = await apiFetch("/api/v1/workspaces/current/branding/logo");
  if (!res.ok) {
    throw new Error(`logo ${res.status}`);
  }
  return res.blob();
}
