# Draft for team review: the examples come from the acceptance criteria of issue #34, the
# verification session with Pedro (2026-11-23), the prototype (the reasons of each step, the texts)
# and the mockup (the message of Rui Matos's return of AC-2026-0147). No Example Mapping was run yet.
#
# Stand-ins, as in UC17: UC13 (the request is created by a test step), UC15 (temporary file store),
# UC20 («Ana resubmits» is a test step), UC27 (minimal inbox).
# PLACEHOLDER, to revisit: «Mensagem de rejeição» (no rejection message exists in the prototype or the
#   mockup), and Segunda Chefia, as in UC17.
# open question (Candeias, UC03): a step can be published with reasons of one kind only. Rejecting is
#                always possible (Pedro, 2026-11-23): should each step need reasons of both kinds?
# Criterion 9 (rejecting needs no signature): signatures are outside the MVP, so nothing here signs.
@UC18 @wip
Feature: Reject a document
  As a Reviewer,
  I want to return a request for correction or reject it, choosing one or more reasons,
  so that the requester knows exactly what went wrong.

  # The same people, path and request as UC17 and UC19 (see the comments there). The reasons are the
  # prototype's. The reviewer chooses reasons from the workflow's lists; notes (UC19) are free text.
  Background:
    Given the organization's Entra ID has these accounts
      | name           | unit                        | job title                     | manager      | groups                                                       |
      | Ana Ferreira   | Divisão de Obras Municipais | Técnica Superior              | Rui Matos    | Todos os trabalhadores, Divisão de Obras Municipais          |
      | Rui Matos      | Divisão de Obras Municipais | Chefe de Divisão              | Helena Costa | Todos os trabalhadores, Chefias, Divisão de Obras Municipais |
      | Segunda Chefia | Divisão de Obras Municipais | Chefe de Divisão              | Helena Costa | Todos os trabalhadores, Chefias, Divisão de Obras Municipais |
    And the workflow "Ajudas de custo" has the path
      | step                   | handled by                       | reasons to return                                                                      | reasons to reject                              |
      | Pedido                 | Todos os trabalhadores           |                                                                                        |                                                |
      | Validação da chefia    | Chefias da unidade do requerente | Preenchido incorretamente; Falta documento comprovativo                                | Deslocação não autorizada; Fora do prazo legal |
      | Verificação financeira | Divisão Financeira               | Preenchido incorretamente; Falta documento comprovativo                                | Despesa não elegível                           |
      | Validação final        | RH · Formação                    | Falta o certificado de presença ou não está válido; Datas do certificado não coincidem | Fora do prazo legal                            |
    And Ana Ferreira submitted the request "AC-2026-0147" for "Ajudas de custo" with
      | field                | answer                          |
      | Motivo               | Formação                        |
      | Destino              | Lisboa                          |
      | Descrição            | Formação em Contratação Pública |
      | Partida              | 08/11/2026 07:30                |
      | Regresso             | 10/11/2026 19:40                |
      | Datas do certificado | 08/11/2026 a 09/11/2026         |
      | Transporte           | Transportes públicos            |
      | NIF                  | 234 567 418                     |
      | IBAN                 | PT50 0035 0000 1234 5678 9015 4 |
    And with the files "bilhete_comboio.pdf" and "fatura_hotel.pdf"
    And Rui Matos took the request "AC-2026-0147" from his inbox

  @browser
  Rule: Return and Reject are two separate buttons

    Scenario: The decision has both
      When Rui Matos reviews the request "AC-2026-0147"
      Then he can open «Devolver para correção» and «Rejeitar o pedido», one at a time

  Rule: Each button opens its own list of the step's reasons, and at least one must be chosen

    @browser
    Scenario Outline: Each button lists its own reasons, as checkboxes
      When Rui Matos opens «<button>» on the request "AC-2026-0147"
      Then he can tick "<reasons>", and only those

      Examples:
        | button                 | reasons                                                 |
        | Devolver para correção | Preenchido incorretamente; Falta documento comprovativo |
        | Rejeitar o pedido      | Deslocação não autorizada; Fora do prazo legal          |

    @browser
    Scenario Outline: Confirming with no reason shows the error
      Given Rui Matos opened «<button>» on the request "AC-2026-0147"
      When he confirms it with no reason
      Then he sees the error «Escolha pelo menos um motivo.»

      Examples:
        | button                 |
        | Devolver para correção |
        | Rejeitar o pedido      |

    Scenario: The server refuses a return with no reason
      When Rui Matos returns the request "AC-2026-0147" for "" with the message "Corrija a data de regresso e junte o certificado, por favor."
      Then the decision is refused
      And the request is still at "Validação da chefia"

  Rule: Return only takes correctable reasons, and sends the request back to the requester
    # It creates no task: the returned request in Ana's inbox is UC20 (Dinis) and UC27 (Daniel).

    Scenario: The request goes back to the requester
      When Rui Matos returns the request "AC-2026-0147" for "Preenchido incorretamente; Falta documento comprovativo" with the message "Corrija a data de regresso e junte o certificado, por favor."
      Then the request is returned, at "Pedido"
      And it is no longer in the inbox of Rui Matos or Segunda Chefia

    Scenario: A reason to reject does not return
      When Rui Matos returns the request "AC-2026-0147" for "Fora do prazo legal" with the message "Corrija a data de regresso e junte o certificado, por favor."
      Then the decision is refused
      And the request is still at "Validação da chefia"

    Scenario: Only who took the task returns
      When Segunda Chefia returns the request "AC-2026-0147" for "Preenchido incorretamente" with the message "Corrija a data de regresso e junte o certificado, por favor."
      Then she is not allowed to
      And the request is still at "Validação da chefia"

  Rule: Reject only takes the reasons that end the request, and ends it as rejected
    # Decision with Pedro (2026-11-23): rejecting needs a message, with or without notes, as notes do
    # not go to the requester with a rejection. It is optional in the prototype.

    Scenario: The procedure ends as rejected
      When Rui Matos rejects the request "AC-2026-0147" for "Fora do prazo legal" with the message "Mensagem de rejeição"
      Then the request is rejected, at "Validação da chefia"
      And it is no longer in the inbox of Rui Matos or Segunda Chefia

    Scenario: A reason to return does not reject
      When Rui Matos rejects the request "AC-2026-0147" for "Preenchido incorretamente" with the message "Mensagem de rejeição"
      Then the decision is refused

    Scenario: Only who took the task rejects
      When Segunda Chefia rejects the request "AC-2026-0147" for "Fora do prazo legal" with the message "Mensagem de rejeição"
      Then she is not allowed to

    Scenario: A rejection with no message is refused, even with a note
      Given Rui Matos left the note "Falta o certificado de presença com o carimbo da entidade formadora." on "Datas do certificado"
      When Rui Matos rejects the request "AC-2026-0147" for "Fora do prazo legal" with the message ""
      Then the decision is refused

    @browser
    Scenario: The confirm button is red, and the message mandatory
      Given Rui Matos opened «Rejeitar o pedido» on the request "AC-2026-0147"
      When he confirms it for "Fora do prazo legal" with no message
      Then he sees the error «Diga a Ana a razão da rejeição: escreva a mensagem.»
      And the confirm button is red

  Rule: When returning, the message is mandatory if the reviewer left no note
    # A message has at most 1000 characters, as a note.

    Scenario: With no note and no message, the return is refused
      When Rui Matos returns the request "AC-2026-0147" for "Preenchido incorretamente" with the message ""
      Then the decision is refused
      And the request is still at "Validação da chefia"

    Scenario: With a note, the message may be left out
      Given Rui Matos left the note "Falta o certificado de presença com o carimbo da entidade formadora." on "Datas do certificado"
      When Rui Matos returns the request "AC-2026-0147" for "Preenchido incorretamente" with the message ""
      Then the request is returned, at "Pedido"

    Scenario: The server refuses a message longer than 1000 characters
      When Rui Matos returns the request "AC-2026-0147" for "Preenchido incorretamente" with a message of 1001 characters
      Then the decision is refused

    @browser
    Scenario: Returning with no note and no message shows the error
      Given Rui Matos opened «Devolver para correção» on the request "AC-2026-0147"
      When he confirms it for "Preenchido incorretamente" with no message
      Then he sees the error «Diga a Ana o que tem de corrigir: escreva a mensagem ou deixe uma nota no campo.»

  # Left for UC22 (Bernardo), whose step is the only one with this reason (Pedro, 2026-11-23).
  @browser @wip
  Rule: Choosing «Datas do certificado não coincidem» fills in the message with the dates that do not match

    Scenario: The message says the dates
      Given the request is at "Validação final"
      When the reviewer ticks "Datas do certificado não coincidem"
      Then the message reads «O certificado indica 08/11/2026 a 09/11/2026 e a deslocação foi de 08/11/2026 a 10/11/2026. Junte o certificado correto ou corrija as datas da deslocação.»

  Rule: The notice reads «Pedido devolvido para correção» or «Pedido rejeitado», with «Desfazer»
    # As in UC17: a return can be undone until the requester resubmits, a rejection while nobody has
    # acted after it. The task goes back to who decided, with their notes as draft.

    Scenario Outline: Undoing gives the request back to who decided it
      Given Rui Matos <decided> the request "AC-2026-0147" for "<reason>" with the message "<message>"
      When he undoes his decision
      Then the request is back at "Validação da chefia", in the inbox of Rui Matos
      And the request is in the inbox of Segunda Chefia, marked «Em revisão por Rui Matos, Chefe de Divisão»
      And the newest entry of its history is "Rui Matos, Chefe de Divisão, desfez a <decision> do passo «Validação da chefia»", with the date and time of the undoing

      Examples:
        | decided  | decision  | reason                    | message                                                      |
        | returned | devolução | Preenchido incorretamente | Corrija a data de regresso e junte o certificado, por favor. |
        | rejected | rejeição  | Fora do prazo legal       | Mensagem de rejeição                                         |

    Scenario: A return is not undone once the requester resubmitted
      Given Rui Matos returned the request "AC-2026-0147" for "Preenchido incorretamente" with the message "Corrija a data de regresso e junte o certificado, por favor."
      And Ana Ferreira corrected "Regresso" to "09/11/2026 19:10" and attached "certificado_presenca.pdf", resubmitting the request "AC-2026-0147" as version 2
      When he undoes his decision
      Then he is told «Já não é possível desfazer: o pedido já foi tratado no passo «Pedido».»

    Scenario: Only the reviewer who decided can undo
      Given Rui Matos rejected the request "AC-2026-0147" for "Fora do prazo legal" with the message "Mensagem de rejeição"
      When Segunda Chefia undoes his decision
      Then she is not allowed to

    Scenario: Undoing gives the notes back to the reviewer
      Given Rui Matos left the note "Falta o certificado de presença com o carimbo da entidade formadora." on "Datas do certificado"
      And Rui Matos returned the request "AC-2026-0147" for "Preenchido incorretamente" with the message "Corrija a data de regresso e junte o certificado, por favor."
      When he undoes his decision
      Then his note on "Datas do certificado" can be changed again

    @browser
    Scenario Outline: The notice offers «Desfazer», which goes back to the request
      Given on his screen Rui Matos <decided> the request "AC-2026-0147" for "<reason>" with the message "<message>"
      Then his inbox still shows the notice «<notice>» with «Desfazer»
      When he chooses «Desfazer»
      Then he is reviewing the request "AC-2026-0147" again, with the notice «<undone>»

      Examples:
        | decided  | reason                    | message                                                      | notice                         | undone             |
        | returned | Preenchido incorretamente | Corrija a data de regresso e junte o certificado, por favor. | Pedido devolvido para correção | Devolução desfeita |
        | rejected | Fora do prazo legal       | Mensagem de rejeição                                         | Pedido rejeitado               | Rejeição desfeita  |

  Rule: The chosen reasons appear in the history, in the notice and in what the requester sees
    # Decisions with Pedro (2026-11-23): the history also shows the message and counts the notes, and
    # the reasons are visible in these two notices. The requester sees them on the request's page;
    # «Corrigir o pedido» and the correction screen are UC20 (Dinis). The others read the history.

    Scenario: A return in the history
      Given Rui Matos left the note "Falta o certificado de presença com o carimbo da entidade formadora." on "Datas do certificado"
      When Rui Matos returns the request "AC-2026-0147" for "Preenchido incorretamente; Falta documento comprovativo" with the message "Corrija a data de regresso e junte o certificado, por favor."
      Then the newest entry of its history is "Rui Matos, Chefe de Divisão, devolveu para correção: Preenchido incorretamente; Falta documento comprovativo com 1 nota", with the message "Corrija a data de regresso e junte o certificado, por favor."

    Scenario: A rejection in the history
      When Rui Matos rejects the request "AC-2026-0147" for "Fora do prazo legal" with the message "Mensagem de rejeição"
      Then the newest entry of its history is "Rui Matos, Chefe de Divisão, rejeitou o pedido: Fora do prazo legal", with the message "Mensagem de rejeição"

    Scenario: A rejection in the history counts the notes
      Given Rui Matos left the note "Falta o certificado de presença com o carimbo da entidade formadora." on "Datas do certificado"
      When Rui Matos rejects the request "AC-2026-0147" for "Fora do prazo legal" with the message "Mensagem de rejeição"
      Then the newest entry of its history is "Rui Matos, Chefe de Divisão, rejeitou o pedido: Fora do prazo legal com 1 nota", with the message "Mensagem de rejeição"

    @browser
    Scenario: The notice shows the reasons
      When on his screen Rui Matos returns the request "AC-2026-0147" for "Preenchido incorretamente; Falta documento comprovativo" with the message "Corrija a data de regresso e junte o certificado, por favor."
      Then the notice shows «Motivos: Preenchido incorretamente; Falta documento comprovativo.»

    @browser
    Scenario Outline: The requester sees the reasons and the message on the request
      Given Rui Matos <decided> the request "AC-2026-0147" for "<reason>" with the message "<message>"
      When Ana Ferreira opens her request "AC-2026-0147"
      Then she reads «<title>», with "<reason>" and "<message>"

      Examples:
        | decided  | reason                    | message                                                      | title                                                            |
        | returned | Preenchido incorretamente | Corrija a data de regresso e junte o certificado, por favor. | Devolvido para correção por Rui Matos, Chefe de Divisão · motivo: |
        | rejected | Fora do prazo legal       | Mensagem de rejeição                                         | Rejeitado por Rui Matos, Chefe de Divisão · motivo:               |

    @browser
    Scenario Outline: «Estado» says who decided
      Given Rui Matos <decided> the request "AC-2026-0147" for "<reason>" with the message "<message>"
      When Ana Ferreira opens her request "AC-2026-0147"
      Then «Estado» reads «<state>»

      Examples:
        | decided  | reason                    | message                                                      | state                                                          |
        | returned | Preenchido incorretamente | Corrija a data de regresso e junte o certificado, por favor. | Devolvido para correção por Rui Matos, Chefe de Divisão.        |
        | rejected | Fora do prazo legal       | Mensagem de rejeição                                         | Rejeitado por Rui Matos, Chefe de Divisão: Fora do prazo legal. |
