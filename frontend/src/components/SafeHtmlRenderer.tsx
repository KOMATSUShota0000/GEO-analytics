import DOMPurify, { type Config } from "dompurify";
import { useMemo } from "react";
export type SafeHtmlRendererProps = {
  html: string;
  className?: string;
  sanitizeConfig?: Config;
};
const defaultConfig: Config = {
  USE_PROFILES: { html: true },
};
export function SafeHtmlRenderer({ html, className, sanitizeConfig }: SafeHtmlRendererProps) {
  const sanitized = useMemo(() => {
    if (typeof window === "undefined") {
      return "";
    }
    const cfg = sanitizeConfig ?? defaultConfig;
    return DOMPurify.sanitize(html, cfg);
  }, [html, sanitizeConfig]);
  return <div className={className} dangerouslySetInnerHTML={{ __html: sanitized }} />;
}
