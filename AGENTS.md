# AGENTS.md

Instructions for coding agents working in this repository. The [README](README.md) is the
reference for setup, layout and CI; this file adds what an agent needs to work here and doesn't
repeat it. Read the README's "Rules to know before writing code" before changing code.

## Where the requirements live

Read the user story and its feature file before writing code. If the docs and the code disagree,
say so when you hand the work over instead of picking one silently.

| File | What it is |
|---|---|
| `docs/defiro-MVP-user-stories.pdf` | The MVP's user stories (UC01, UC02, ...) and their acceptance criteria |
| `docs/defiro-QA-Strategy.pdf` | How a story goes from examples to Gherkin to code (discuss, distill, develop, demo), the test levels, and the Definition of Done |
| `docs/defiro_architecture_mvp.drawio` | Architecture: Workflow Designer, Workflow Engine, Plugin Registry and the built-in plugins, their stores, and who uses what |
| `docs/sequence_diagram.txt` | PlantUML sequence of the MVP procedure (travel expense reimbursement), from workflow set-up to the closed request, by use case |
| `docs/workflow-specification.md` | The workflow file: the JSON format the Designer publishes and the Engine runs |
| `docs/ucxx-<slug>-design.md` | Design notes for one story, when it has them |
| `apps/server/src/test/resources/features/<module>/UCxx-<slug>.feature` | The story's agreed behaviour, in Gherkin |

Stay within the MVP: if a story raises a question the MVP doesn't cover, leave it out of the work
and mention it when you hand the work over.

## Commands

Use JDK 21: a newer JDK breaks the formatting check with a `NoSuchFieldError`.

```bash
./mvnw spotless:apply                                  # format Java before committing
./mvnw verify -DskipITs                                # while iterating: build and unit tests, no Docker
./mvnw verify -pl apps/server -am -DskipITs -Dtest=WorkflowDraftTest -Dsurefire.failIfNoSpecifiedTests=false
./mvnw verify -DskipUTs -Dcucumber.filter.tags="@UC03" # one story's scenarios; needs Docker
./mvnw verify                                          # everything CI runs, before opening a pull request
```

Web app, in `apps/web`: `npm run check` (what CI runs), `npm run format`, `npm test`.

## How a story gets done

1. **Gherkin first.** Write or extend the story's feature file, in Portuguese (`# language: pt`
   on the first line): the client is Portuguese and the scenarios are meant to be read by anyone.
   It describes the behaviour defined now, with one `Regra:` per acceptance criterion, tagged
   `@UCxx @wip`. Examples use the web prototype's people: Ana Ferreira (requester), Rui Matos,
   Carla Dias, Joana Pires (HR validator), Sofia Almeida (Workflow Manager).
2. **Gherkin is reviewed by a maintainer before code.** Don't open a pull request for draft
   scenarios: push the branch, share it for review, and wait.
3. **Tests before code.** Then the code that makes them pass. A fixed defect starts with a test
   that reproduces it. Remove `@wip` once a scenario passes.
4. **Scope to the prototype.** Build what the web prototype shows, not more.
5. **Branch, then pull request only when asked.** Name the branch after the story
   (`feature/UC18-reject-reasons`), from `develop`. Push it and share the branch; open a pull
   request only when a maintainer asks for one. When you do, base it on `develop` and fill in
   `.github/pull_request_template.md`, including its Definition of Done.

## Conventions the tools don't check

- Text people see in the app is in Portuguese, and names of screens and buttons are quoted with
  «», as in «Novo pedido». Code, comments and commit messages are in English.
- Commit messages follow Conventional Commits with the story as scope: `feat(designer): ... (UC04)`.

## Boundaries

- Never commit secrets, tokens or `.env` files.
- Never skip, disable or loosen a test, a quality gate condition or a Sonar rule to get a green
  build. If a check is wrong, say why in the pull request.
- Ask before changing `.github/workflows/`, an existing Flyway migration, or a module's allowed
  dependencies in `package-info.java`.
- Don't add a dependency without saying why in the pull request.
