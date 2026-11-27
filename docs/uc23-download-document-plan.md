# UC23 · Transferir um documento gerado: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The requester, and the people who took part in a closed procedure, can open its final PDF on screen and save it.

**Architecture:** The final document gets its own Engine table, `engine.generated_document`, with one row per procedure. Its bytes go in the existing `FileStore`. A `FinalDocuments` service checks participation, using the same rule as the request (extracted from `Requests`), and serves the stored bytes at `GET /api/engine/procedures/{reference}/document`. The web app fetches the PDF as a blob. It shows the blob in an `<iframe>` inside `DocumentDialog`, or downloads it at once when `navigator.pdfViewerEnabled` is false. A minimal «Os meus pedidos» and the closing notice open the dialog.

**Tech Stack:** Spring Boot, JdbcClient, Flyway (Postgres), Cucumber with Portuguese keywords (`io.cucumber.java.pt`), Playwright (`WebBrowser`), React, Vitest.

**Spec:** `docs/uc23-download-document-design.md` and `apps/server/src/test/resources/features/engine/UC23-download-document.feature`

**Base branch:** `feature/UC17-approve-document`. It has `engine.procedure`, `FileStore`, `Requests`, `Decisions`, `ProcedureSeed`, the web `engine.ts`, `Flash` and `InboxPage`. Rebase onto `develop` once UC17 merges. If UC13's tables (#65) land first, `Procedures.find` and the participation rule move with them. This plan's own table only references `engine.procedure(id)`.

## Global Constraints

- The UI copy is Portuguese and comes from the prototype: «Os meus pedidos», «Documento», «Fechar», «Transferir PDF», «Ver documento», «Procedimento fechado», «Fechado a dd/mm/aaaa».
- The file name is `ordem_reembolso_<reference>.pdf`.
- The document is generated once, at closing, and never again. UC23 only reads it.
- `@wip` comes off each scenario as soon as its steps pass. The two criterion-4 scenarios (name and job title in the PDF) stay `@wip`, because they check UC22's generator.
- Step definitions for this feature use `io.cucumber.java.pt` (`@Dado`, `@Quando`, `@Então`, `@E`). Reuse the existing `{person}` parameter type.
- **Participants (pending benny's choice in the thread):** the plan uses the UC17 rule: the requester, anyone with an entry in the audit log, or a candidate of an open task. If benny keeps the draft instead (requester and whoever closed it), Task 2's `Participants.mayOpenDocument` checks `requester || closer`, and the Rui/Carla rows stay «é recusado».

## Review Focus

1. **The PDF doesn't render inside the dialog** because of the response headers. UC17 sends `Content-Security-Policy: sandbox`, and Chrome's PDF viewer won't load in a sandboxed document. Serving a blob URL avoids this. Task 6's Playwright check «vê no ecrã o PDF» asserts that the iframe's PDF loaded, not just that the iframe exists.
2. **The same document is requested twice** (dialog, then «Transferir PDF»). It must be one fetch. The blob is reused for the `<a download>`. Task 5 test: `fetch` is called once per opening.
3. **Closing a procedure whose last approval is undone** (UC17 «Desfazer») leaves a document for a procedure that is no longer closed. `FinalDocuments.open` requires `status = 'closed'`, not just a row. Task 2 test: `undoneClosingHasNoDocument`.
4. **A reference with characters that need encoding** in the URL, and the `Content-Disposition` file name. Task 3 test: the header is `inline; filename="ordem_reembolso_AC-2026-0098.pdf"`.
5. **The network fails while opening.** The dialog shows the error notice «Não foi possível abrir o documento · Tente outra vez daqui a pouco.», not an empty frame. Task 5 test.

---

### Task 1: The table and repository of generated documents

**Files:**
- Create: `apps/server/src/main/resources/db/migration/engine/V2026_11_27_1__engine_generated_document.sql`
- Create: `apps/server/src/main/java/io/github/defiro/engine/internal/GeneratedDocuments.java`
- Test: `apps/server/src/test/java/io/github/defiro/engine/internal/GeneratedDocumentsIT.java`

**Interfaces:**
- Produces: `record GeneratedDocuments.Document(UUID procedure, String filename, String mediaType, String sha256, OffsetDateTime generatedAt)`, plus these methods:
  - `Document store(UUID procedure, String filename, String mediaType, byte[] content)`
  - `Optional<Document> of(UUID procedure)`
  - `byte[] content(Document document)`

- [ ] **Step 1: Write the failing IT**, `GeneratedDocumentsIT` (Testcontainers, as in `FilesIT`):
  - `storesTheDocumentAndItsBytes`: store `"ordem_reembolso_AC-2026-0098.pdf"`, `"application/pdf"` and `%PDF-1.7 test` bytes. Then `of(procedure)` has that filename, its SHA-256 and the media type, and `content(...)` equals the bytes.
  - `aProcedureHasOneDocument`: a second `store` for the same procedure throws `DuplicateKeyException`.
  - `noDocumentBeforeClosing`: `of(otherProcedure)` is empty.
- [ ] **Step 2: Run it.** `./mvnw -pl apps/server verify -Dit.test=GeneratedDocumentsIT`. Expected: it FAILS to compile.
- [ ] **Step 3: Write the migration.** It creates `engine.generated_document(procedure_id uuid primary key references engine.procedure, filename varchar(255) not null, media_type varchar(100) not null, storage_key varchar(255) not null unique, sha256 char(64) not null, generated_at timestamptz not null default now())`. Add a header comment: UC22 writes it, and UC23 reads it.
- [ ] **Step 4: Implement `GeneratedDocuments`** (`@Repository`, as `Files`). The storage key is `"generated/" + procedure`. Move the SHA-256 code from `Files` into a package-private `Digests.sha256(byte[])`, and have both classes use it.
- [ ] **Step 5: Run `GeneratedDocumentsIT` and `FilesIT`.** Expected: PASS.
- [ ] **Step 6: Commit.** `feat(UC23): the final document of a procedure, kept once`

### Task 2: Who may open the document, and opening it

**Files:**
- Create: `apps/server/src/main/java/io/github/defiro/engine/internal/Participants.java`
- Create: `apps/server/src/main/java/io/github/defiro/engine/internal/FinalDocuments.java`
- Modify: `apps/server/src/main/java/io/github/defiro/engine/internal/Requests.java` (`mustTakePart` moves to `Participants`)
- Test: `apps/server/src/test/java/io/github/defiro/engine/internal/FinalDocumentsIT.java`

**Interfaces:**
- Consumes: `GeneratedDocuments` (Task 1), `Procedures.find(String)`, `ProcedureSeed`, `Decisions.approve(UUID, Person)`.
- Produces: `Participants.mustTakePart(Procedure, RequestData, WorkflowSpecification, List<AuditLog.Entry>, Person) -> Optional<Tasks.OpenTask>`. It throws `NotAllowedException`, as today.
- Produces: `record FinalDocuments.Opened(GeneratedDocuments.Document document, byte[] content)`.
- Produces: `Optional<Opened> FinalDocuments.open(String reference, Person person)`. It's empty for an unknown reference, for a procedure that isn't closed, or when there's no document. It throws `NotAllowedException` for someone who doesn't take part.

- [ ] **Step 1: Write the failing IT**, `FinalDocumentsIT`. Seed AC-2026-0098 with `ProcedureSeed`: Rui, Carla and Joana approve through `Decisions.approve`, then `GeneratedDocuments.store`. The tests:
  - `theRequesterGetsIt`: Ana gets the stored bytes.
  - `whoClosedItGetsIt`: Joana gets the stored bytes.
  - `earlierReviewersGetIt`: Rui and Carla get it. Under the alternative rule, they're refused.
  - `someoneElseIsRefused`: Sofia gets `NotAllowedException`.
  - `notClosedHasNoDocument`: AC-2026-0147, waiting at «Verificação financeira», is empty for Ana.
  - `undoneClosingHasNoDocument`: Joana's approval is undone, so it's empty for Ana.
- [ ] **Step 2: Run it.** Expected: it FAILS to compile.
- [ ] **Step 3: Extract `Participants`** (`@Component`) from `Requests.mustTakePart`, with the same body. `Requests` delegates to it.
- [ ] **Step 4: Implement `FinalDocuments.open`.** Find the procedure, call `participants.mustTakePart`, require `status().equals("closed")`, then `generatedDocuments.of(id)`.
- [ ] **Step 5: Run `FinalDocumentsIT`, `RequestsIT` and `RequestFilesIT`.** Expected: PASS. UC17's behaviour is unchanged.
- [ ] **Step 6: Commit.** `feat(UC23): only the participants of a closed procedure open its document`

### Task 3: The document's endpoint

**Files:**
- Modify: `apps/server/src/main/java/io/github/defiro/engine/internal/RequestController.java`
- Test: `apps/server/src/test/java/io/github/defiro/engine/internal/FinalDocumentsIT.java` (MockMvc cases)

**Interfaces:**
- Consumes: `FinalDocuments.open` (Task 2).
- Produces: `GET /api/engine/procedures/{reference}/document`. It answers 200 with `application/pdf`, `Content-Disposition: inline; filename="ordem_reembolso_<ref>.pdf"` and `Cache-Control: no-store`. It answers 404 when there's no document, 403 to someone who doesn't take part, and 401 without a session.

- [ ] **Step 1: Write the failing MockMvc tests**, signed in as in `SecurityIT`:
  - `servesThePdf`: checks the status, the content type, the exact `Content-Disposition` above, and that the body equals the bytes.
  - `notClosedIs404`.
  - `outsiderIs403`.
  - `withoutSessionIs401`.
- [ ] **Step 2: Run them.** Expected: they FAIL with 404 on every case.
- [ ] **Step 3: Add `ResponseEntity<byte[]> document(@PathVariable String reference, @AuthenticationPrincipal ClaimAccessor user)`** to `RequestController`. Build the disposition with `ContentDisposition.inline().filename(name, UTF_8)`. Don't send CSP `sandbox` here: the web app shows a blob, and the file is ours.
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat(UC23): GET a closed procedure's document`

### Task 4: «Os meus pedidos» on the server

**Files:**
- Create: `apps/server/src/main/java/io/github/defiro/engine/internal/MyRequestsController.java`
- Modify: `apps/server/src/main/java/io/github/defiro/engine/internal/Procedures.java`
- Test: `apps/server/src/test/java/io/github/defiro/engine/internal/MyRequestsIT.java`

**Interfaces:**
- Produces: `record Procedures.Mine(String reference, String workflow, String status, String currentStep, OffsetDateTime closedAt, boolean hasDocument)`.
- Produces: `List<Mine> Procedures.of(String requester)`, newest first. `workflow` is the specification's name, `currentStep` is the step's name or null, and `hasDocument` is `status = 'closed' and a generated_document exists`.
- Produces: `GET /api/engine/my-requests` returns `List<Mine>` for the person signed in.

- [ ] **Step 1: Write the failing IT.**
  - `listsOnlyMine`: Ana sees AC-2026-0098 and AC-2026-0147, and Rui sees none of them.
  - `closedWithDocument`: AC-2026-0098 has `status "closed"`, its `closedAt` set by the seed to 17/10/2026 15:30 Lisbon time, and `hasDocument true`.
  - `inProgressHasNone`: AC-2026-0147 has `hasDocument false` and `currentStep "Verificação financeira"`.
- [ ] **Step 2: Run it.** Expected: it FAILS.
- [ ] **Step 3: Implement `Procedures.of` and the controller.** One SQL query, joining `specification_version` for the names and doing a `left join` on `generated_document`.
- [ ] **Step 4: Add `ProcedureSeed.closedAt(String reference, OffsetDateTime at)`** (test only). The Background needs it for «a 17/10/2026 às 15:30».
- [ ] **Step 5: Run it.** Expected: PASS.
- [ ] **Step 6: Commit.** `feat(UC23): the requester's own requests, with their document`

### Task 5: The web app, the dialog, «Os meus pedidos» and the closing notice

**Files:**
- Modify: `apps/web/src/api/engine.ts`
- Create: `apps/web/src/components/DocumentDialog.tsx`, `apps/web/src/components/DocumentDialog.test.tsx`
- Create: `apps/web/src/pages/MyRequests.tsx` (a section rendered by `InboxPage`)
- Modify: `apps/web/src/pages/InboxPage.tsx`, `apps/web/src/components/Flash.tsx`, `apps/web/src/review/notices.ts`, `apps/web/src/pages/RequestPage.tsx` (navigation state after the last approval)
- Test: `apps/web/src/pages/myRequests.test.tsx`, `apps/web/src/components/Flash.test.tsx`

**Interfaces:**
- Consumes: Tasks 3 and 4.
- Produces in `engine.ts`:
  - `type MyRequest = { reference: string; workflow: string; status: string; currentStep: string | null; closedAt: string | null; hasDocument: boolean }`
  - `listMyRequests(): Promise<MyRequest[]>`
  - `fetchDocument(reference: string): Promise<{ blob: Blob; filename: string }>`. The filename comes from `Content-Disposition`.
- Produces: `DocumentDialog({ reference, open, onClose, onSaved }: { reference: string | null; open: boolean; onClose: () => void; onSaved: (filename: string) => void })`
- Produces: `Flash`'s new optional `onDoc?: () => void`, which shows «Ver documento». `ReviewState` gets `document?: string` (a reference).
- Produces: `closedNotice(reference: string, requester: string): Notice` = `{ tom: 'ok', titulo: 'Procedimento fechado', texto: `${reference}: foi gerado o documento final. ${requester} já o pode transferir.` }`
- Produces: `savedNotice(filename: string): Notice` = `{ tom: 'ok', titulo: 'PDF guardado', texto: `${filename} foi guardado.` }` (my call on the copy: the prototype has none)
- Produces: `documentFailed: Notice` = `{ tom: 'bad', titulo: 'Não foi possível abrir o documento', texto: 'Tente outra vez daqui a pouco.' }`

- [ ] **Step 1: Write the failing Vitest tests.**
  - DocumentDialog:
    - `showsThePdf`: with `pdfViewerEnabled` true, an iframe titled «ordem_reembolso_AC-2026-0098.pdf» has `src` starting with `blob:`, and «Fechar» and «Transferir PDF» are buttons.
    - `fetchesOnce`: opening and then choosing «Transferir PDF» calls `fetch` once.
    - `transferirSaves`: clicking it creates an `<a download="ordem_reembolso_AC-2026-0098.pdf">` and calls `onSaved` with that name.
    - `noViewerDownloadsAtOnce`: with `pdfViewerEnabled` false, there's no iframe, the download happens and `onSaved` is called.
    - `fecharReturnsFocus`: focus goes back to the button that opened it.
    - `failureShowsNotice`: on a 500, it shows `documentFailed`.
  - myRequests:
    - `closedOffersDocumento`: a closed row shows «Fechado a 17/10/2026» and a «Documento» button whose accessible name is «Documento do pedido AC-2026-0098».
    - `inProgressHasNoDocumento`: an in-progress row shows its step and no «Documento».
  - Flash:
    - `verDocumento`: with `onDoc`, it shows «Ver documento» and stays until closed.
- [ ] **Step 2: Run them.** `npm --prefix apps/web test -- --run`. Expected: they FAIL.
- [ ] **Step 3: Port `DocumentDialog`** from `prototype/frontend:apps/web/src/components/DocumentDialog.tsx`. Keep the same sticky bar, classes and `<dialog>` focus handling. Replace its preview `<section>` with `<iframe title={filename} src={blobUrl} className="h-[70vh] w-full rounded-md border border-line-strong">`. Revoke the URL on close. When `navigator.pdfViewerEnabled === false`, skip `showModal()`, download and call `onSaved`.
- [ ] **Step 4: Add `MyRequests`** under the inbox's table, with the heading «Os meus pedidos». It's a table with the reference, the workflow, the state («Fechado a dd/mm/aaaa», or the current step) and «Documento» for `hasDocument`.
- [ ] **Step 5: Wire the closing notice.** In `RequestPage`, an approval with `nextStep === null` navigates to the inbox with `{ notice: closedNotice(reference, requesterName), document: reference }`. `InboxPage` passes `onDoc` to `Flash` when `document` is set. `onSaved` shows `savedNotice`.
- [ ] **Step 6: Run the web tests and lint.** `npm --prefix apps/web test -- --run && npm --prefix apps/web run lint`. Expected: PASS.
- [ ] **Step 7: Commit.** `feat(web): open and save a closed request's document (UC23)`

### Task 6: The scenarios' steps, in Portuguese

**Files:**
- Create: `apps/server/src/test/java/io/github/defiro/acceptance/engine/DocumentoSteps.java` (API scenarios)
- Create: `apps/server/src/test/java/io/github/defiro/acceptance/engine/DocumentoScreenSteps.java` (`@browser`)
- Modify: `apps/server/src/test/resources/features/engine/UC23-download-document.feature`. Move `@wip` from the feature onto the two criterion-4 scenarios. If benny picks the UC17 rule, change the Rui and Carla rows to a «recebe» outline.
- Modify: `apps/server/src/test/java/io/github/defiro/acceptance/engine/RequestSteps.java`, only if the Background's English steps can't be reused. The Portuguese Background needs its own `@Dado` annotations, which delegate to the same seed methods.

**Interfaces:**
- Consumes: `ProcedureSeed`, `ProcedureSeed.closedAt` (Task 4), `GeneratedDocuments.store` (Task 1, the UC22 stand-in, with a fixed `%PDF` fixture from `src/test/resources/fixtures/ordem_reembolso_AC-2026-0098.pdf`), `WebBrowser`, and `FakeEntraId` as in `RequestSteps`.

- [ ] **Step 1: Run the feature with `@wip` removed** (`-Dcucumber.filter.tags="@UC23 and not @wip"`, then with `@browser`). Expected: undefined steps.
- [ ] **Step 2: Write the Background and API steps** in `DocumentoSteps`. «pede o documento de {string}» calls the endpoint as that person and keeps the response. «recebe {string} como {string}» checks the file name and the content type. «é a ordem de reembolso gerada…» checks that the SHA-256 equals the fixture's. «é recusado» checks 403. «não há documento» checks 404. «é-lhe pedido que inicie sessão» checks 401.
- [ ] **Step 3: Run the API scenarios.** Expected: PASS.
- [ ] **Step 4: Write the browser steps** in `DocumentoScreenSteps`. For «o navegador … não consegue mostrar um PDF», use `page.addInitScript("Object.defineProperty(navigator,'pdfViewerEnabled',{get:()=>false})")`. Use `page.waitForDownload` for «é transferido». Use `page.setViewportSize(width, 800)` for the widths. «continua a ver» checks that both buttons' bounding boxes are inside the viewport after scrolling the dialog to its end.
- [ ] **Step 5: Run the `@UC23 and @browser` scenarios.** Expected: PASS, except the two `@wip` scenarios.
- [ ] **Step 6: Commit.** `test(UC23): the steps of the scenarios`

---

## Left to the builder

- The markup of the «Os meus pedidos» table, as long as the accessible names above hold.
- Whether `MyRequests` loads alongside `listInbox` or after it.
