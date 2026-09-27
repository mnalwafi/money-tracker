# Workspace Agent Configuration

## Active Skills & Standards

This workspace is equipped with the following skills to guide development:

### 1. Ponytail (`.agents/skills/ponytail/`)
- Minimalist, senior-developer restraint.
- Enforce the ladder: YAGNI -> existing codebase utilities -> stdlib -> native platform features -> minimum code.
- Always prefer simplicity, eliminate unnecessary abstractions, and fix root causes directly.

### 2. Anti-Slop (`.agents/skills/antislop/`)
- Strict hygiene against generic AI code bloat, boilerplate comments, and redundant error handling.
- Maintain high design craft for UI components and clean code comments (`antislop-ui`, `antislop-code`).
- Follow established design tokens and avoid unneeded complexity.

### 3. CodeGraph (`.agents/skills/codegraph/`)
- Pre-indexed local code graph with SQLite + tree-sitter.
- Fast symbol search, caller/callee traversal, and impact analysis via `codegraph` CLI and MCP server.
- Index located at `.codegraph/`. Use `codegraph query <symbol>` and `codegraph callers <symbol>` for cross-file navigation.
