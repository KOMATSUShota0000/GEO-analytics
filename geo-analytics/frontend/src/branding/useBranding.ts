import { useContext } from "react";
import { BrandingContext, type BrandingContextValue } from "./WorkspaceBrandingProvider";

export function useBranding(): BrandingContextValue {
  const ctx = useContext(BrandingContext);
  if (ctx === null) {
    throw new Error("useBranding must be used within WorkspaceBrandingProvider");
  }
  return ctx;
}
