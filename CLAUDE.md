## Long-Running Project

This project uses session-persistent tracking. At the start of every session:
1. Read `docs/progress.md` silently for a full catch-up -- do not ask the user to re-explain anything.
2. Continue from the first incomplete task.
3. After each completed task, update `docs/progress.md` immediately (mark `[x]`, update Status Summary, update date).
