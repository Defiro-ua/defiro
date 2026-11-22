# Draft for team review: the examples come from the acceptance criteria of issue #29 and the groups
# of the web prototype. As agreed for #29, the organization's groups and units are a configured list
# (defiro.directory in application.properties) until defiro reads them from Entra ID through
# Microsoft Graph, and a step keeps the name of who handles it.
@UC05
Feature: Assign a group or unit to each step
  As a Workflow Manager,
  I want to say which group or unit handles each step,
  so that each task reaches the right people.

  Background:
    Given Sofia Almeida is a Workflow Manager
    And she is designing the workflow "Ajudas de custo"

  Rule: Each step's handler is chosen from the organization's groups and units
    # «Chefias da unidade do requerente» is not a group: defiro works it out from the requester's unit.

    Scenario: The organization's groups and units can handle a step
      When she asks who can handle a step
      Then she can choose "Chefias da unidade do requerente", "Chefias", "Divisão de Obras Municipais", "Divisão Financeira", "Recursos Humanos" and "RH · Formação"

    Scenario: A step handled by someone outside the organization cannot be created
      Given the step "Validação da chefia" is handled by "Departamento de Marketing"
      When she creates the workflow
      Then it is refused with "Passo 2 («Validação da chefia»): «Departamento de Marketing» não é um grupo nem uma unidade da organização."

    @browser
    Scenario: The step's screen offers the organization's groups and units
      When she adds a review step in the designer
      Then the step's screen offers "Chefias da unidade do requerente", "Chefias", "Divisão de Obras Municipais", "Divisão Financeira", "Recursos Humanos" and "RH · Formação"

  Rule: The first screen sets who can request, and the workflow only appears in «Novo pedido» for those people

    Scenario: Who can make the request
      When she asks who can make the request
      Then she can choose "Todos os trabalhadores", "Chefias", "Divisão de Obras Municipais", "Divisão Financeira", "Recursos Humanos" and "RH · Formação"

    Scenario: The request cannot be made by someone outside the organization
      Given the request can be made by "Departamento de Marketing"
      When she creates the workflow
      Then it is refused with "Passo 1 («Pedido»): «Departamento de Marketing» não é um grupo nem uma unidade da organização."

    # «Novo pedido» is UC13's (Engine): this scenario runs once it exists.
    @wip
    Scenario: Only the people who can request see the workflow in «Novo pedido»
      Given "Ajudas de custo" was created for "Divisão Financeira" only
      When Ana Ferreira, who is not in "Divisão Financeira", opens «Novo pedido»
      Then she does not see "Ajudas de custo"

  Rule: A step with no handler cannot move on

    Scenario: A step with no handler cannot be created
      Given the step "Validação da chefia" is handled by nobody
      When she creates the workflow
      Then it is refused with "Passo 2 («Validação da chefia»): escolha quem trata."

    @browser
    Scenario: The step's screen does not move on without who handles it
      When she adds a review step in the designer
      And she moves on without choosing who handles it
      Then she is told "Passo 2 («Revisão»): escolha quem trata."

  @browser
  Rule: The path shows who handles each step

    Scenario: Who handles each step, in the path
      Given the path is "Pedido", "Validação da chefia"
      Then the path shows "Pedido" made by "Todos os trabalhadores" and "Validação da chefia" handled by "Chefias da unidade do requerente"

  # The inbox is UC27's (Engine): this rule runs once it exists.
  @wip
  Rule: When a request reaches a step, the task appears in the inbox of the people in that step's group

    Scenario: The task reaches the step's group
      Given "Ajudas de custo" was created with "Validação da chefia" handled by "Divisão Financeira"
      When a request reaches "Validação da chefia"
      Then the task appears in the inbox of the people in "Divisão Financeira"
