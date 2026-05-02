import { CssBaseline, ThemeProvider, createTheme } from "@mui/material";
import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { fetchWorkspaceBranding, fetchWorkspaceLogoBlob } from "../api/branding-api";

const DEFAULT_TOOL_NAME = "Geo Analytics";
const DEFAULT_BRAND_COLOR = "#4f46e5";

export type BrandingContextValue = {
  toolName: string;
  brandColor: string;
  logoBlobUrl: string | null;
};

export const BrandingContext = createContext<BrandingContextValue | null>(null);

export function WorkspaceBrandingProvider({ children }: { children: ReactNode }): JSX.Element {
  const [toolName, setToolName] = useState(DEFAULT_TOOL_NAME);
  const [brandColor, setBrandColor] = useState(DEFAULT_BRAND_COLOR);
  const [logoBlobUrl, setLogoBlobUrl] = useState<string | null>(null);
  const logoObjectUrlRef = useRef<string | null>(null);

  const revokeLogoUrl = useCallback(() => {
    if (logoObjectUrlRef.current !== null) {
      URL.revokeObjectURL(logoObjectUrlRef.current);
      logoObjectUrlRef.current = null;
    }
  }, []);

  useEffect(() => {
    document.documentElement.style.setProperty("--brand-color", brandColor);
  }, [brandColor]);

  useEffect(() => {
    document.title = `${toolName} · Dashboard`;
  }, [toolName]);

  const theme = useMemo(
    () =>
      createTheme({
        palette: {
          primary: {
            main: brandColor,
          },
        },
      }),
    [brandColor],
  );

  useEffect(() => {
    let cancelled = false;

    revokeLogoUrl();
    setLogoBlobUrl(null);

    void (async () => {
      try {
        const data = await fetchWorkspaceBranding();
        if (cancelled) return;
        const tn =
          typeof data.toolName === "string" && data.toolName.trim().length > 0
            ? data.toolName.trim()
            : DEFAULT_TOOL_NAME;
        const bc =
          typeof data.brandColor === "string" && data.brandColor.trim().length > 0
            ? data.brandColor.trim()
            : DEFAULT_BRAND_COLOR;
        setToolName(tn);
        setBrandColor(bc);

        const logoUrlField = typeof data.logoUrl === "string" ? data.logoUrl.trim() : "";
        if (logoUrlField.length === 0) {
          return;
        }
        try {
          const blob = await fetchWorkspaceLogoBlob();
          if (cancelled) return;
          revokeLogoUrl();
          const url = URL.createObjectURL(blob);
          if (cancelled) {
            URL.revokeObjectURL(url);
            return;
          }
          logoObjectUrlRef.current = url;
          setLogoBlobUrl(url);
        } catch {
          if (!cancelled) {
            revokeLogoUrl();
            setLogoBlobUrl(null);
          }
        }
      } catch {
        if (!cancelled) {
          setToolName(DEFAULT_TOOL_NAME);
          setBrandColor(DEFAULT_BRAND_COLOR);
          revokeLogoUrl();
          setLogoBlobUrl(null);
        }
      }
    })();

    return () => {
      cancelled = true;
      revokeLogoUrl();
      setLogoBlobUrl(null);
    };
  }, [revokeLogoUrl]);

  const contextValue = useMemo<BrandingContextValue>(
    () => ({
      toolName,
      brandColor,
      logoBlobUrl,
    }),
    [toolName, brandColor, logoBlobUrl],
  );

  return (
    <BrandingContext.Provider value={contextValue}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </BrandingContext.Provider>
  );
}
