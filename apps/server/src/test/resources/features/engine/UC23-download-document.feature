# Draft for team review: the examples come from the acceptance criteria of UC23, the sequence
# diagram (docs/sequence_diagram.txt, «5 · Download») and the web prototype (request AC-2026-0098,
# Ana's closed request, and DocumentDialog). Design note: docs/uc23-download-document-design.md.
# No Example Mapping was run yet.
#
# Stand-ins, until their owners build them:
#   - UC13: the closed procedure is created by a test step, not by the request form.
#   - UC22: the reimbursement order is stored by a test step, as UC22 would store it at closing.
#
# As decided with benny (2026-11-27), only what the prototype supports:
#   - the requester opens the document from «Os meus pedidos»;
#   - the HR validator who closes the procedure opens it from the closing notice;
#   - anyone else is refused.
# open question (team): reviewers (Rui, Carla) and Finance have no way back to a closed request in
#                the prototype. Does Finance need one, for example a list of the requests they took
#                part in? Not part of UC23.
# @wip until the team has reviewed this draft, UC22 stores the document and the steps exist.
@UC23 @wip
Feature: Download a generated document
  As a Workflow Participant,
  I want to see and download the final document of a closed request as a PDF,
  so that Finance can use it outside defiro.

  # People: Ana Ferreira, Rui Matos, Carla Dias and Joana Pires are the prototype's personas, as they are.
  # The prototype gives its dates relative to the day it is opened (closed 41 days before, at 15:30).
  #   They are fixed here from 27/11/2026, the day this draft was written.
  Background:
    Given the organization's Entra ID has these accounts
      | name          | unit                        | job title                     | manager      | groups                                                       |
      | Ana Ferreira  | Divisão de Obras Municipais | Técnica Superior              | Rui Matos    | Todos os trabalhadores, Divisão de Obras Municipais          |
      | Rui Matos     | Divisão de Obras Municipais | Chefe de Divisão              | Helena Costa | Todos os trabalhadores, Chefias, Divisão de Obras Municipais |
      | Carla Dias    | Divisão Financeira          | Técnica da Divisão Financeira | Rui Matos    | Todos os trabalhadores, Divisão Financeira                   |
      | Joana Pires   | Recursos Humanos            | Técnica de Recursos Humanos   | Rui Matos    | Todos os trabalhadores, Recursos Humanos, RH · Formação      |
      | Sofia Almeida | Modernização Administrativa | Gestora de workflows          | Rui Matos    | Todos os trabalhadores                                       |
    And the workflow "Ajudas de custo" has the path
      | step                   | handled by                       |
      | Pedido                 | Todos os trabalhadores           |
      | Validação da chefia    | Chefias da unidade do requerente |
      | Verificação financeira | Divisão Financeira               |
      | Validação final        | RH · Formação                    |
    And Ana Ferreira's request "AC-2026-0098" for "Ajudas de custo" was approved by Rui Matos and Carla Dias
    And Joana Pires validated and closed it on 17/10/2026 at 15:30
    And the reimbursement order "ordem_reembolso_AC-2026-0098.pdf" was generated when it was closed

  Rule: Once a procedure is closed, the requester opens its final document from «Os meus pedidos»

    @browser
    Scenario: A closed request offers its document in «Os meus pedidos»
      When Ana Ferreira opens «Os meus pedidos»
      Then the request "AC-2026-0098" shows "Fechado a 17/10/2026"
      And it offers «Documento»

    @browser
    Scenario: A request that is not closed offers no document
      Given Ana Ferreira's request "AC-2026-0147" is in «Verificação financeira»
      When Ana Ferreira opens «Os meus pedidos»
      Then the request "AC-2026-0147" does not offer «Documento»

    Scenario: A procedure that is not closed has no document
      Given Ana Ferreira's request "AC-2026-0147" is in «Verificação financeira»
      When Ana Ferreira asks for the document of "AC-2026-0147"
      Then there is no document

    # The prototype's closing notice: «Procedimento fechado · AC-2026-0098: foi gerado o documento
    #   final. Ana Ferreira (Técnica Superior) já o pode transferir.» with «Ver documento».
    @browser
    Scenario: The validator opens the document from the closing notice
      Given Ana Ferreira's request "AC-2026-0147" is in «Validação final»
      When Joana Pires validates and closes it
      Then she sees "Procedimento fechado" with «Ver documento»
      And «Ver documento» opens the document of "AC-2026-0147"

  Rule: The document opens on screen, with «Fechar» and «Transferir PDF» always visible at the top
    # The screen shows the PDF itself, as stored when the procedure was closed: the prototype's
    #   preview only stood in for it. Where the device cannot show a PDF on screen, there is no
    #   preview and the PDF downloads straight away (decided with benny, 2026-11-27).

    @browser
    Scenario: The document opens on screen
      When Ana Ferreira opens the document of "AC-2026-0098" from «Os meus pedidos»
      Then she sees the PDF "ordem_reembolso_AC-2026-0098.pdf" on screen
      And she sees «Fechar» and «Transferir PDF» at the top

    @browser
    Scenario Outline: «Fechar» and «Transferir PDF» stay visible on any screen size
      Given Ana Ferreira's screen is <width> pixels wide
      When she opens the document of "AC-2026-0098" and scrolls to its end
      Then she still sees «Fechar» and «Transferir PDF»

      Examples:
        | width |
        | 360   |
        | 768   |
        | 1440  |

    @browser
    Scenario: «Fechar» closes the document and returns to where it was opened
      Given Ana Ferreira opened the document of "AC-2026-0098" from «Os meus pedidos»
      When she chooses «Fechar»
      Then she is back in «Os meus pedidos», on «Documento» of "AC-2026-0098"

    @browser
    Scenario: A device that cannot show the PDF downloads it instead
      Given Ana Ferreira's browser cannot show a PDF on screen
      When she opens the document of "AC-2026-0098" from «Os meus pedidos»
      Then "ordem_reembolso_AC-2026-0098.pdf" is downloaded
      And she is told it was saved

  Rule: «Transferir PDF» downloads the document as a PDF and confirms on screen that it was saved

    Scenario: The document is the PDF generated when the procedure was closed
      When Ana Ferreira asks for the document of "AC-2026-0098"
      Then she gets "ordem_reembolso_AC-2026-0098.pdf" as "application/pdf"
      And it is the reimbursement order generated when the procedure was closed

    @browser
    Scenario: «Transferir PDF» saves the PDF and says so
      Given Ana Ferreira opened the document of "AC-2026-0098"
      When she chooses «Transferir PDF»
      Then "ordem_reembolso_AC-2026-0098.pdf" is downloaded
      And she is told it was saved

  Rule: The document shows each person with their name and job title
    # As they were when the procedure was closed: the PDF is generated once and never again.

    Scenario: Each person in the document has their name and job title
      When Ana Ferreira gets the document of "AC-2026-0098"
      Then it shows "Requerente" as "Ana Ferreira (Técnica Superior)"
      And it shows "Validado por" as "Joana Pires (Técnica de Recursos Humanos) a 17/10/2026"

    Scenario: A later change of job title does not change the document
      Given Ana Ferreira's job title is now "Técnica Superior Especialista"
      When Ana Ferreira gets the document of "AC-2026-0098"
      Then it shows "Requerente" as "Ana Ferreira (Técnica Superior)"

  Rule: Only participants of the procedure can open its document
    # Participants, for the MVP, as the prototype supports: the requester and whoever closed the
    #   procedure. The reviewers of the earlier steps took part but have no screen back to it.
    # open question (team): the API answers 403 to someone who takes no part and 404 to an unknown
    #                reference (as UC17's draft asks). Should both be 404?

    Scenario: The validator who closed the procedure can get the document
      When Joana Pires asks for the document of "AC-2026-0098"
      Then she gets "ordem_reembolso_AC-2026-0098.pdf"

    Scenario Outline: Someone else cannot get the document, even with its address
      When <person> asks for the document of "AC-2026-0098"
      Then it is refused

      Examples:
        | person        |
        | Sofia Almeida |
        | Rui Matos     |
        | Carla Dias    |

    Scenario: Asking for the document is not possible without signing in
      When someone who has not signed in asks for the document of "AC-2026-0098"
      Then they are asked to sign in
