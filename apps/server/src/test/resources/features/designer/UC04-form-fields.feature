# Draft for team review: the examples come from the acceptance criteria of issue #28 and the request
# form of the web prototype (prototype/frontend, apps/web/src/domain/designer.ts). The fields are the
# questions of the request form: only the request step has them, as agreed for #28.
@UC04
Feature: Define the form fields of each step
  As a Workflow Manager,
  I want to choose the questions the request form asks,
  so that the platform generates the form without programming.

  Background:
    Given Sofia Almeida is a Workflow Manager
    And the organization has the groups "Chefias", "Divisão de Obras Municipais", "Divisão Financeira", "Recursos Humanos" and "RH · Formação"
    And she is designing the workflow "Ajudas de custo"

  @browser
  Rule: Name, organizational unit and manager are filled in from the profile and are never asked

    Scenario: The request form starts with the profile
      When she opens the request form of the workflow
      Then the form shows "Nome", "Unidade orgânica" and "Chefia direta" from the profile, and she cannot change them

  Rule: Each field is created on its own screen with a label, an answer type and whether it is mandatory

    Scenario: A question joins the request form
      When she adds the question "Material pedido", a short text that is mandatory
      Then the request form asks "Material pedido", a short text that is mandatory

    @browser
    Scenario: A question has its own screen
      When she adds a question to the request form
      Then she sees the screen of the question, with its label, its answer type and whether it is mandatory

  @browser
  Rule: The answer type is chosen from cards with examples

    Scenario: The answer types
      When she adds a question to the request form
      Then she can choose "Texto curto", "Texto longo", "Número", "Data", "Data e hora", "Sim ou não", "Escolha única" and "Ficheiros"

  Rule: A single-choice field needs at least two options

    Scenario: A single choice with one option cannot be created
      Given the request form asks "Transporte", a single choice of "Carro próprio" only
      When she creates the workflow
      Then it is refused with "Formulário do pedido, campo 1 («Transporte»): faltam opções de resposta (pelo menos duas)."

  @browser
  Rule: A field with no label, or a choice with no options, is flagged in the field list

    Scenario: A question with no label
      Given the request form has a question with no label
      When she opens the request form of the workflow
      Then the question is flagged with «Falta o rótulo»

  @browser
  Rule: Fields can be moved up or down and removed

    Scenario: Moving a question up
      Given the request form asks "Local da deslocação" and then "Transporte"
      When she moves the question "Transporte" up
      Then the request form asks "Transporte" and then "Local da deslocação"

    Scenario: Removing a question
      Given the request form asks "Local da deslocação" and then "Transporte"
      When she removes the question "Transporte"
      Then the request form asks "Local da deslocação" only

  Rule: The workflow cannot be created without at least one question for the requester

    Scenario: A workflow with no question
      Given the workflow is complete but its request form has no question
      When she creates the workflow
      Then it is refused with "Formulário do pedido: ainda não tem perguntas para quem faz o pedido… crie pelo menos uma pergunta, por exemplo «Material pedido»"

  # The generated form is «Novo pedido», which UC13 builds (Engine): this rule runs once it exists.
  @wip
  Rule: In the generated form, optional fields say «(opcional)», number fields only take digits and dates are written as dd/mm/aaaa

    Scenario: How the generated form shows each field
      Given the request form asks "Justificação", an optional long text, "Quilómetros", a number, and "Data de partida", a date
      When a requester opens the form of "Ajudas de custo"
      Then "Justificação" says «(opcional)»
      And "Quilómetros" only takes digits
      And "Data de partida" is written as dd/mm/aaaa
