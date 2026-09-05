-- Why: 競合比較機能の段階的廃止（ADR-031〜034）の C5-c。競合ルーブリック監査の永続化と
--      EXTRACTING_COMPETITORS ステータスを撤去する（不可逆・オーナー承認済み）。
--      jobs.competitor_rubric_audits_json は非 NULL 0 行。書き込み経路（JobBenchmarkCaptureService
--      の競合ループ）は C5-b で既に消滅しており、読み出し側の rubricGaps も競合が無い以上
--      常に空を返す死蔵だった。

-- Why: 制約より先に既存行を退避する。EXTRACTING_COMPETITORS は「クエリ追加を受け付ける
--      処理中」を表していたため、同じ意味を持つ CREATED へ寄せる（新しいガードも CREATED
--      のみをクエリ追加可能としている）。開発DBでは 0 行だが本番の取りこぼしを防ぐ。
UPDATE public.jobs SET job_status = 'CREATED' WHERE job_status = 'EXTRACTING_COMPETITORS';

ALTER TABLE public.jobs
    DROP COLUMN IF EXISTS competitor_rubric_audits_json;

-- Why: V134 で追加した CHECK は Java の JobStatus と値集合が一致していなければならない。
--      EXTRACTING_COMPETITORS を enum から外したため張り替える。
--      不一致は EnumCheckConstraintSyncTest が検出する。
ALTER TABLE public.jobs DROP CONSTRAINT IF EXISTS chk_jobs_job_status;
ALTER TABLE public.jobs
    ADD CONSTRAINT chk_jobs_job_status
    CHECK (job_status IN ('CREATED', 'REALTIME_PROCESSING', 'FILE_UPLOADED',
                          'SUBMITTED', 'RUNNING', 'COMPLETED', 'FAILED'));
