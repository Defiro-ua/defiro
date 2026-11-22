# Draft for team review: the examples come from the acceptance criteria of issue #33, the
# verification session with Pedro (2026-11-22) and the web prototype and mockup (people, request
# AC-2026-0147 and its corrected version 2). No Example Mapping was run yet.
#
# Stand-ins, until their owners build them (each marked in the code for its owner):
#   - UC13 (Dinis): the request is created by a test step, not by the request form.
#   - UC15 (Dinis): files are kept by a temporary store in the database, behind the Files interface.
#   - UC20 (Dinis): «Ana corrects and resubmits» is a test step over a resubmit stand-in.
#   - UC27 (Daniel): the inbox is a minimal list: reference, requester, step and «Rever».
# open question (Dinis, Candeias): the answers are kept as [{field, label, value}]. Does that fit
#                the request form of UC13 and the form fields of UC04?
# @wip until the team has reviewed this draft and its steps exist.
@UC17 @wip
Feature: Approve a document
  As a Reviewer,
  I want to approve a request in a couple of clicks from my inbox,
  so that it moves on without delay.

  # People: Ana Ferreira, Rui Matos, Carla Dias and Sofia Almeida are the mockup's personas, as they are.
  # Luísa Prata is «Chefe de Divisão» in the mockup; her unit and groups are deduced from her
  #   approving «Validação da chefia» for Pedro Sousa, of the Divisão de Ambiente. Her manager is unknown.
  # Segunda Chefia is a placeholder, not in the mockup: a second «Chefias» member of Ana's unit,
  #   needed for «every candidate sees the task» and «the first to act wins». To be replaced by a
  #   person the team defines.
  Background:
    Given the organization's Entra ID has these accounts
      | name           | unit                        | job title                     | manager      | groups                                                       |
      | Ana Ferreira   | Divisão de Obras Municipais | Técnica Superior              | Rui Matos    | Todos os trabalhadores, Divisão de Obras Municipais          |
      | Rui Matos      | Divisão de Obras Municipais | Chefe de Divisão              | Helena Costa | Todos os trabalhadores, Chefias, Divisão de Obras Municipais |
      | Segunda Chefia | Divisão de Obras Municipais | Chefe de Divisão              | Helena Costa | Todos os trabalhadores, Chefias, Divisão de Obras Municipais |
      | Carla Dias     | Divisão Financeira          | Técnica da Divisão Financeira | Rui Matos    | Todos os trabalhadores, Divisão Financeira                   |
      | Sofia Almeida  | Modernização Administrativa | Gestora de workflows          | Rui Matos    | Todos os trabalhadores                                       |
      | Luísa Prata    | Divisão de Ambiente         | Chefe de Divisão              |              | Todos os trabalhadores, Chefias, Divisão de Ambiente         |
    And the workflow "Ajudas de custo" has the path
      | step                   | handled by                       |
      | Pedido                 | Todos os trabalhadores           |
      | Validação da chefia    | Chefias da unidade do requerente |
      | Verificação financeira | Divisão Financeira               |
      | Validação final        | RH · Formação                    |
    # The mockup gives its dates relative to the day it is opened (Partida 14 days before, at 07:30;
    #   Regresso 12 days before, at 19:40; corrected to 13 days before, at 19:10). They are fixed
    #   here from 22/11/2026, the day this draft was written, in the prototype's format.
    And Ana Ferreira submitted the request "AC-2026-0147" for "Ajudas de custo" with
      | field      | answer                          |
      | Motivo     | Formação                        |
      | Destino    | Lisboa                          |
      | Descrição  | Formação em Contratação Pública |
      | Partida    | 08/11/2026 07:30                |
      | Regresso   | 10/11/2026 19:40                |
      | Transporte | Transportes públicos            |
      | NIF        | 234 567 418                     |
      | IBAN       | PT50 0035 0000 1234 5678 9015 4 |
    And with the files "bilhete_comboio.pdf" and "fatura_hotel.pdf"

  Rule: A request opens from the inbox in one click, and shows the data, the files, the path with the current step and the history
    # The table of expenses with their receipts and the total belong to UC15 #8 (Dinis).
    # The history section, closed by default with «Ver histórico», is shown as in the prototype;
    # its own rules (order, entries never change, who sees it) are UC25 (Bernardo).
    # Opening the request in one click from the inbox is checked by the browser journey below.

    Scenario: The request shows its data and files
      When Rui Matos opens the request "AC-2026-0147"
      Then he sees the answer "Lisboa" for "Destino" and "PT50 0035 0000 1234 5678 9015 4" for "IBAN"
      And he sees the files "bilhete_comboio.pdf" and "fatura_hotel.pdf"

    Scenario: The request shows the path with the current step
      When Rui Matos opens the request "AC-2026-0147"
      Then he sees the path "Pedido", "Validação da chefia", "Verificação financeira", "Validação final" at "Validação da chefia"

    Scenario: The request shows its history
      When Rui Matos opens the request "AC-2026-0147"
      Then its history shows "Ana Ferreira, Técnica Superior, submeteu o pedido"

    Scenario: The reviewer can open a file of the request
      When Rui Matos opens the file "bilhete_comboio.pdf" of the request "AC-2026-0147"
      Then he gets the content Ana Ferreira attached

    Scenario: Someone who takes no part in the request cannot open it
      When Sofia Almeida opens the request "AC-2026-0147"
      Then she is not allowed to

    Scenario: Someone who takes no part in the request cannot open its files
      When Sofia Almeida opens the file "bilhete_comboio.pdf" of the request "AC-2026-0147"
      Then she is not allowed to

  Rule: On a resubmitted request, the reviewer sees what changed in the new version
    # The whole request is shown. A changed field is marked «Alterado» and shows «Antes: …»;
    # a new file is marked «Novo na versão 2». The correction is the one of the mockup.

    Background:
      Given Ana Ferreira corrected "Regresso" to "09/11/2026 19:10" and attached "certificado_presenca.pdf", resubmitting the request "AC-2026-0147" as version 2

    Scenario: A changed field shows its previous answer
      When Rui Matos opens the request "AC-2026-0147"
      Then "Regresso" is marked as changed, with the answer of version 1, "10/11/2026 19:40", before it
      And "Destino" is not marked as changed

    Scenario: A new file is marked as new in this version
      When Rui Matos opens the request "AC-2026-0147"
      Then the file "certificado_presenca.pdf" is marked as new in version 2
      And the file "bilhete_comboio.pdf" is not

  @browser
  Rule: Approve is the main button and comes last in its group
    # «Devolver para correção» and «Rejeitar o pedido» are UC18: until then they are shown but disabled.

    Scenario: The decision buttons end with «Aprovar»
      When Rui Matos reviews the request "AC-2026-0147"
      Then his decision buttons are «Devolver para correção», «Rejeitar o pedido» and «Aprovar», with «Aprovar» as the main one

  @browser
  Rule: Approving takes 2 clicks from the inbox, with no confirmation dialog

    Scenario: Approving from the inbox
      When Rui Matos approves the request "AC-2026-0147" from his inbox
      Then it took him 2 clicks, with no confirmation asked

  Rule: Approving sends the request to the next step and its group, and the reviewer returns to the inbox
    # A step is handled by a group or a unit of Entra ID, and its members are the candidates.
    # «Chefias da unidade do requerente» means the members of «Chefias» in the requester's unit.
    # open question (Candeias, UC05): the Designer keeps the group's name, but Entra ID puts each
    #                group's id in the token. How does the Engine match the two?
    # Returning to the inbox is checked by the browser journey below.

    Scenario Outline: Every candidate of the step, and only they, have the task in their inbox
      When <person> looks at the inbox
      Then the request "AC-2026-0147" is <shown>

      Examples:
        | person         | shown     |
        | Rui Matos      | there     |
        | Segunda Chefia | there     |
        | Luísa Prata    | not there |
        | Carla Dias     | not there |

    Scenario: Approving moves the request to the next step and its group
      When Rui Matos approves the request "AC-2026-0147"
      Then the request is at "Verificação financeira", in the inbox of Carla Dias
      And it is no longer in the inbox of Rui Matos or Segunda Chefia

    Scenario: Only a candidate of the step can approve
      When Luísa Prata approves the request "AC-2026-0147"
      Then she is not allowed to
      And the request is still at "Validação da chefia"

    Scenario: The first to act wins
      Given Segunda Chefia approved the request "AC-2026-0147"
      When Rui Matos approves the request "AC-2026-0147"
      Then he is told «Este pedido já foi tratado por Segunda Chefia, Chefe de Divisão.»
      And the request was approved only once

  Rule: The notice «Pedido aprovado» appears with «Desfazer», which reverses the decision, and stays until it is closed
    # Decision with Pedro: an approval can be undone while nobody has acted on the next step.
    # After that the server refuses. Undoing is logged; nothing is erased from the history.

    Scenario: Undoing before the next step is acted on
      Given Rui Matos approved the request "AC-2026-0147"
      When he undoes his approval
      Then the request is back at "Validação da chefia", in the inbox of Rui Matos
      And it is no longer in the inbox of Carla Dias

    Scenario: Undoing is refused once the next step was acted on
      Given Rui Matos approved the request "AC-2026-0147"
      And Carla Dias approved it at "Verificação financeira"
      When Rui Matos undoes his approval
      Then he is told «Já não é possível desfazer: o pedido já foi tratado no passo «Verificação financeira».»
      And the request is still at "Validação final"

    Scenario: Only the reviewer who approved can undo
      Given Rui Matos approved the request "AC-2026-0147"
      When Carla Dias undoes his approval
      Then she is not allowed to

    Scenario: Undoing is logged in the history
      Given Rui Matos approved the request "AC-2026-0147"
      When he undoes his approval
      Then the newest entry of its history is "Rui Matos, Chefe de Divisão, desfez a aprovação do passo «Validação da chefia»", with the date and time of the undoing

    @browser
    Scenario: The notice offers «Desfazer» and stays until it is closed
      Given Rui Matos approved the request "AC-2026-0147" from his inbox
      When 10 seconds go by
      Then his inbox still shows the notice «Pedido aprovado» with «Desfazer»

    @browser
    Scenario: «Desfazer» goes back to the request
      Given Rui Matos approved the request "AC-2026-0147" from his inbox
      When he chooses «Desfazer»
      Then he is reviewing the request "AC-2026-0147" again, with the notice «Aprovação desfeita»

  Rule: The approval is logged in the history with the reviewer's name, job title, date and time

    Scenario: Approving adds two entries to the history
      When Rui Matos approves the request "AC-2026-0147"
      Then the newest entries of its history, with the date and time of the approval, are
        | entry                                                              |
        | Passou ao passo «Verificação financeira», com Divisão Financeira   |
        | Rui Matos, Chefe de Divisão, aprovou o passo «Validação da chefia» |

  # The browser journey of UC17 in the QA Strategy, with the return and correction of the mockup.
  # It turns on (@wip off) when UC18 (return) and UC19 (notes) are merged; «Ana corrects and
  # resubmits» stays a test step until UC20 (Dinis).
  @browser @wip
  Rule: Journey: Rui returns with a note, Ana corrects, Rui approves

    Scenario: A returned request comes back corrected and is approved
      Given Rui Matos returned the request "AC-2026-0147" for "Preenchido incorretamente", with the note "Falta o certificado de presença com o carimbo da entidade formadora." on "Certificado de presença"
      And Ana Ferreira corrected "Regresso" to "09/11/2026 19:10" and attached "certificado_presenca.pdf", resubmitting it as version 2
      When Rui Matos approves the request "AC-2026-0147" from his inbox, after seeing what changed
      Then he is back on his inbox with the notice «Pedido aprovado»
      And the request is at "Verificação financeira"
