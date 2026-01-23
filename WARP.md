# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Development Environment Setup

### Required Dependencies
- JDK 11 or newer (recommended from Adoptium)
- Mill 0.11.12 (build tool)
- Verilator 5.008
- Git

### Build System
This project uses Mill as the primary build tool. Here are the key commands:

```bash
# Compile the project
mill -i __.compile

# Run tests
mill -i __.test

# Generate Verilog
mill -i MarCore.runMain Elaborate -td build/

# Format code
mill -i __.reformat

# Check formatting
mill -i __.checkFormat

# Setup IDE support (BSP)
mill -i mill.bsp.BSP/install
```

## Project Architecture

### Directory Structure
The codebase follows a specific organization pattern:
- Parameters and constants are separated
- Components are divided between ISA-dependent and ISA-independent
- Architecture-specific vs architecture-independent components
- Pipeline units, functional units, and basic computational units are separated

Each directory containing a significant service component includes:
- A detailed README documenting:
  - Current development status
  - Directory design rationale
  - Known/potential issues
  - Unit test coverage
  - Future development suggestions (optional)

### Code Generation Guidelines
To maintain Verilog code readability:
1. Module encapsulation is preferred over function definitions for key signal operations
2. Use camelCase for signal naming (dots are translated to underscores in Verilog)
3. Break continuous operations into multiple steps to reduce intermediate variables
4. Use optimized operations (e.g., `in.orR()` instead of `.reduce(_||_)`)

## Development Workflow

### Branch Strategy
- Development work happens on feature branches
- Pull requests target the `dev_la` branch
- Keep local repository synced with upstream before submitting PRs

### Commit Standards
Format: `<type>(<scope>): <subject>`

Types:
- `feat` 🌟: New features
- `fix` 🐛: Bug fixes
- `refactor` 🔨: Code refactoring
- `perf` 🚀: Performance improvements
- `docs` 📚: Documentation changes
- `style` 🎨: Code style/formatting
- `test` ✅: Test changes
- `chore` 🏡: Maintenance tasks

Rules:
- Subject line should not exceed 72 characters
- Use imperative mood in subject line
- Link to relevant issues when applicable

### Code Quality Requirements
Pull requests may be rejected if:
1. Code doesn't follow src/README.md guidelines
2. Independent modules lack:
   - Unit tests with random parameters
   - Comprehensive documentation
3. Work-in-Progress (WIP) modules are submitted for merge

### File Organization
- Each file should focus on a single service or closely related set of services
- Recommended maximum of 72 characters per line
- Target maximum of 300 lines per file
- No more than 10 independent files per directory unless providing common services for subdirectories
