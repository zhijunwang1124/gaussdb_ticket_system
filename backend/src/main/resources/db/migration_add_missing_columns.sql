-- 迁移脚本：更新ticket表结构，添加缺失的字段
-- 执行前请备份数据库！

-- 检查并添加缺失的字段
DO $$
BEGIN
    -- 如果字段不存在则添加
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '问题严重性'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "问题严重性" VARCHAR(128);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '实例简称'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "实例简称" VARCHAR(128);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '业务名称'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "业务名称" VARCHAR(256);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '描述'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "描述" TEXT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '滞留时间'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "滞留时间" VARCHAR(64);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = 'SLA时间'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "SLA时间" VARCHAR(64);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '问题审核人'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "问题审核人" VARCHAR(128);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '运维人员分析人'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "运维人员分析人" VARCHAR(128);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '开发人员分析人'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "开发人员分析人" VARCHAR(128);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '开发人员闭环人'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "开发人员闭环人" VARCHAR(128);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '运维人员闭环人'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "运维人员闭环人" VARCHAR(128);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '问题审核关闭人'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "问题审核关闭人" VARCHAR(128);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '创建日期'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "创建日期" TIMESTAMP;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '问题审核总时长'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "问题审核总时长" VARCHAR(64);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '运维人员分析总时长'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "运维人员分析总时长" VARCHAR(64);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '开发人员分析总时长'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "开发人员分析总时长" VARCHAR(64);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '开发人员闭环总时长'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "开发人员闭环总时长" VARCHAR(64);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '运维人员闭环总时长'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "运维人员闭环总时长" VARCHAR(64);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '问题审核关闭总时长'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "问题审核关闭总时长" VARCHAR(64);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '故障到恢复用时'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "故障到恢复用时" VARCHAR(64);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '是否涉及故障恢复'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "是否涉及故障恢复" VARCHAR(32);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '磐石版本是否涉及'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "磐石版本是否涉及" VARCHAR(32);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '是否需要预警'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "是否需要预警" VARCHAR(32);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = 'DTS单号'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "DTS单号" VARCHAR(64);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '规避措施'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "规避措施" TEXT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '是否质量问题'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "是否质量问题" VARCHAR(32);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '恢复方法'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "恢复方法" TEXT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '是否咨询问题'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "是否咨询问题" VARCHAR(32);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '是否涉及内核升级'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "是否涉及内核升级" VARCHAR(32);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '协同处理人'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "协同处理人" VARCHAR(256);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '高斯版本'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "高斯版本" VARCHAR(128);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = 'HCS版本号'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "HCS版本号" VARCHAR(128);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = 'HCS/轻量化'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "HCS/轻量化" VARCHAR(32);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '问题类型'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "问题类型" VARCHAR(128);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '根因分类'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "根因分类" VARCHAR(128);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '部署形态'
    ) THEN
        ALTER TABLE ticket ADD COLUMN "部署形态" VARCHAR(64);
    END IF;

    -- 数据迁移：如果旧的"起始日期"字段存在，将其值复制到"创建日期"
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '起始日期'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '创建日期'
    ) THEN
        UPDATE ticket SET "创建日期" = "起始日期" WHERE "创建日期" IS NULL;
    END IF;

END $$;

-- 显示迁移后的表结构
SELECT column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_name = 'ticket'
ORDER BY ordinal_position;