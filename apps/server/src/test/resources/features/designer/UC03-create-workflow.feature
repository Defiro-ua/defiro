# Draft for team review: the examples come from the acceptance criteria of issue #27 and the
# messages of the web prototype (apps/web/src/domain/designer.ts). No Example Mapping was run yet.
@UC03
Feature: Create workflow
  As a Workflow Manager,
  I want to create a workflow in the designer, one question at a time,
  so that a new type of request can be made without programming.

  Background:
    Given Sofia Almeida is a Workflow Manager
    And she is designing the workflow "Ajudas de custo"

  Rule: Only a Workflow Manager can design and create workflows
    # The role is the app role «Workflow Manager» that UC01 reads from Entra ID.

    Scenario: Someone who is not a Workflow Manager cannot start designing a workflow
      Given Ana Ferreira is not a Workflow Manager
      When she starts designing the workflow "Horas extraordinárias"
      Then she is not allowed to

    Scenario: Someone who is not a Workflow Manager cannot create a workflow
      Given Ana Ferreira is not a Workflow Manager
      When she tries to create the workflow Sofia Almeida is designing
      Then she is not allowed to

  # Criteria 1, 2, 3 and 8 are about the screens: these @browser rules run in a browser (Playwright),
  # against the web app, in the Acceptance stage. The request form joins the screens with UC04.
  @browser
  Rule: «Criar workflow» is the main button in the Workflow Manager's side menu

    Scenario: The Workflow Manager starts a workflow from the side menu
      When Sofia Almeida opens defiro
      Then «Criar workflow» is the main button of her side menu

  @browser
  Rule: The designer is a sequence of screens, each with «Passo X de N» and a progress bar

    Scenario: The first screen asks for the name
      When she starts creating a workflow
      Then she sees the screen for the name with «Passo 1 de 5»

  @browser
  Rule: «Anterior» goes back without losing answers, and «Sair» leaves the designer

    Scenario: Going back keeps the answers
      Given she named the workflow and moved on
      When she goes back
      Then the name "Ajudas de custo" is still there

  Rule: Each review step has a name, who handles it, a deadline and its reasons

    Scenario: A complete review step joins the path
      When she adds the review step "Validação da chefia" handled by "Chefias da unidade do requerente" with a deadline of 5 working days
      Then the path is "Pedido", "Validação da chefia"

  Rule: Each step needs at least one reason, every reason needs a name, and return and reject reasons are separate

    Scenario: A step with no reasons cannot be created
      Given the step "Validação da chefia" has no reasons
      When she creates the workflow
      Then it is refused with "Passo 2 («Validação da chefia»): acrescente pelo menos um motivo para devolver ou rejeitar."

    Scenario: A reason with no name cannot be created
      Given the step "Validação da chefia" has a return reason with no name
      When she creates the workflow
      Then it is refused with "Passo 2 («Validação da chefia»): dê um nome ao motivo de devolução 1."

    Scenario: Return and reject reasons are kept apart
      When she gives "Validação da chefia" the return reason "Preenchido incorretamente" and the reject reason "Pedido não autorizado"
      Then "Validação da chefia" can be returned for "Preenchido incorretamente" only
      And it can be rejected for "Pedido não autorizado" only

  # The path is edited in the browser, and the Designer only stores the whole draft
  # (docs/sequence_diagram.txt, UC03): these run in a browser. When publishing, the Designer still
  # refuses a path whose final validation is not last or not alone.
  @browser
  Rule: Steps can be edited, moved, removed or added, and the final validation is always last
    # open question: can the request step «Pedido» ever be removed or moved? The prototype says no.

    Scenario: Moving a review step up
      Given the path is "Pedido", "Validação da chefia", "Verificação financeira"
      When she moves "Verificação financeira" up
      Then the path is "Pedido", "Verificação financeira", "Validação da chefia"

    Scenario: Removing a review step
      Given the path is "Pedido", "Validação da chefia", "Verificação financeira"
      When she removes "Verificação financeira"
      Then the path is "Pedido", "Validação da chefia"

    Scenario: The final validation goes last
      Given the path is "Pedido", "Validação da chefia"
      When she adds a final validation step
      Then the path ends with "Validação final"

    Scenario: There is only one final validation
      Given the path ends with "Validação final"
      When she adds a final validation step
      Then the path has one "Validação final"

  Rule: A workflow with a missing or invalid answer is not created, and the error names the field

    Scenario: A workflow with no name
      Given the workflow has no name
      When she creates the workflow
      Then it is refused with "Dê um nome ao workflow." on the workflow's name

  Rule: Creating a complete workflow makes it active, with the notice «Workflow criado»

    Scenario: Creating a complete workflow
      Given the workflow is complete
      When she creates the workflow
      Then "Ajudas de custo" is active as version 1

  Rule: The new workflow appears in «Workflows» and in «Novo pedido» for the people allowed to request it
    # open question: «Novo pedido» belongs to UC13 (Engine); that part is tested once UC13 exists.

    Scenario: The new workflow is listed in «Workflows»
      Given "Ajudas de custo" was created
      When she opens the list of workflows
      Then "Ajudas de custo" is listed

  Rule: A created workflow can be downloaded as a file, and a file can start a new workflow
    # The file is the workflow's specification in JSON (docs/workflow-specification.md). A file that
    # comes in becomes a new draft, to review in the designer: the Designer checks it when it is
    # created, as any other draft.

    Scenario: Downloading a created workflow gives its published version
      Given "Ajudas de custo" was created
      When she downloads "Ajudas de custo"
      Then she gets the file "ajudas-de-custo-v1.json" with the workflow "Ajudas de custo" and the path "Pedido", "Validação da chefia"

    Scenario: A workflow file becomes a draft to review
      Given she has the file of the workflow "Horas extraordinárias" with the path "Pedido", "Validação da chefia"
      When she loads the file
      Then she has a new draft "Horas extraordinárias" with the path "Pedido", "Validação da chefia"
      And it is not created yet

    Scenario: A file that is not a workflow is refused
      When she loads a file that is not a workflow
      Then the file is refused with "O ficheiro não é um workflow da defiro."

    @browser
    Scenario: Creating a workflow from a file
      Given she has the file of the workflow "Horas extraordinárias" with the path "Pedido", "Validação da chefia"
      When she loads the file in «Workflows» and creates the workflow
      Then «Workflows» lists "Horas extraordinárias" as version 1
