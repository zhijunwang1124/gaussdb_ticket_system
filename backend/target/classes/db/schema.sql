CREATE TABLE IF NOT EXISTS ticket (
    id BIGSERIAL PRIMARY KEY,
    yw_no VARCHAR(64) NOT NULL UNIQUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE ticket DROP COLUMN IF EXISTS raw_data_json;
ALTER TABLE ticket DROP COLUMN IF EXISTS title;
ALTER TABLE ticket DROP COLUMN IF EXISTS create_log_text;
ALTER TABLE ticket DROP COLUMN IF EXISTS created_time;

ALTER TABLE ticket ADD COLUMN IF NOT EXISTS "起始日期" TIMESTAMP;
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS "当前阶段" VARCHAR(128);
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS "局点" VARCHAR(256);
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS "当前处理人" VARCHAR(128);
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS "问题描述" TEXT;
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS "进展概述" TEXT;
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS "管控版本" VARCHAR(128);
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS "内核版本" VARCHAR(128);
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS "问题根因" TEXT;
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS "对外答复" TEXT;

CREATE TABLE IF NOT EXISTS analysis_column_config (
    id BIGSERIAL PRIMARY KEY,
    column_name VARCHAR(128) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    allowed_values JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE analysis_column_config ADD COLUMN IF NOT EXISTS column_key VARCHAR(64);
ALTER TABLE analysis_column_config ADD COLUMN IF NOT EXISTS is_top_level BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE analysis_column_config SET column_key = ('col_' || id::text)
    WHERE column_key IS NULL OR trim(column_key) = '';
UPDATE analysis_column_config SET is_top_level = TRUE
    WHERE column_key IN ('product_category', 'is_quality_issue');
UPDATE analysis_column_config SET is_top_level = FALSE
    WHERE column_key IN ('level1_category');
ALTER TABLE analysis_column_config ALTER COLUMN column_key SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_analysis_column_config_column_key ON analysis_column_config (column_key);

CREATE TABLE IF NOT EXISTS ticket_analysis_result (
    yw_no VARCHAR(64) PRIMARY KEY REFERENCES ticket(yw_no) ON DELETE CASCADE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ticket_analysis_snapshot (
    id BIGSERIAL PRIMARY KEY,
    yw_no VARCHAR(64) NOT NULL UNIQUE,
    analysis_result_json JSONB NOT NULL,
    analyzed_at TIMESTAMP NOT NULL,
    model_name VARCHAR(128),
    task_id BIGINT
);

CREATE TABLE IF NOT EXISTS analyze_task (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS analyze_task_item (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    yw_no VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO analysis_column_config(column_key, column_name, description, allowed_values, is_top_level)
VALUES
('product_category', '产品分类', '根据工单内容判断产品类别', '["管控问题","内核问题","咨询问题"]', TRUE),
('is_quality_issue', '是否质量问题', '判断是否属于质量问题', '["是","否"]', TRUE),
('level1_category', '一级分类', '根据故障性质划分一级分类', '["慢","满","夯","宕","错"]', FALSE)
ON CONFLICT (column_name) DO NOTHING;
