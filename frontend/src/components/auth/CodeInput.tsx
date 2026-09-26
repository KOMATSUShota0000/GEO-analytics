import { useEffect, useRef } from "react";
import { CODE_LENGTH, normalizeCode } from "../../auth/loginCode";

type Props = {
  /** 先頭から詰めた半角数字（0〜6桁）。 */
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  autoFocus?: boolean;
  invalid?: boolean;
};

/**
 * ログインコードを1マス1桁で入れる6つの欄（#164）。
 *
 * 値は先頭から詰めた数字として持ち、空いたマスを挟まない。途中のマスを押しても、最初の空きマスへ移る。
 */
export default function CodeInput({ value, onChange, disabled = false, autoFocus = false, invalid = false }: Props) {
  const inputs = useRef<(HTMLInputElement | null)[]>([]);
  // Why: 1桁入れて次のマスへ移した直後の onFocus では、props の value はまだ入力前の古い値のまま。
  //      古い値で「空きより後ろのマス」と判定すると1マス目へ引き戻され、続けて打った数字が前の数字を上書きする。
  //      入力のたびにここへ最新の値を先に書き、判定はこれを見る。
  const latest = useRef(value);
  latest.current = value;

  const commit = (next: string) => {
    latest.current = next;
    onChange(next);
  };

  useEffect(() => {
    if (autoFocus) {
      inputs.current[Math.min(value.length, CODE_LENGTH - 1)]?.focus();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoFocus]);

  const focusAt = (index: number) => {
    const target = inputs.current[Math.max(0, Math.min(CODE_LENGTH - 1, index))];
    target?.focus();
    target?.select();
  };

  // Why: メールからの自動入力（one-time-code）や貼り付けは、1つのマスに6桁まとめて入ってくる。
  //      1マス1文字に制限すると切り捨てられるので、入ってきた数字をそのマスから後ろへ振り分ける。
  const put = (index: number, raw: string) => {
    const digits = normalizeCode(raw);
    if (digits.length === 0) return;
    const current = latest.current;
    const next =
      digits.length >= CODE_LENGTH
        ? digits
        : normalizeCode(current.slice(0, index) + digits + current.slice(index + digits.length));
    commit(next);
    focusAt(Math.min(index + digits.length, CODE_LENGTH - 1));
  };

  const handleKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    const current = latest.current;
    if (e.key === "Backspace") {
      e.preventDefault();
      if (index < current.length) {
        commit(current.slice(0, index) + current.slice(index + 1));
      } else if (index > 0) {
        commit(current.slice(0, index - 1));
        focusAt(index - 1);
      }
    } else if (e.key === "ArrowLeft") {
      e.preventDefault();
      focusAt(index - 1);
    } else if (e.key === "ArrowRight") {
      e.preventDefault();
      focusAt(Math.min(index + 1, current.length));
    }
  };

  return (
    <div role="group" aria-label="6桁のコード" className="flex justify-between gap-2">
      {Array.from({ length: CODE_LENGTH }, (_, index) => (
        <input
          key={index}
          ref={(el) => {
            inputs.current[index] = el;
          }}
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          autoComplete={index === 0 ? "one-time-code" : "off"}
          aria-label={`コードの${index + 1}桁目`}
          aria-invalid={invalid || undefined}
          value={value[index] ?? ""}
          disabled={disabled}
          onFocus={(e) => {
            if (index > latest.current.length) {
              focusAt(latest.current.length);
            } else {
              e.currentTarget.select();
            }
          }}
          onChange={(e) => put(index, e.target.value)}
          onKeyDown={(e) => handleKeyDown(index, e)}
          onPaste={(e) => {
            e.preventDefault();
            put(index, e.clipboardData.getData("text"));
          }}
          className={[
            "h-14 w-full min-w-0 rounded-lg border bg-white text-center text-2xl font-semibold text-slate-900",
            "transition focus:outline-none focus:ring-2 disabled:bg-slate-50 disabled:text-slate-400",
            invalid
              ? "border-red-300 focus:border-red-400 focus:ring-red-200"
              : "border-slate-300 focus:border-indigo-500 focus:ring-indigo-500/20",
          ].join(" ")}
        />
      ))}
    </div>
  );
}
