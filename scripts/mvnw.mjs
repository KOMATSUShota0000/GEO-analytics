#!/usr/bin/env node
// Why: npm スクリプトのシェルは Windows が cmd.exe、Unix が sh で、
//      Maven ラッパーの起動表記が両立しない（cmd.exe は "./mvnw" を解釈できず、
//      sh はカレントディレクトリを PATH 探索しないため "mvnw" が解決できない）。
//      package.json 側を単一表記に保つため、ここでプラットフォーム差を吸収する。
import { spawn } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const isWindows = process.platform === "win32";
const wrapper = join(projectRoot, isWindows ? "mvnw.cmd" : "mvnw");

const child = spawn(wrapper, process.argv.slice(2), {
  cwd: projectRoot,
  stdio: "inherit",
  // Why: Node 18.20 以降、.cmd/.bat の直接起動は shell 経由でないと拒否される
  shell: isWindows,
});

child.on("error", (err) => {
  console.error(`Maven ラッパーを起動できません: ${wrapper}\n${err.message}`);
  process.exit(1);
});
child.on("exit", (code, signal) => {
  process.exit(signal ? 1 : (code ?? 1));
});
