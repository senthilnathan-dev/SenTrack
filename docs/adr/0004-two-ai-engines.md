# ADR 0004: Two optional AI engines behind one assistant

- Status: Accepted (2026-10-20)

## Context
Users may want AI help with their notes, plans and money summaries, but the data is private and the phone is mid-range. A cloud
model is more capable; an on-device model keeps data on the phone but may be slow.

## Decision
- AI is off by default.
- One shared assistant interface (`AiAssist`, in `core-ui`) so feature modules contain no AI code.
- Two engines: an on-device open model through LiteRT-LM, set up with one tap and tested on the phone before use, and cloud
  providers (Gemini, OpenAI, Claude) with the user's own key.
- Cloud requests always show the exact text and need confirmation; on-device runs send nothing.
- Models in the catalog must be openly licensed, downloadable without an account, and pinned by SHA-256.
- Answers are screened by checks that do not depend on the model (invented or dropped numbers).

## Consequences
Features work unchanged with AI off. A new model needs a catalog entry (URL, size, hash). The on-device speed varies by phone,
so the setup includes a benchmark whose thresholds can be tuned in one place (`BenchmarkScoring`).
