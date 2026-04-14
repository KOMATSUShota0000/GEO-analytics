import { Box, CircularProgress } from "@mui/material";
import { useEffect, useState } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { getAccessToken, setAccessToken, tryRestoreSession } from "./authSession";

type GateState = "loading" | "authed" | "anon";

export default function RequireAuthLayout(): JSX.Element {
  const location = useLocation();
  const [state, setState] = useState<GateState>("loading");

  useEffect(() => {
    let cancelled = false;
    (async () => {
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
      if (getAccessToken()) {
        if (!cancelled) {
          setState("authed");
        }
        return;
      }
      await tryRestoreSession();
      if (!cancelled) {
        setState(getAccessToken() ? "authed" : "anon");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

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
  return <Outlet />;
}
