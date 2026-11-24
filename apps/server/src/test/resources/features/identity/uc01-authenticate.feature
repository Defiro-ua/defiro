# The identity provider is played by a fake OpenID Connect provider: no test calls a real one.
# The values the user sees (menu items, notices) are the ones of the web prototype (apps/web).
@UC01
Feature: Authenticate (SSO)
  As a municipal employee,
  I want to sign in with my organization's account,
  so that I don't need another password and only see what my role allows.

  Background:
    Given these people work in the organization
      | name          | unit                        | job title            | manager   | role             |
      | Ana Ferreira  | Divisão de Obras Municipais | Técnica Superior     | Rui Matos |                  |
      | Sofia Almeida | Modernização Administrativa | Gestora de workflows | Rui Matos | Workflow Manager |

  Rule: The home page has a single «Entrar» button, which opens the organization's sign-in page
    # The button itself is on the screen, so the UC01 browser journey checks it.

    Scenario: Signing in starts on the organization's sign-in page
      When Ana Ferreira chooses to sign in
      Then she is sent to the organization's sign-in page

  Rule: The identity provider only says who the person is; name, unit, job title and manager come from defiro
    # The name is brought up to date from the identity provider at each sign-in.

    Scenario: The profile comes from defiro
      When Ana Ferreira signs in
      Then defiro knows her as "Ana Ferreira", "Técnica Superior" in "Divisão de Obras Municipais", managed by "Rui Matos"

    Scenario: A change made in defiro shows without signing in again
      Given Ana Ferreira is signed in
      When IT changes her job title in defiro to "Técnica Superior Especialista"
      Then defiro knows her job title as "Técnica Superior Especialista"

    Scenario: Signing in stores no password
      When Ana Ferreira signs in
      Then defiro has stored no password

  Rule: Someone defiro does not know yet gets an account with no roles and no groups

    Scenario: A first sign-in creates the account
      Given Marta Nunes has an account at the identity provider, and defiro does not know her yet
      When Marta Nunes signs in
      Then she lands on her task inbox
      And her side menu is «Novo pedido» and «Caixa de tarefas»
      And she belongs to no groups

  Rule: A pre-registered person is recognised by their verified email, and only by that

    Scenario: A verified email
      Given Rita Lopes is registered in defiro by her email, as a "Workflow Manager"
      And the identity provider has verified that email
      When Rita Lopes signs in
      Then her side menu is «Criar workflow», «Novo pedido», «Caixa de tarefas» and «Workflows»

    Scenario: An email nobody verified
      Given Rita Lopes is registered in defiro by her email, as a "Workflow Manager"
      And the identity provider has not verified that email
      When Rita Lopes signs in
      Then her side menu is «Novo pedido» and «Caixa de tarefas»

  Rule: An inactive person cannot sign in

    Scenario: An inactive person is refused
      Given Ana Ferreira is inactive in defiro
      When Ana Ferreira signs in
      Then she is back on the home page with the notice «A sua conta está inativa»
      And she is not signed in to defiro

  Rule: The groups the user belongs to are the ones defiro has
    # Departments and units are groups too (UC05, UC28).

    Scenario: The groups come from defiro
      Given in defiro Ana Ferreira belongs to the groups "Divisão de Obras Municipais" and "Finanças"
      When Ana Ferreira signs in
      Then defiro knows her groups as "Divisão de Obras Municipais" and "Finanças"

  Rule: After signing in, the user lands on their task inbox
    # open question: the inbox is UC27. Until it exists, signing in lands on the inbox's route
    #                in the web app, «/caixa-de-tarefas», as a placeholder.

    Scenario: Signing in lands on the inbox
      When Ana Ferreira signs in
      Then she lands on her task inbox

  Rule: The side menu only shows what the user's role allows: «Workflows» and «Criar workflow» appear only for the Workflow Manager

    Scenario: A Workflow Manager also sees the workflows
      When Sofia Almeida signs in
      Then her side menu is «Criar workflow», «Novo pedido», «Caixa de tarefas» and «Workflows»

    Scenario: An employee without the Workflow Manager role does not
      When Ana Ferreira signs in
      Then her side menu is «Novo pedido» and «Caixa de tarefas»

  Rule: The top bar shows the user's name and job title

    Scenario: The top bar shows who is signed in
      When Sofia Almeida signs in
      Then the top bar shows "Sofia Almeida" and "Gestora de workflows"

  Rule: Signing out ends the session and returns to the home page with the notice «Sessão terminada»
    # It also ends the session at the identity provider: the next «Entrar» asks who is signing in.

    Scenario: Signing out ends the session
      Given Ana Ferreira is signed in
      When she signs out
      Then she is no longer signed in to defiro

    Scenario: Signing out returns to the home page with the notice
      Given Ana Ferreira is signed in
      When she signs out
      Then she is back on the home page with the notice «Sessão terminada»

    @browser
    Scenario: Signing out from the account menu
      Given Ana Ferreira signed in with the browser
      When she chooses «Terminar sessão» in the account menu
      Then the browser shows the home page with the notice «Sessão terminada»
