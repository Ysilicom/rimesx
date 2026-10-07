# RIMES X Agent Guidelines & Build Rules

## ⚠️ STRICT RULE: NO LOCAL COMPILATION

1. **NEVER compile native or Android binaries on the local machine**:
   - DO NOT run `python3 platforms/android/scripts/build-engine.py` locally.
   - DO NOT run `./gradlew assembleRelease` or `./gradlew assembleDebug` locally.
   - DO NOT run `ninja`, `cmake`, or Clang compilations locally.
   - Local device CPU/memory is strictly reserved for code editing, git operations, and lightweight tasks.

2. **ALL BUILD & PACKAGING MUST USE GITHUB ACTIONS (CLOUD COMPILATION)**:
   - Workflow file: `.github/workflows/build-rimesx.yml`
   - All C++ compilation, R8 optimizations, self-signing, and APK artifacts are handled on GitHub Cloud Runners.
   - Push code to `main` or trigger via GitHub Actions web interface (`workflow_dispatch`).
