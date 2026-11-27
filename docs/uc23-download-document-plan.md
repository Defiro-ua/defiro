# UC23 · Transferir um documento gerado: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the scenarios of `UC23-download-document.feature` pass, as written.

**Architecture:** The final document gets its own Engine table, `engine.generated_document`, with one row per procedure. Its bytes go in the existing `FileStore`. `FinalDocuments` lets only the requester and whoever closed the procedure have it, and serves it at `GET /api/engine/procedures/{reference}/document`. The web app fetches it as a blob. It shows the blob in an `<iframe>` in `DocumentDialog`, or downloads it at once when `navigator.pdfViewerEnabled` is false. «Os meus pedidos» and the closing notice open the dialog.

**Tech Stack:** Spring Boot, JdbcClient, Flyway (Postgres), Cucumber with Portuguese keywords (`io.cucumber.java.pt`), Playwright (`WebBrowser`), React, Vitest.

**Spec:** `apps/server/src/test/resources/features/engine/UC23-download-document.feature` (the source of truth) and `docs/uc23-download-document-design.md`

**Base branch:** `feature/UC17-approve-document`. It has `engine.procedure`, `FileStore`, `Requests`, `Decisions`, `ProcedureSeed`, the web `engine.ts`, `Flash` and `InboxPage`. Rebase onto `develop` once UC17 merges.

## How each task runs (TDD)

Each task names the scenario or scenarios it turns green. Each task follows the same cycle:
1. Take `@wip` off that scenario only, run it and watch it fail.
2. Write the unit, IT or Vitest test for the piece it needs, and watch that fail.
3. Write the least code that passes it.
4. Run the scenario green and commit.

Run one scenario with: `./mvnw -pl apps/server verify -DskipUTs -Dcucumber.filter.tags="@UC23 and not @wip"`. Scenarios tagged `@browser` also need `-Dcucumber.filter.tags="@UC23 and @browser and not @wip"` and the web build, as `junit-platform.properties` says.

## Global Constraints

- **Who gets the document, as the feature says:** the requester, and whoever closed the procedure (Joana). Everyone else is refused: Sofia, Rui and Carla, even though Rui and Carla approved earlier steps. A request without a session is asked to sign in. This is stricter than UC17's rule for opening the request, so `FinalDocuments` has its own check. Don't reuse `Requests.mustTakePart`.
- The UI copy is Portuguese and comes from the prototype: «Os meus pedidos», «Documento», «Fechar», «Transferir PDF», «Ver documento», «Procedimento fechado», «Fechado a dd/mm/aaaa».
- The file name is `ordem_reembolso_<reference>.pdf`, and the media type is `application/pdf`.
- The document is generated once, at closing. UC23 only reads it. Until UC22 exists, the Background stores the test fixture `src/test/resources/fixtures/ordem_reembolso_AC-2026-0098.pdf` (any valid one-page PDF).
- The two scenarios of «Regra: O documento mostra cada pessoa com o nome e o cargo» test UC22's generator. Mark them `@wip` and leave them out of this plan.
- Step definitions use `io.cucumber.java.pt` (`@Dado`, `@Quando`, `@Então`, `@E`). The step text starts after the keyword: `@Dado("que o Entra ID da organização tem estas contas")`.

## Review Focus

1. **The PDF doesn't render inside the dialog.** UC17 sends `Content-Security-Policy: sandbox` on files, and Chrome's PDF viewer won't load in a sandboxed document. The document endpoint doesn't send it, and the dialog shows a `blob:` URL. Task 9's step asserts that the iframe's document loaded (`frame.url()` starts with `blob:`, and the response was 200).
2. **The document is fetched twice** (dialog, then «Transferir PDF»). The dialog's Vitest test `fetchesOnce` checks one `fetch` per opening.
3. **The last approval is undone** («Desfazer», UC17), so the procedure is no longer closed but its document row remains. Task 3 adds `undoneClosingHasNoDocument`.
4. **The network fails while opening.** The dialog shows «Não foi possível abrir o documento». Task 9 adds `failureShowsNotice`.
5. **Two closings in a row** (the closer's notice, then the requester's list) would show the same document. The table's primary key on `procedure_id` refuses a second document. Task 1 covers it with `aProcedureHasOneDocument`.

---

### Task 1: The table and repository of generated documents

**Files:**
- Create: `apps/server/src/main/resources/db/migration/engine/V2026_11_27_1__engine_generated_document.sql`
- Create: `apps/server/src/main/java/io/github/defiro/engine/internal/GeneratedDocuments.java`
- Create: `apps/server/src/main/java/io/github/defiro/engine/internal/Digests.java` (the SHA-256 code moved out of `Files`)
- Test: `apps/server/src/test/java/io/github/defiro/engine/internal/GeneratedDocumentsIT.java`

**Interfaces:**
- Produces: `record GeneratedDocuments.Document(UUID procedure, String filename, String mediaType, String sha256)`, plus these methods:
  - `Document store(UUID procedure, String filename, String mediaType, byte[] content)`
  - `Optional<Document> of(UUID procedure)`
  - `byte[] content(Document document)`
- Produces: `static String Digests.sha256(byte[] content)`.

- [ ] **Step 1: Write the failing IT** (Testcontainers, as in `FilesIT`):
  - `storesTheDocumentAndItsBytes`: `store(p, "ordem_reembolso_AC-2026-0098.pdf", "application/pdf", bytes)`. Then `of(p)` has that filename and media type, `sha256 == Digests.sha256(bytes)`, and `content(...)` equals `bytes`.
  - `aProcedureHasOneDocument`: a second `store` for `p` throws `DuplicateKeyException`.
  - `noDocumentYet`: `of(other)` is empty.
- [ ] **Step 2: Run it.** `./mvnw -pl apps/server verify -Dit.test=GeneratedDocumentsIT`. Expected: it FAILS to compile.
- [ ] **Step 3: Write the migration.** It creates `engine.generated_document(procedure_id uuid primary key references engine.procedure, filename varchar(255) not null, media_type varchar(100) not null, storage_key varchar(255) not null unique, sha256 char(64) not null, generated_at timestamptz not null default now())`. Add a comment: UC22 writes it, and UC23 reads it.
- [ ] **Step 4: Implement `GeneratedDocuments`** (`@Repository`). The storage key is `"generated/" + procedure`. Move the SHA-256 code into `Digests`, and have `Files` use it.
- [ ] **Step 5: Run `GeneratedDocumentsIT` and `FilesIT`.** Expected: PASS.
- [ ] **Step 6: Commit.** `feat(UC23): the final document of a procedure, kept once`

### Task 2: The Background, and the requester gets the PDF

Scenario: «O documento é o PDF gerado quando o procedimento foi fechado».

**Files:**
- Create: `apps/server/src/test/java/io/github/defiro/acceptance/engine/DocumentoSteps.java`
- Create: `apps/server/src/test/resources/fixtures/ordem_reembolso_AC-2026-0098.pdf`
- Create: `apps/server/src/main/java/io/github/defiro/engine/internal/FinalDocuments.java`
- Modify: `apps/server/src/main/java/io/github/defiro/engine/internal/RequestController.java`
- Modify: `apps/server/src/test/java/io/github/defiro/engine/internal/ProcedureSeed.java` (add `closedAt`)
- Test: `apps/server/src/test/java/io/github/defiro/engine/internal/FinalDocumentsIT.java`

**Interfaces:**
- Consumes: Task 1, `ProcedureSeed.submit`, `Decisions.approve(UUID, Person)`, `FakeEntraId`, and the `{person}` parameter type.
- Produces: `void ProcedureSeed.closedAt(String reference, OffsetDateTime at)` (test only). It sets `closed_at`, and the closing audit entry's `occurred_at`.
- Produces: `record FinalDocuments.Opened(GeneratedDocuments.Document document, byte[] content)`.
- Produces: `Optional<Opened> FinalDocuments.open(String reference, Person person)`.
- Produces: `GET /api/engine/procedures/{reference}/document`. It answers 200 with `Content-Type: application/pdf`, `Content-Disposition: inline; filename="ordem_reembolso_AC-2026-0098.pdf"` and `Cache-Control: no-store`.
- Produces the steps:
  - `que o Entra ID da organização tem estas contas`, `o workflow {string} tem o percurso`, `o pedido {string} de {person} para {string} foi aprovado por {person} e {person}`, `{person} validou-o e fechou-o a {string} às {string}`, and `ao fechar foi gerada a ordem de reembolso {string}`. These delegate to the same seed and `FakeEntraId` calls as UC17's `RequestSteps`.
  - `{person} pede o documento de {string}`
  - `recebe {string} como {string}`
  - `é a ordem de reembolso gerada quando o procedimento foi fechado`, which compares the SHA-256 with the fixture's.

- [ ] **Step 1: Take `@wip` off the scenario and run it.** Expected: undefined steps.
- [ ] **Step 2: Write the Background steps and run them.** Expected: the Background passes, and «pede o documento» is undefined.
- [ ] **Step 3: Write the failing IT** `FinalDocumentsIT.theRequesterGetsIt`: on the seeded AC-2026-0098, `open("AC-2026-0098", ana)` gives the fixture's bytes.
- [ ] **Step 4: Run it.** Expected: it FAILS to compile.
- [ ] **Step 5: Implement `FinalDocuments.open`** for the requester only: `procedures.find(reference)`, `requester().equals(person.id())`, then `generatedDocuments.of(id)`. Implement the endpoint, with `ContentDisposition.inline().filename(name, UTF_8)` and no CSP `sandbox`.
- [ ] **Step 6: Write the scenario's last steps and run the IT and the scenario.** Expected: PASS.
- [ ] **Step 7: Commit.** `feat(UC23): the requester gets the document of a closed procedure`

### Task 3: A procedure that isn't closed has no document

Scenario: «Um procedimento que não está fechado não tem documento».

**Files:**
- Modify: `FinalDocuments.java`, `DocumentoSteps.java`
- Test: `FinalDocumentsIT.java`

**Interfaces:**
- Produces the steps: `que o pedido {string} de {person} está em {string}`, which seeds a submitted request waiting at that step, and `não há documento`, which expects 404.

- [ ] **Step 1: Take off `@wip` and run.** Expected: undefined steps.
- [ ] **Step 2: Write the failing IT cases.**
  - `notClosedHasNoDocument`: AC-2026-0147, at «Verificação financeira», is empty for Ana, even with a stored document row.
  - `undoneClosingHasNoDocument`: once Joana's approval of AC-2026-0098 is undone, it's empty for Ana.
- [ ] **Step 3: Run them.** Expected: FAIL. Today a stored row is enough.
- [ ] **Step 4: Require `status().equals("closed")`** in `FinalDocuments.open`.
- [ ] **Step 5: Write the steps, then run the IT and the scenario.** Expected: PASS.
- [ ] **Step 6: Commit.** `feat(UC23): no document until the procedure is closed`

### Task 4: Whoever closed the procedure gets it, and nobody else does

Scenarios: «Quem fechou o procedimento recebe o documento», and the outline «Mais ninguém recebe o documento, mesmo com o endereço» (Sofia, Rui, Carla).

**Files:**
- Modify: `FinalDocuments.java`, `DocumentoSteps.java`
- Test: `FinalDocumentsIT.java`

**Interfaces:**
- Consumes: `AuditLog.of(UUID)` and `Decisions.APPROVED`.
- Produces: `FinalDocuments.open` throws `NotAllowedException`, which answers 403, for anyone who isn't the requester or the closer. The closer is the actor of the procedure's newest `approved` entry while the status is `closed`.
- Produces the steps: `recebe {string}` and `é recusado`, which expects 403.

- [ ] **Step 1: Take off `@wip` from both, and run.** Expected: Joana gets an empty answer, and the outline gets a 404 for each row.
- [ ] **Step 2: Write the failing IT cases.**
  - `whoClosedItGetsIt`: Joana gets the bytes.
  - `earlierReviewersAreRefused`: Rui and Carla get `NotAllowedException`.
  - `someoneElseIsRefused`: Sofia gets `NotAllowedException`.
- [ ] **Step 3: Run them.** Expected: FAIL.
- [ ] **Step 4: Implement the check** in `FinalDocuments`: `requester || closer`, or else `throw new NotAllowedException()`. Check it before the status and document lookup, so an outsider can't learn whether the procedure is closed.
- [ ] **Step 5: Run the IT and both scenarios.** Expected: PASS.
- [ ] **Step 6: Commit.** `feat(UC23): only the requester and whoever closed it get the document`

### Task 5: Without a session, it asks to sign in

Scenario: «Sem iniciar sessão não se pede o documento».

**Files:**
- Modify: `DocumentoSteps.java`

**Interfaces:**
- Produces the steps: `alguém sem sessão iniciada pede o documento de {string}` and `é-lhe pedido que inicie sessão`. They expect the same answer `SecurityIT` expects for an API call with no session.

- [ ] **Step 1: Take off `@wip` and run.** Expected: undefined steps.
- [ ] **Step 2: Write the steps and run.** Expected: PASS. The existing security config already covers `/api/**`. If it FAILS, the endpoint is outside the protected paths. Fix it in `SecurityConfiguration`, not in the step.
- [ ] **Step 3: Commit.** `test(UC23): the document needs a session`

### Task 6: «Os meus pedidos» on the server

Prepares the two «Os meus pedidos» scenarios of Task 8.

**Files:**
- Create: `apps/server/src/main/java/io/github/defiro/engine/internal/MyRequestsController.java`
- Modify: `Procedures.java`
- Test: `apps/server/src/test/java/io/github/defiro/engine/internal/MyRequestsIT.java`

**Interfaces:**
- Produces: `record Procedures.Mine(String reference, String workflow, String status, String currentStep, OffsetDateTime closedAt, boolean hasDocument)`.
- Produces: `List<Mine> Procedures.of(String requester)`, newest first. `workflow` and `currentStep` are names, and `hasDocument` is `status = 'closed'` and a document exists.
- Produces: `GET /api/engine/my-requests` returns `List<Mine>` for the person signed in.

- [ ] **Step 1: Write the failing IT.**
  - `listsOnlyMine`: Ana has AC-2026-0098 and AC-2026-0147. Rui has neither.
  - `closedWithDocument`: AC-2026-0098 has `"closed"`, its `closedAt` equals 17/10/2026 15:30 Europe/Lisbon, and `hasDocument true`.
  - `inProgressHasNone`: AC-2026-0147 has `hasDocument false` and `currentStep "Verificação financeira"`.
- [ ] **Step 2: Run it.** Expected: it FAILS to compile.
- [ ] **Step 3: Implement `Procedures.of`** (one query, with a `left join` on `generated_document`) **and the controller.**
- [ ] **Step 4: Run it.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat(UC23): the requester's own requests, with their document`

### Task 7: The web API client

**Files:**
- Modify: `apps/web/src/api/engine.ts`
- Test: `apps/web/src/api/engine.test.ts`

**Interfaces:**
- Produces:
  - `type MyRequest = { reference: string; workflow: string; status: string; currentStep: string | null; closedAt: string | null; hasDocument: boolean }`
  - `listMyRequests(): Promise<MyRequest[]>`
  - `fetchDocument(reference: string): Promise<{ blob: Blob; filename: string }>`. The filename comes from `Content-Disposition`, with `ordem_reembolso_${reference}.pdf` if the header is missing. It throws `unexpected(...)` on anything but 200.

- [ ] **Step 1: Write the failing tests**, with `fetch` mocked as the existing tests do:
  - `listMyRequests` GETs `/api/engine/my-requests`.
  - `fetchDocument` GETs `/api/engine/procedures/AC-2026-0098/document` and returns the blob with `"ordem_reembolso_AC-2026-0098.pdf"`.
  - `fetchDocument` rejects on 403.
- [ ] **Step 2: Run them.** `npm --prefix apps/web test -- --run src/api/engine.test.ts`. Expected: FAIL.
- [ ] **Step 3: Implement both.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat(web): the client for my requests and their document (UC23)`

### Task 8: «Os meus pedidos» in the web app

Scenarios: «Um pedido fechado oferece o seu documento em «Os meus pedidos»», and «Um pedido que não está fechado não oferece documento».

**Files:**
- Create: `apps/web/src/pages/MyRequests.tsx`, `apps/web/src/pages/MyRequests.test.tsx`
- Modify: `apps/web/src/pages/InboxPage.tsx` (renders `<MyRequests onDocument={…} />` under the tasks)
- Create: `apps/server/src/test/java/io/github/defiro/acceptance/engine/DocumentoScreenSteps.java`

**Interfaces:**
- Consumes: `listMyRequests` (Task 7).
- Produces: `MyRequests({ onDocument }: { onDocument: (reference: string) => void })`. Each closed row's «Documento» button has the accessible name «Documento do pedido <ref>».
- Produces the steps: `{person} abre «Os meus pedidos»`, `o pedido {string} mostra {string}`, `oferece «Documento»` and `o pedido {string} não oferece «Documento»`.

- [ ] **Step 1: Take off `@wip` from both and run them** (`@browser`). Expected: undefined steps.
- [ ] **Step 2: Write the failing Vitest tests.**
  - `closedOffersDocumento`: the row shows «Fechado a 17/10/2026» and a «Documento do pedido AC-2026-0098» button.
  - `inProgressShowsItsStep`: the row shows «Verificação financeira» and no «Documento».
  - `documentoCallsOnDocument`: clicking it calls `onDocument` with `"AC-2026-0098"`.
- [ ] **Step 3: Run them.** Expected: FAIL.
- [ ] **Step 4: Implement `MyRequests`.** It has the heading «Os meus pedidos», a table with the reference, the workflow and the state, and «Documento» when `hasDocument` is true. Format the date with the `pt-PT` date, Europe/Lisbon, as `dateTime` does in `review/notices.ts`.
- [ ] **Step 5: Run Vitest, then write the browser steps and run both scenarios.** Expected: PASS.
- [ ] **Step 6: Commit.** `feat(web): «Os meus pedidos» offers a closed request's document (UC23)`

### Task 9: The document opens on screen

Scenario: «O documento abre no ecrã».

**Files:**
- Create: `apps/web/src/components/DocumentDialog.tsx`, `apps/web/src/components/DocumentDialog.test.tsx`
- Modify: `InboxPage.tsx` (holds the open reference and renders the dialog), `DocumentoScreenSteps.java`

**Interfaces:**
- Consumes: `fetchDocument` (Task 7).
- Produces: `DocumentDialog({ reference, onClose, onSaved }: { reference: string | null; onClose: () => void; onSaved: (filename: string) => void })`. It opens whenever `reference` isn't null.
- Produces: `documentFailed: Notice` = `{ tom: 'bad', titulo: 'Não foi possível abrir o documento', texto: 'Tente outra vez daqui a pouco.' }` in `review/notices.ts`.
- Produces the steps: `{person} abre o documento de {string} em «Os meus pedidos»`, `vê no ecrã o PDF {string}` and `vê «Fechar» e «Transferir PDF» no topo`.

- [ ] **Step 1: Take off `@wip` and run.** Expected: undefined steps.
- [ ] **Step 2: Write the failing Vitest tests.**
  - `showsThePdf`: an iframe titled «ordem_reembolso_AC-2026-0098.pdf» has a `src` starting with `blob:`.
  - `barHasBothButtons`: «Fechar» and «Transferir PDF» are buttons inside the dialog's first child.
  - `fetchesOnce`.
  - `failureShowsNotice`: on a rejected `fetchDocument`, the dialog shows `documentFailed.titulo` and no iframe.
- [ ] **Step 3: Run them.** Expected: FAIL.
- [ ] **Step 4: Port `DocumentDialog`** from `prototype/frontend:apps/web/src/components/DocumentDialog.tsx`. Keep its `<dialog>`, its focus handling and its sticky bar. Replace the preview `<section>` with `<iframe title={filename} src={blobUrl} className="h-[70vh] w-full rounded-md border border-line-strong" />`. Revoke the blob URL on close.
- [ ] **Step 5: Run Vitest, then write the browser steps and run the scenario.** «vê no ecrã o PDF» checks that the iframe's `title` and the 200 answer of `/document` (Review Focus 1). Expected: PASS.
- [ ] **Step 6: Commit.** `feat(web): the document opens on screen (UC23)`

### Task 10: The bar stays visible on any screen size

Scenario outline: «Fechar» e «Transferir PDF» ficam visíveis em qualquer tamanho de ecrã, at 360, 768 and 1440 pixels.

**Files:**
- Modify: `DocumentDialog.tsx` (only if it fails), `DocumentoScreenSteps.java`

**Interfaces:**
- Produces the steps: `que o ecrã de {person} tem {int} píxeis de largura` (`page.setViewportSize(width, 800)`), `ela abre o documento de {string} e desce até ao fim` (scrolls the dialog's box to `scrollHeight`), and `continua a ver «Fechar» e «Transferir PDF»` (both buttons' bounding boxes are within the viewport).

- [ ] **Step 1: Take off `@wip` and run.** Expected: undefined steps.
- [ ] **Step 2: Write the steps and run.** Expected: PASS, or FAIL at 360 if the dialog's `w-[680px]` overflows.
- [ ] **Step 3: If it fails at 360**, make the dialog `w-[min(680px,calc(100vw-32px))]` and the iframe `h-[60vh]`, then run again. Expected: PASS.
- [ ] **Step 4: Commit.** `feat(web): the document's bar stays visible on small screens (UC23)`

### Task 11: «Fechar» returns to where it was opened

Scenario: «Fechar» fecha o documento e volta ao sítio de onde foi aberto.

**Files:**
- Modify: `DocumentDialog.test.tsx`, `DocumentoScreenSteps.java`

**Interfaces:**
- Produces the steps: `que {person} abriu o documento de {string} em «Os meus pedidos»`, `escolhe «Fechar»` and `volta a «Os meus pedidos», em «Documento» de {string}` (the focused element's accessible name is «Documento do pedido <ref>»).

- [ ] **Step 1: Take off `@wip` and run.** Expected: undefined steps.
- [ ] **Step 2: Write the Vitest test `fecharReturnsFocus` and run it.** Expected: PASS, from the ported focus handling. If it FAILS, fix the dialog's `closed()`.
- [ ] **Step 3: Write the steps and run the scenario.** Expected: PASS.
- [ ] **Step 4: Commit.** `test(UC23): «Fechar» returns to «Documento»`

### Task 12: «Transferir PDF» saves the PDF and says so

Scenario: «Transferir PDF» guarda o PDF e di-lo.

**Files:**
- Modify: `DocumentDialog.tsx`, `DocumentDialog.test.tsx`, `InboxPage.tsx`, `review/notices.ts`, `DocumentoScreenSteps.java`

**Interfaces:**
- Produces: `savedNotice(filename: string): Notice` = `{ tom: 'ok', titulo: 'PDF guardado', texto: `${filename} foi guardado.` }`. The wording is my call, since the prototype has none.
- Produces the steps: `que {person} abriu o documento de {string}`, `escolhe «Transferir PDF»`, `{string} é transferido` (`page.waitForDownload`, and `suggestedFilename` equals the name), and `é-lhe dito que foi guardado` (the Flash shows «PDF guardado»).

- [ ] **Step 1: Take off `@wip` and run.** Expected: it FAILS, with no download.
- [ ] **Step 2: Write the failing Vitest test `transferirSaves`.** Clicking it creates an `<a download="ordem_reembolso_AC-2026-0098.pdf">` with the same blob URL and calls `onSaved` with that name, with no second `fetch`.
- [ ] **Step 3: Run it.** Expected: FAIL.
- [ ] **Step 4: Implement it.** `InboxPage` passes `onSaved={(f) => setNotice(savedNotice(f))}`.
- [ ] **Step 5: Run Vitest and the scenario.** Expected: PASS.
- [ ] **Step 6: Commit.** `feat(web): «Transferir PDF» saves the document and says so (UC23)`

### Task 13: A device that can't show the PDF downloads it

Scenario: «Um dispositivo que não mostra o PDF transfere-o».

**Files:**
- Modify: `DocumentDialog.tsx`, `DocumentDialog.test.tsx`, `DocumentoScreenSteps.java`

**Interfaces:**
- Produces the step: `que o navegador de {person} não consegue mostrar um PDF no ecrã`, which runs `page.addInitScript("Object.defineProperty(navigator,'pdfViewerEnabled',{get:()=>false})")` before signing in.

- [ ] **Step 1: Take off `@wip` and run.** Expected: it FAILS, because the dialog opens.
- [ ] **Step 2: Write the failing Vitest test `noViewerDownloadsAtOnce`.** With `pdfViewerEnabled` false, `showModal` isn't called, there's no iframe, the download link is clicked, and `onSaved` is called.
- [ ] **Step 3: Run it.** Expected: FAIL.
- [ ] **Step 4: Implement the branch** on `navigator.pdfViewerEnabled === false`, then call `onClose()`.
- [ ] **Step 5: Run Vitest and the scenario.** Expected: PASS.
- [ ] **Step 6: Commit.** `feat(web): download straight away where the PDF cannot be shown (UC23)`

### Task 14: The validator opens the document from the closing notice

Scenario: «Quem valida abre o documento a partir do aviso de fecho».

**Files:**
- Modify: `apps/web/src/components/Flash.tsx`, `Flash.test.tsx`, `review/notices.ts`, `pages/RequestPage.tsx`, `pages/InboxPage.tsx`, `DocumentoScreenSteps.java`

**Interfaces:**
- Produces:
  - `Flash`'s optional `onDoc?: () => void`, which shows «Ver documento» and keeps the notice until it's closed, as `onUndo` does.
  - `ReviewState.document?: string` (a reference).
  - `closedNotice(reference: string, requester: string): Notice` = `{ tom: 'ok', titulo: 'Procedimento fechado', texto: `${reference}: foi gerado o documento final. ${requester} já o pode transferir.` }`. `requester` is «Nome (Cargo)», as the prototype's `withRole` does.
- Produces the steps: `{person} o valida e fecha` (from the browser, as UC17's `ReviewScreenSteps` approves), `vê {string} com «Ver documento»` and `«Ver documento» abre o documento de {string}`.

- [ ] **Step 1: Take off `@wip` and run.** Expected: undefined steps.
- [ ] **Step 2: Write the failing Vitest tests.**
  - `Flash.verDocumento`: with `onDoc`, it shows «Ver documento», clicking it calls `onDoc`, and the notice doesn't fade.
  - `review.test.tsx` `lastApprovalShowsClosedNotice`: approving with `nextStep: null` navigates to the inbox with `closedNotice` and `document: "AC-2026-0147"`.
- [ ] **Step 3: Run them.** Expected: FAIL.
- [ ] **Step 4: Implement it.** In the browser scenario, the Background stores the document when Joana closes. Until UC22 exists, the step stores the fixture right after the approval.
- [ ] **Step 5: Run Vitest and the scenario.** Expected: PASS.
- [ ] **Step 6: Commit.** `feat(web): «Ver documento» in the closing notice (UC23)`

### Task 15: The feature with no `@wip` left but UC22's

**Files:**
- Modify: `UC23-download-document.feature`

- [ ] **Step 1: Check the tags.** Remove `@wip` from the feature line. Only the two criterion-4 scenarios keep `@wip`, with a comment: `# @wip até a UC22 gerar o PDF com estes dados.`
- [ ] **Step 2: Run the whole feature, with and without `@browser`.** Expected: every scenario except those two PASSES.
- [ ] **Step 3: Run the server and web suites once.** `./mvnw verify` and `npm --prefix apps/web test -- --run`. Expected: PASS.
- [ ] **Step 4: Commit.** `test(UC23): the scenarios run, but the two of UC22`

---

## Left to the builder

- The markup of the «Os meus pedidos» table, as long as the accessible names above hold.
- Whether `MyRequests` loads alongside `listInbox` or after it.
