import { Box, CircularProgress } from "@mui/material";
import { useEffect, useState } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { WorkspaceBrandingProvider } from "../branding/WorkspaceBrandingProvider";
import { getAccessToken, setAccessToken, tryRestoreSession } from "./authSession";

type GateState = "loading" | "authed" | "anon";

export default function RequireAuthLayout(): JSX.Element {
  const location = useLocation();
  const [state, setState] = useState<GateState>("loading");

  useEffect(() => {
    let cancelled = false;

    if (
      typeof window !== "undefined" &&
      window.location.search.includes("internal_token")
    ) {
      if (!getAccessToken()) {
        const w = window as unknown as { __PDF_AUTH_TOKEN__?: string };
        const injected = w.__PDF_AUTH_TOKEN__;
        if (typeof injected === "string" && injected.trim().length > 0) {
          setAccessToken(injected.trim());
        }
      }
      if (!cancelled) {
        setState("authed");
      }
      return;
    }

    const existing = getAccessToken();
    if (existing !== null && existing.length > 0) {
      if (!cancelled) {
        setState("authed");
      }
      return;
    }

    (async () => {
      await tryRestoreSession();
      if (!cancelled) {
        setState(getAccessToken() ? "authed" : "anon");
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [location.pathname]);

  if (state === "loading") {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="40vh">
        <CircularProgress />
      </Box>
    );
  }
  if (state === "anon") {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return (
    <WorkspaceBrandingProvider>
      <Outlet />
    </WorkspaceBrandingProvider>
  );
}
