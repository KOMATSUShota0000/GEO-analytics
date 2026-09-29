/**
 * GitHub Pages に置く公開版（`vite build --mode pages`）として動いているか。
 *
 * Why: 公開版はサーバー無しで画面のファイルだけを置くため、ログインや申し込みなど API が要る画面へは進めない（#189）。
 * 公開ページ（`/demo`・`/plans`）だけを出し、申し込みのボタンは押せないようにする。
 */
export const IS_PUBLIC_SITE = import.meta.env.MODE === "pages";
