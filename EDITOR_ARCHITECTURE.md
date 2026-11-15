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

## Non-goals for the first release
Desktop word-processor pagination, columns, equation editor, real-time collaboration, and arbitrary plugin execution.
