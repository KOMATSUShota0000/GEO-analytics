import type { RemediationTask } from "../types/analysis";

export const TASK_SECTION_FALLBACK_LABEL = "サイト全体・未分類";

export function getPriorityRank(priority: string): number {
  if (priority === "S") {
    return 1;
  }
  if (priority === "A") {
    return 2;
  }
  if (priority === "B") {
    return 3;
  }
  return 99;
}

export function compareTasks(a: RemediationTask, b: RemediationTask): number {
  const ra = getPriorityRank(a.priority);
  const rb = getPriorityRank(b.priority);
  if (ra !== rb) {
    return ra - rb;
  }
  if (b.impactScore !== a.impactScore) {
    return b.impactScore - a.impactScore;
  }
  return a.id.localeCompare(b.id);
}

export type TaskSectionGroup = {
  sectionLabel: string;
  tasks: RemediationTask[];
};

function effectiveSection(task: RemediationTask): string {
  const raw = task.targetSection?.trim();
  if (raw === undefined || raw.length === 0) {
    return TASK_SECTION_FALLBACK_LABEL;
  }
  return raw;
}

function compareSectionLabels(a: string, b: string): number {
  if (a === TASK_SECTION_FALLBACK_LABEL && b !== TASK_SECTION_FALLBACK_LABEL) {
    return 1;
  }
  if (b === TASK_SECTION_FALLBACK_LABEL && a !== TASK_SECTION_FALLBACK_LABEL) {
    return -1;
  }
  return a.localeCompare(b, "ja");
}

export function hasLockedRemediationTasks(tasks: RemediationTask[]): boolean {
  if (!Array.isArray(tasks) || tasks.length === 0) {
    return false;
  }
  return tasks.some((t) => t.isMasked === true);
}

export function groupTasksForDisplay(tasks: RemediationTask[]): TaskSectionGroup[] {
  if (!Array.isArray(tasks) || tasks.length === 0) {
    return [];
  }
  const map = new Map<string, RemediationTask[]>();
  for (let i = 0; i < tasks.length; i++) {
    const task = tasks[i];
    const key = effectiveSection(task);
    const bucket = map.get(key);
    if (bucket !== undefined) {
      bucket.push(task);
    } else {
      map.set(key, [task]);
    }
  }
  const keys = [...map.keys()].sort((x, y) => compareSectionLabels(x, y));
  const out: TaskSectionGroup[] = [];
  for (let i = 0; i < keys.length; i++) {
    const k = keys[i];
    const list = map.get(k);
    if (list === undefined) {
      continue;
    }
    const sorted = [...list].sort((a, b) => compareTasks(a, b));
    out.push({ sectionLabel: k, tasks: sorted });
  }
  return out;
}

function stripMarkupForClipboard(text: string): string {
  if (typeof text !== "string" || text.length === 0) {
    return "";
  }
  const noTags = text.replace(/<[^>]*>/g, "");
  return noTags.replace(/\s+/g, " ").trim();
}

export function buildClipboardText(task: RemediationTask): string {
  if (task.isMasked === true) {
    return task.title.trim();
  }
  const body = stripMarkupForClipboard(task.content);
  if (body.length === 0) {
    return task.title.trim();
  }
  return `${task.title.trim()}\n\n${body}`;
}
