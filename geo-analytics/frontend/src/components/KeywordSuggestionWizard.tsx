import { registerProjectKeywords, suggestKeywords } from "../api/keywordApi";
import type { KeywordCategory, KeywordRegistrationResult, KeywordSuggestionResponse } from "../types/keyword";
import { AlertCircle, Loader2, RotateCcw, Sparkles } from "lucide-react";
import { useCallback, useState } from "react";

export type KeywordSuggestionWizardProps = {
  projectId: string | null;
  ensureProjectReady?: () => Promise<boolean>;
  onKeywordsSelected: (selectedKeywords: string[]) => void | Promise<void>;
  onRegistered?: (result: KeywordRegistrationResult) => void;
  isSubmitting: boolean;
};

type Phase = "input" | "loading" | "review" | "error";

function isValidHttpUrl(raw: string): boolean {
  const s = raw.trim();
  if (!s) return false;
  try {
    const u = new URL(s);
    return (u.protocol === "http:" || u.protocol === "https:") && u.hostname.length > 0;
  } catch {
    return false;
  }
}

export function KeywordSuggestionWizard({
  projectId,
  ensureProjectReady,
  onKeywordsSelected,
  onRegistered,
  isSubmitting,
}: KeywordSuggestionWizardProps): JSX.Element {
  const [phase, setPhase] = useState<Phase>("input");
  const [url, setUrl] = useState("");
  const [targetDescription, setTargetDescription] = useState("");
  const [response, setResponse] = useState<KeywordSuggestionResponse | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [keywordCategoryByText, setKeywordCategoryByText] = useState<Map<string, string>>(() => new Map());
  const [errorMessage, setErrorMessage] = useState("");
  const [registerPending, setRegisterPending] = useState(false);
  const canSuggest = isValidHttpUrl(url) && targetDescription.trim().length > 0;
  const busy = isSubmitting || registerPending;
  const categories = response?.categories ?? [];
  const selectedCount = selected.size;
  const toggleKeyword = useCallback((kw: string, categoryName: string) => {
    setSelected((prevSel) => {
      const wasOn = prevSel.has(kw);
      setKeywordCategoryByText((pm) => {
        const m = new Map(pm);
        if (wasOn) m.delete(kw);
        else m.set(kw, categoryName);
        return m;
      });
      const next = new Set(prevSel);
      if (wasOn) next.delete(kw);
      else next.add(kw);
      return next;
    });
  }, []);
  const categoryKeywordTexts = useCallback((cat: KeywordCategory) => {
    return cat.keywords.map((k) => k.trim()).filter((t) => t.length > 0);
  }, []);
  const toggleCategoryAll = useCallback((cat: KeywordCategory) => {
    const keys = categoryKeywordTexts(cat);
    if (keys.length === 0) return;
    setSelected((prevSel) => {
      const allOn = keys.every((k) => prevSel.has(k));
      const selectAll = !allOn;
      setKeywordCategoryByText((pm) => {
        const m = new Map(pm);
        for (const k of keys) {
          if (selectAll) m.set(k, cat.category_name);
          else m.delete(k);
        }
        return m;
      });
      const next = new Set(prevSel);
      for (const k of keys) {
        if (selectAll) next.add(k);
        else next.delete(k);
      }
      return next;
    });
  }, [categoryKeywordTexts]);
  const clearAllSelections = useCallback(() => {
    setSelected(new Set());
    setKeywordCategoryByText(new Map());
  }, []);
  const runSuggest = useCallback(async () => {
    if (!canSuggest) return;
    if (ensureProjectReady) {
      const ok = await ensureProjectReady();
      if (!ok) return;
    }
    setPhase("loading");
    setErrorMessage("");
    try {
      const data = await suggestKeywords(url, targetDescription);
      setResponse(data);
      const init = new Set<string>();
      const catMap = new Map<string, string>();
      for (const c of data.categories) {
        for (const k of c.keywords) {
          const t = k.trim();
          if (!t) continue;
          init.add(t);
          catMap.set(t, c.category_name);
        }
      }
      setSelected(init);
      setKeywordCategoryByText(catMap);
      setPhase("review");
    } catch (e: unknown) {
      setErrorMessage(e instanceof Error ? e.message : String(e));
      setPhase("error");
    }
  }, [canSuggest, ensureProjectReady, url, targetDescription]);
  const handleRegister = useCallback(async () => {
    if (busy || selectedCount === 0) return;
    setRegisterPending(true);
    try {
      const list = Array.from(selected);
      if (projectId) {
        const payload = list.map((k) => ({
          text: k,
          category_name: keywordCategoryByText.get(k) ?? "業界・一般",
        }));
        const result = await registerProjectKeywords(projectId, payload);
        onRegistered?.(result);
      }
      await Promise.resolve(onKeywordsSelected(list));
      setUrl("");
      setTargetDescription("");
      setResponse(null);
      setSelected(new Set());
      setKeywordCategoryByText(new Map());
      setPhase("input");
    } catch (e: unknown) {
      setErrorMessage(e instanceof Error ? e.message : String(e));
      setPhase("error");
    } finally {
      setRegisterPending(false);
    }
  }, [busy, keywordCategoryByText, onKeywordsSelected, onRegistered, projectId, selected, selectedCount]);
  return (
    <div className="flex min-h-0 w-full flex-col overflow-hidden rounded-2xl border border-slate-200/90 bg-gradient-to-b from-white to-slate-50/80 shadow-[0_1px_0_rgba(15,23,42,0.06),0_12px_40px_-12px_rgba(15,23,42,0.12)]">
      <div className="border-b border-slate-200/80 bg-white/90 px-5 py-4 backdrop-blur-sm">
        <div className="flex items-center gap-2">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-md shadow-indigo-500/25">
            <Sparkles className="h-4 w-4" strokeWidth={2.25} />
          </div>
          <div>
            <h2 className="text-sm font-semibold tracking-tight text-slate-900">AIキーワード提案</h2>
            <p className="text-xs text-slate-500">サイトとターゲットから、SGE向け候補を生成します</p>
          </div>
        </div>
      </div>
      <div className="relative max-h-[min(70vh,520px)] flex-1 overflow-y-auto">
        {phase === "input" && (
          <div className="space-y-4 p-5">
            <div className="space-y-1.5">
              <label htmlFor="kw-wizard-url" className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                解析したいサイトURL
              </label>
              <input
                id="kw-wizard-url"
                type="url"
                inputMode="url"
                autoComplete="url"
                placeholder="https://example.com"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm outline-none ring-indigo-500/0 transition placeholder:text-slate-400 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
              />
            </div>
            <div className="space-y-1.5">
              <label htmlFor="kw-wizard-target" className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                ターゲット層
              </label>
              <textarea
                id="kw-wizard-target"
                rows={4}
                placeholder="例：共働きの30代、BtoBの情シス担当者など"
                value={targetDescription}
                onChange={(e) => setTargetDescription(e.target.value)}
                className="w-full resize-none rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm outline-none ring-indigo-500/0 transition placeholder:text-slate-400 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
              />
            </div>
            <button
              type="button"
              disabled={!canSuggest || isSubmitting}
              onClick={() => void runSuggest()}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 py-3 text-sm font-semibold text-white shadow-lg shadow-indigo-500/30 transition hover:bg-indigo-700 active:scale-[0.99] disabled:cursor-not-allowed disabled:bg-slate-300 disabled:shadow-none"
            >
              {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
              提案させる
            </button>
          </div>
        )}
        {phase === "loading" && (
          <div className="flex min-h-[240px] flex-col items-center justify-center gap-5 px-6 py-12">
            <div className="relative">
              <div className="absolute inset-0 animate-ping rounded-full bg-indigo-400/30" />
              <Loader2 className="relative h-11 w-11 animate-spin text-indigo-600" strokeWidth={2.25} />
            </div>
            <div className="text-center">
              <p className="text-sm font-semibold text-slate-800">AIが分析中…</p>
              <p className="mt-1 text-xs leading-relaxed text-slate-500">数十秒かかる場合があります</p>
            </div>
            <div className="flex gap-1">
              {[0, 1, 2].map((i) => (
                <span
                  key={i}
                  className="h-1.5 w-1.5 rounded-full bg-indigo-500/40 animate-pulse"
                  style={{ animationDelay: `${i * 160}ms` }}
                />
              ))}
            </div>
          </div>
        )}
        {phase === "review" && (
          <div className="space-y-6 p-5 pb-28">
            <div className="rounded-xl border border-amber-200/80 bg-gradient-to-r from-amber-50/90 to-orange-50/50 px-4 py-3 shadow-sm">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <p className="text-xs font-medium leading-relaxed text-slate-700">
                  カテゴリ別に選び直したい場合は、ここからすべて未選択にできます。
                </p>
                <button
                  type="button"
                  onClick={clearAllSelections}
                  disabled={selectedCount === 0}
                  className="shrink-0 rounded-lg border border-slate-200/90 bg-white px-3 py-2 text-xs font-semibold text-rose-700 shadow-sm transition hover:border-rose-200 hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-white"
                >
                  すべての選択を解除する
                </button>
              </div>
            </div>
            {categories.map((cat: KeywordCategory) => {
              const catKeys = categoryKeywordTexts(cat);
              const allInCategorySelected =
                catKeys.length > 0 && catKeys.every((k) => selected.has(k));
              return (
              <section key={cat.category_name} className="rounded-xl border border-slate-200/80 bg-white/90 p-4 shadow-sm">
                <div className="mb-3 flex items-start justify-between gap-3">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-indigo-700">{cat.category_name}</h3>
                  {catKeys.length > 0 ? (
                    <button
                      type="button"
                      onClick={() => toggleCategoryAll(cat)}
                      className="shrink-0 text-xs text-indigo-600 hover:underline"
                    >
                      {allInCategorySelected ? "すべて解除" : "すべて選択"}
                    </button>
                  ) : null}
                </div>
                <div className="flex flex-wrap gap-2">
                  {cat.keywords.map((kw) => {
                    const k = kw.trim();
                    if (!k) return null;
                    const on = selected.has(k);
                    return (
                      <button
                        key={`${cat.category_name}:${k}`}
                        type="button"
                        onClick={() => toggleKeyword(k, cat.category_name)}
                        className={`rounded-full border px-3 py-1.5 text-xs font-medium transition active:scale-[0.97] ${
                          on
                            ? "border-indigo-500 bg-indigo-600 text-white shadow-md shadow-indigo-500/25 ring-2 ring-indigo-400/40"
                            : "border-slate-200 bg-slate-100 text-slate-600 hover:border-slate-300 hover:bg-slate-50"
                        }`}
                      >
                        {k}
                      </button>
                    );
                  })}
                </div>
              </section>
            );
            })}
          </div>
        )}
        {phase === "error" && (
          <div className="flex min-h-[220px] flex-col items-center justify-center gap-4 px-6 py-10 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-rose-50 text-rose-600">
              <AlertCircle className="h-6 w-6" />
            </div>
            <p className="max-w-sm text-sm text-slate-700">{errorMessage}</p>
            <div className="flex flex-wrap justify-center gap-2">
              <button
                type="button"
                onClick={() => void runSuggest()}
                className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-md shadow-indigo-500/25 transition hover:bg-indigo-700"
              >
                <RotateCcw className="h-4 w-4" />
                リトライ
              </button>
              <button
                type="button"
                onClick={() => {
                  setPhase("input");
                  setErrorMessage("");
                }}
                className="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
              >
                入力に戻る
              </button>
            </div>
          </div>
        )}
      </div>
      {phase === "review" && (
        <div className="sticky bottom-0 z-20 border-t border-slate-200/90 bg-white/95 px-5 py-4 shadow-[0_-8px_30px_-12px_rgba(15,23,42,0.15)] backdrop-blur-md">
          <button
            type="button"
            disabled={busy || selectedCount === 0}
            onClick={() => void handleRegister()}
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 py-3.5 text-sm font-semibold text-white shadow-lg shadow-indigo-500/30 transition hover:bg-indigo-700 active:scale-[0.995] disabled:cursor-not-allowed disabled:bg-slate-300 disabled:shadow-none"
          >
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            選択した{selectedCount}件を登録する
          </button>
        </div>
      )}
    </div>
  );
}
