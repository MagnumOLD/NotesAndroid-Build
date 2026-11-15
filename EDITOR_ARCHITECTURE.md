# Advanced document editor — implementation specification

## Product decisions
- The editor is a new, block-based document subsystem. Existing task and purchase editors remain separate.
- The old editor model is not retained as a runtime compatibility layer.
- Existing note content is preserved through a one-time migration. The migration is explicit, idempotent, backed up, and only switches the active store after validation.
- Documents are typed and versioned. Assets are stored separately from document JSON.
- The first implementation milestone builds the document core independently before UI replacement.

## Document model
A document has a stable ID, schema version, timestamps, metadata, and an ordered list of typed blocks. Blocks have stable IDs. Text is represented by text-bearing blocks with inline spans whose ranges are UTF-16 offsets (matching Kotlin/Compose text APIs). Spans are normalized, non-empty, and must stay within the text length.

Initial block types: paragraph, heading (levels 1–3), ordered/unordered list with nested items, checklist, quote, code, table, image, attachment, link, callout, and divider.

Tables contain rows and cells; each cell owns text and inline spans. Images and attachments reference opaque asset IDs. Binary content never lives inside document JSON.

## Editor state and engines
- Document state is authoritative and independent of Compose.
- Compose text input state is a transient editing adapter for the active text block; it is not the persistence format.
- Input, selection, document mutation, history, rendering, and assets have separate responsibilities.
- Mutations are expressed as deterministic commands. History groups adjacent typing into transactions and supports undo/redo for structural and inline changes.
- Stable block IDs are required for lazy rendering, focus restoration, and selection mapping.

## Persistence
- A versioned JSON codec is the initial storage interchange format.
- Repository writes are atomic at the document boundary; debounced writes must be flushed on editor exit and lifecycle stop.
- A recovery journal stores the last known unsaved document snapshot.
- Assets are stored under app-private storage and referenced by ID.
- Garbage collection must only delete assets proven to be unreferenced by all live documents, vault entries, recovery snapshots, and backups.

## One-time legacy migration
1. Snapshot and checksum the legacy record store.
2. Convert each legacy NOTE's NoteBlock list to paragraph/checklist blocks, preserving text, checked state, timestamps, IDs where possible, and metadata.
3. Convert collections, archive, and trash recursively.
4. Validate document counts, text content, and asset references.
5. Write the new store and migration receipt atomically.
6. Keep the original snapshot untouched until the new store has been opened and verified.
7. Never repeatedly migrate at startup. The receipt records source and target schema versions and completion.

## Implementation gates
1. Model + codec + deterministic operations and tests.
2. Text editing adapter, cursor/selection mapping, and command history.
3. Basic blocks and renderer.
4. Advanced blocks and asset storage.
5. New screen and toolbars.
6. Repository, recovery, and migration.
7. Integration, regression tests, performance, and APK verification.

## Extended backup format (implementation status)
- The Settings export action now creates a ZIP archive containing the legacy record snapshot, time-tracking state, rich-document JSON, and referenced binary assets.
- The import picker accepts both ZIP archives and legacy JSON backups (versions 1–7).
- ZIP parsing stages files privately and validates the manifest, duplicate IDs, document identity, asset references, path safety, per-entry/total size limits, and ZIP central directory before any live-store changes.
- Restore copies each asset to app-private storage under a fresh ID, rewrites image/attachment references, and remaps colliding document IDs rather than overwriting existing documents.
- If document or asset installation fails, newly installed files are rolled back. Legacy records are merged only after ZIP payload validation and rich-document installation.
- The migration startup gate creates a checksum-verified backup before migrating NOTE records. The user can retry a failed migration or continue using the legacy editor without setting the migration-complete marker.
- Migration backups are fingerprinted per legacy snapshot; a changed snapshot gets a separate backup rather than overwriting an earlier recovery point.
- Automated tests cover archive round-trip, truncated archives, missing assets, restore collisions, and asset-reference remapping. Full CI and emulator validation remain required.
- Ordinary NOTE records now open the persistent advanced document editor after the startup migration marker is verified. The legacy note record keeps a plain-text preview/search mirror; rich content remains the document source of truth.
- TASK records continue to use the existing task editor and time-tracking path. Migration failure offers a retry or a legacy-editor fallback without setting the completion marker.
- The editor route flushes on back navigation and lifecycle stop, offers explicit recovery-draft restore/discard, and preserves note share/delete actions.
- New notes open the document editor with keyboard autofocus; empty notes are discarded without leaving orphan document files.
- Permanent deletion from trash removes associated document files and collects assets only when no valid document or recovery draft references them.
- The latest emulator, unit-test, and APK checks are required after each integration change; final APK artifact verification and manual device QA remain outstanding.

## Non-goals for the first release
Desktop word-processor pagination, columns, equation editor, real-time collaboration, and arbitrary plugin execution.
