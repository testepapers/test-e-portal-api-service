# Liquibase Database Changelog

This directory contains Liquibase changelog files for managing database schema migrations.

## Structure

```
db/changelog/
├── db.changelog-master.json          # Master changelog file
└── changes/
    ├── 001-create-users-table.json   # Users table (existing)
    ├── 002-add-indexes.json          # Indexes (existing)
    ├── 003-create-core-catalogs.json # Core catalog tables
    ├── 004-create-tenant-and-region.json # Tenant and region tables
    └── 005-create-question-table.json   # Question table and related objects
```

## Changelog Files

### 003-create-core-catalogs.json
Creates the core catalog tables:
- `board` - Educational boards (CBSE, ICSE, etc.)
- `grade` - Grade levels
- `subject` - Subjects (Mathematics, Science, etc.)
- `chapter` - Chapters within subjects
- `question_type` - Question type definitions (MCQ, Match, etc.)

### 004-create-tenant-and-region.json
Creates multi-tenant support tables:
- `region` - Geographic regions
- `tenant` - Tenant/organization information

### 005-create-question-table.json
Creates the main question table and related objects:
- `question` - Main question table with:
  - Foreign keys to board, grade, subject, chapter, question_type, tenant
  - JSONB columns for spec, solution, source
  - Array column for tags
  - Timestamps (created_at, updated_at)
  - Marks column for scoring
- Indexes:
  - Composite indexes for common query patterns
  - GIN indexes for JSONB columns (spec, solution)
  - GIN index for tags array
  - Unique index on seed_key (where not null)
- Constraints:
  - Check constraint for difficulty values
- Triggers:
  - Auto-update trigger for updated_at timestamp

## Execution Order

The changelogs are executed in the order specified in `db.changelog-master.json`:

1. Users table (001)
2. Indexes (002)
3. Core catalogs (003) - Must run before question table
4. Tenant and region (004) - Must run before question table
5. Question table (005) - Depends on all previous changelogs

## Running Migrations

### Automatic (Spring Boot)
Liquibase runs automatically on application startup if configured in `application.yaml`:

```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.json
    default-schema: public
```

### Manual
```bash
# Using Liquibase CLI
liquibase --changeLogFile=db/changelog/db.changelog-master.json update

# Using Gradle (if plugin configured)
./gradlew liquibaseUpdate
```

## Rollback

Each changelog includes rollback instructions. To rollback:

```bash
liquibase --changeLogFile=db/changelog/db.changelog-master.json rollbackCount 1
```

## Validation Service Dependencies

The validation service requires these tables:
- ✅ `question` - Main table for questions
- ✅ `question_type` - Question type lookup
- ✅ `tenant` - Required by question table (FK with default)

Optional dependencies (for full functionality):
- `board`, `grade`, `subject`, `chapter` - For question categorization
- `region` - For tenant home region

## Notes

- All tables use `IF NOT EXISTS` equivalent logic via Liquibase
- Foreign key constraints are properly ordered
- JSONB columns are indexed with GIN indexes for performance
- Timestamps are automatically managed via triggers
- The `marks` column defaults to 1 if not specified

## Adding New Changes

When adding new changelogs:

1. Create a new JSON file in `changes/` directory
2. Use sequential numbering (006, 007, etc.)
3. Add the include statement to `db.changelog-master.json`
4. Ensure proper rollback instructions
5. Test on a development database first
