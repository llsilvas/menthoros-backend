# V20 Migration Runbook: BIGINT → UUID Conversion for treino_planejado_id

**⚠️ CRITICAL MIGRATION - REQUIRES MANUAL VALIDATION**

## Overview
Converts `treino_planejado_id` from BIGINT to UUID in `tb_treino_realizado`.

**Risk Level:** HIGH (type change, data loss possible)
**Environment Check Required:** YES (validates environment before proceeding)

---

## Pre-Deployment Checklist

### Step 1: Backup Database
```bash
# PostgreSQL backup (production)
pg_dump -U postgres -h <host> <database> > /backups/v20_pre_migration_$(date +%Y%m%d_%H%M%S).sql

# Verify backup is readable
ls -lh /backups/v20_pre_migration*.sql
gunzip -t /backups/v20_pre_migration*.sql.gz  # if compressed
```

**Action:** Do not proceed until backup is verified on secondary storage.

---

### Step 2: Validate Environment State

Run this query BEFORE applying migration:

```sql
-- Check if there are existing BIGINT treino_planejado_id values
SELECT 
  COUNT(*) as linked_count,
  COUNT(DISTINCT treino_planejado_id) as unique_planned_ids
FROM tb_treino_realizado
WHERE treino_planejado_id IS NOT NULL;
```

**Expected Result (DEV/TEST):** `linked_count = 0`

**If linked_count > 0:**
- Environment has existing links that CANNOT be safely converted (BIGINT ≠ UUID)
- Two options:
  1. **Backfill Strategy:** Implement mapping table and INSERT...SELECT for safe migration
  2. **Full Wipe:** Delete all TreinoRealizado rows with treino_planejado_id IS NOT NULL (DATA LOSS)
- **DO NOT proceed with V20 as-is**

---

### Step 3: Verify TreinoPlanejado UUID Primary Key Exists

```sql
-- Confirm TreinoPlanejado table has UUID as PK
SELECT 
  column_name, 
  data_type, 
  is_nullable
FROM information_schema.columns
WHERE table_name = 'tb_treino_planejado' 
  AND column_name = 'id'
LIMIT 1;
```

**Expected Result:** `data_type = 'uuid'`, `is_nullable = 'NO'`

---

## Deployment Steps

### Step 4: Apply Migration
```bash
# Option A: Via Flyway (automatic on app startup)
# - Ensure spring.flyway.enabled=true in application.properties
# - Start application
# - Flyway detects V20 and executes

# Option B: Manual via CLI (if Flyway disabled)
psql -U postgres -h <host> -d <database> -f V20__Fix_treino_ids_to_uuid.sql
```

### Step 5: Monitor for Environmental Blocker
Flyway or psql will execute the `DO $$ RAISE EXCEPTION ...` block first.

**If it SUCCEEDS (no exception):**
- Environment validated ✓
- Migration proceeds ✓

**If it FAILS (exception raised):**
```
V20 MIGRATION BLOCKED: <N> registro(s) com treino_planejado_id existente(s)
não podem ser convertidos de BIGINT para UUID...
```
- **STOP IMMEDIATELY** — contact DevOps
- Do not force-apply migration
- Investigate and implement backfill strategy OR wipe data

---

## Post-Deployment Validation

### Step 6: Verify Migration Applied
```sql
-- Confirm old BIGINT column is gone
SELECT column_name 
FROM information_schema.columns
WHERE table_name = 'tb_treino_realizado' 
  AND column_name LIKE '%treino_planejado%'
ORDER BY column_name;
```

**Expected Result:**
- `treino_planejado_id` (UUID, nullable)
- ~~`treino_planejado_id_bigint_old`~~ (REMOVED)

### Step 7: Verify UUID Columns in Audit Table
```sql
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'tb_treino_reconciliacao'
  AND column_name IN ('before_planned_id_uuid', 'after_planned_id_uuid')
ORDER BY column_name;
```

**Expected Result:** Two UUID columns added

### Step 8: Smoke Test Application
```bash
# Start app and check logs
curl -X GET http://localhost:8080/api/health

# Verify no Flyway errors
grep -i "flyway\|migration" /var/log/application.log | tail -20
```

---

## Rollback Plan

If migration FAILS after backup but before verification:

### Option 1: Restore from Backup (Fastest)
```bash
psql -U postgres -h <host> -d <database> < /backups/v20_pre_migration_<timestamp>.sql
```

### Option 2: Manual Rollback (if restore fails)
```sql
-- Re-add BIGINT column
ALTER TABLE tb_treino_realizado 
ADD COLUMN treino_planejado_id_bigint_old BIGINT;

-- Drop UUID column
ALTER TABLE tb_treino_realizado 
DROP COLUMN treino_planejado_id;

-- Rename old column back
ALTER TABLE tb_treino_realizado 
RENAME COLUMN treino_planejado_id_bigint_old TO treino_planejado_id;
```

Then mark V20 as failed in Flyway metadata:
```sql
DELETE FROM flyway_schema_history WHERE version = '20';
```

---

## Operational Contacts

- **Primary DBA:** [contact]
- **DevOps Lead:** [contact]
- **On-Call:** [Slack channel]

---

## References

- V20 Migration File: `src/main/resources/db/migration/V20__Fix_treino_ids_to_uuid.sql`
- Related: TreinoPlanejado UUID primary key (established in V19)
- Design: Reconciliation audit trail requires UUID traceability (Design D8)

**Last Updated:** 2026-05-02
**Approved By:** [Codex Review - Round 3]
