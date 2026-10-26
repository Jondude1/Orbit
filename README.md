# Orbit

A place to dump every idea, then have it swing back around at a good time.

- **Catch** thoughts fast: home screen box, voice, Quick Settings tile, share sheet, launcher shortcuts, Ctrl+Enter on a keyboard cover.
- **Resurface**: thoughts come back only at your chosen review time and days. "Still excites me" brings an idea back sooner; "Not now" spaces it further out (1 → 3 → 7 → 14 → 30 → 60 → 90 days).
- **In motion**: start an idea with one tiny next step. Things you've started but not touched in a week come back with "Still on this?"
- **Build**: on an app or project idea, tap *Write a spec* and AI (Claude, or your own local model) turns it into a one-page plan. Then *Create GitHub repo* makes a public repo with SPEC.md, CLAUDE.md and a self-updating Android starter (CI builds a signed APK and release on every push; the signing key is made on the first build). *Copy build prompt* hands it to Claude Code. Set keys up in Settings → Build (GitHub token needs `repo` + `workflow`).
- Built for the Galaxy Tab S9+ (two-pane layouts, navigation rail), works on phones too.

Every push to `main` builds a signed APK and publishes it as a GitHub Release; the app updates itself from there.
