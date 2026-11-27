# UC23 · Download a generated document: design

**Status:** agreed with benny on 2026-11-27, for team review. The scenarios are in
[`UC23-download-document.feature`](../apps/server/src/test/resources/features/engine/UC23-download-document.feature).

## Intent

Once a procedure is closed, its final document (for «Ajudas de custo», the «Ordem de reembolso») can be
opened on screen and saved as a PDF, so that Finance can use it outside defiro.

## Who can open it

As the web prototype supports it, and no more:

- **The requester**, from «Os meus pedidos». The request shows «Documento» once it is «Fechado».
- **The HR validator who closes the procedure**, from «Ver documento» in the closing notice
  («Procedimento fechado · … já o pode transferir.»).
- **Anyone else is refused**, even with the document's address. That includes the reviewers of the earlier
  steps and the Workflow Manager.

The Engine checks this on every request for the document (sequence diagram, «5 · Download»: *Check the
user takes part in the procedure*).

## What it shows

The PDF that UC22 generates and stores in the File store when it closes the procedure. UC23 does not
generate anything. It reads the stored file. So names and job titles stay as they were on the day the
procedure was closed (criterion 4), even if someone later changes unit or job title.

## The screen

- A dialog with «Fechar» and «Transferir PDF» in a bar fixed at the top. The bar stays visible on any
  screen size and while scrolling (criterion 2).
- Below the bar, the **PDF itself**, previewed by the browser. The prototype's preview, built from the
  request's data, only stood in for the PDF.
- **Where the device cannot show a PDF on screen, there is no preview: the PDF downloads straight away**,
  with the same confirmation as «Transferir PDF».
- «Transferir PDF» saves `ordem_reembolso_<reference>.pdf` and confirms on screen that it was saved
  (criterion 3).
- «Fechar» closes the dialog and returns focus to the button that opened it, as the prototype's
  `DocumentDialog` does.

## Approaches considered

1. **A preview drawn from the document's rows, plus the stored PDF.** It looks like the prototype and works
   on every device, but there are two renderings of one document to keep matching. *Not chosen:* the
   prototype's preview was only standing in for the PDF.
2. **The stored PDF, previewed by the browser, with a straight download where it can't be shown.**
   **Chosen.** There's one source, the file Finance gets. The fallback covers devices that don't show
   embedded PDFs.

## Depends on

- **UC22** closes the procedure and stores the PDF. Until then, a test step stores it.
- **UC13 / UC17**: the procedure and its participants come from the Engine's tables, still being settled
  between `feature/UC13-step-contracts` and `feature/UC17-approve-document`
  (see `docs/uc13-engine-handoff.md` on the UC13 branch).
- **UC15**: the File store, behind the `FileStore` interface of the UC17 branch.

## Left to the builder

- The endpoint's path and how the web app detects that the browser cannot show a PDF.
- How «Os meus pedidos» knows a request has a document (for example, a flag in the list's response).

## Open questions for the team

1. Reviewers (Rui, Carla) and Finance have no way back to a closed request in the prototype. Does Finance
   need one, for example a list of the requests they took part in? It would widen who can open the
   document. Not part of UC23.
2. Should someone who takes no part get 404, the same as an unknown reference, rather than 403? The same
   question is open in UC17's draft.
