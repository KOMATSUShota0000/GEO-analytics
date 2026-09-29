import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv } from "vite";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "");
  const apiTarget = env.VITE_API_PROXY_TARGET || "http://127.0.0.1:8080";
  // Why: 公開版は GitHub Pages のリポジトリ名の下（例: /GEO-analytics/）に置かれる（#189）。
  //      置き場所は公開の手順が actions/configure-pages から渡す。普段のビルドはルートのまま。
  const pagesBase = `${(env.PAGES_BASE_PATH || "/GEO-analytics").replace(/\/$/, "")}/`;
  return {
    base: mode === "pages" ? pagesBase : "/",
    define: {
      global: "window",
      "process.env": {},
    },
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        "/api": {
          target: apiTarget,
          changeOrigin: true,
        },
        "/ws": {
          target: apiTarget,
          changeOrigin: true,
          secure: false,
          ws: true,
        },
      },
    },
  };
});
