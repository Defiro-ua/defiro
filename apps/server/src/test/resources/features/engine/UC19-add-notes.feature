# Draft for team review: the examples come from the acceptance criteria of issue #35, the
# verification session with Pedro (2026-11-22), and the web prototype and mockup (people, request
# AC-2026-0147 and Rui Matos's two notes on it). No Example Mapping was run yet.
#
# Stand-ins, as in UC17: UC13 (the request is created by a test step), UC15 (temporary file store),
# UC27 (minimal inbox). A reviewer's notes are the document of their review step (team diagram).
# Rules 4 and 5 need «Devolver para correção» (UC18): they stay @wip until UC18 is merged.
# open question (Dinis, Candeias): a note is on a field of the request ({field} of the answers) or
#                on a file. Does that key fit the form fields of UC04 and the correction of UC20?
@UC19 @wip
Feature: Add notes to a document
  As a Reviewer,
  I want to leave a note on the exact field that is wrong,
  so that the requester knows what to fix and where.

  # The same people, path and request as UC17 (see the comments there), with the mockup's
  # «Datas do certificado», whose relative dates are fixed from 22/11/2026 like the others.
  Background:
    Given the organization's Entra ID has these accounts
      | name           | unit                        | job title                     | manager      | groups                                                       |
      | Ana Ferreira   | Divisão de Obras Municipais | Técnica Superior              | Rui Matos    | Todos os trabalhadores, Divisão de Obras Municipais          |
      | Rui Matos      | Divisão de Obras Municipais | Chefe de Divisão              | Helena Costa | Todos os trabalhadores, Chefias, Divisão de Obras Municipais |
      | Segunda Chefia | Divisão de Obras Municipais | Chefe de Divisão              | Helena Costa | Todos os trabalhadores, Chefias, Divisão de Obras Municipais |
      | Carla Dias     | Divisão Financeira          | Técnica da Divisão Financeira | Rui Matos    | Todos os trabalhadores, Divisão Financeira                   |
    And the workflow "Ajudas de custo" has the path
      | step                   | handled by                       |
      | Pedido                 | Todos os trabalhadores           |
      | Validação da chefia    | Chefias da unidade do requerente |
      | Verificação financeira | Divisão Financeira               |
      | Validação final        | RH · Formação                    |
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

  Rule: The reviewer can add a note to any field the requester filled in
    # Every answer of «Dados do pedido» and each file. The «Requerente» section comes from Entra ID
    # and takes no note. One note per field and reviewer: «Nota» becomes «Editar nota».

    Scenario: A note on an answer
      When Rui Matos leaves the note "Indicou 10/11/2026; o certificado de presença diz 09/11/2026." on "Regresso"
      Then his note on "Regresso" reads "Indicou 10/11/2026; o certificado de presença diz 09/11/2026."

    Scenario: A note on a file
      # The text is the mockup's other note, only as an example: what matters is where the note is.
      When Rui Matos leaves the note "Falta o certificado de presença com o carimbo da entidade formadora." on the file "fatura_hotel.pdf"
      Then his note on the file "fatura_hotel.pdf" reads "Falta o certificado de presença com o carimbo da entidade formadora."

    Scenario: What comes from Entra ID takes no note
      When Rui Matos leaves the note "Indicou 10/11/2026; o certificado de presença diz 09/11/2026." on "Unidade orgânica"
      Then the note is refused

    Scenario: Only who took the task leaves notes
      When Segunda Chefia leaves the note "Indicou 10/11/2026; o certificado de presença diz 09/11/2026." on "Regresso"
      Then she is not allowed to

    @browser
    Scenario: The decision counts the reviewer's notes
      Given Rui Matos left the note "Indicou 10/11/2026; o certificado de presença diz 09/11/2026." on "Regresso"
      When he reviews the request "AC-2026-0147"
      Then his decision reads «Deixou 1 nota. Se devolver, as notas vão para Ana junto dos campos.»

  Rule: The note is mandatory: saving an empty note shows an error instead of discarding it
    # A note is at most 1000 characters: the box takes no more, and the server refuses a longer one.

    Scenario: The server refuses an empty note
      When Rui Matos leaves the note "" on "Regresso"
      Then the note is refused
      And "Regresso" has no note of his

    Scenario: The server refuses a note longer than 1000 characters
      When Rui Matos leaves a note of 1001 characters on "Regresso"
      Then the note is refused

    @browser
    Scenario: Saving an empty note shows the error, next to the note and in a notice
      When on his screen Rui Matos saves an empty note on "Regresso"
      Then he sees «Escreva a nota antes de guardar. Se já não precisa dela, carregue em «Cancelar».» next to the note and in a notice
      And the note stays open for him to write or cancel

    @browser
    Scenario: The note box counts and stops at 1000 characters
      When Rui Matos types 1001 characters in the note on "Regresso"
      Then the box keeps 1000 of them, with «1000 de 1000 caracteres»

  Rule: Notes can be edited and deleted before deciding, each change with a short notice
    # Deleting asks first, next to the note, with no dialog over the page. Once the reviewer has
    # decided, the notes no longer change.

    Background:
      Given Rui Matos left the note "Indicou 10/11/2026; o certificado de presença diz 09/11/2026." on "Regresso"

    Scenario: Editing a note
      When Rui Matos changes his note on "Regresso" to "Falta o certificado de presença com o carimbo da entidade formadora."
      Then his note on "Regresso" reads "Falta o certificado de presença com o carimbo da entidade formadora."

    Scenario: Deleting a note
      When Rui Matos deletes his note on "Regresso"
      Then "Regresso" has no note of his

    Scenario: Notes no longer change once the reviewer has decided
      Given Rui Matos approved the request "AC-2026-0147"
      When Rui Matos changes his note on "Regresso" to "Falta o certificado de presença com o carimbo da entidade formadora."
      Then he is not allowed to

    @browser
    Scenario: Saving a note shows «Nota guardada»
      When on his screen Rui Matos leaves the note "Falta o certificado de presença com o carimbo da entidade formadora." on "Datas do certificado"
      Then he sees the notice «Nota guardada»

    @browser
    Scenario: Changing a note shows «Nota alterada»
      When on his screen Rui Matos changes his note on "Regresso" to "Falta o certificado de presença com o carimbo da entidade formadora."
      Then he sees the notice «Nota alterada»

    @browser
    Scenario: Deleting a note, once confirmed, shows «Nota apagada»
      When on his screen Rui Matos deletes his note on "Regresso", confirming
      Then he sees the notice «Nota apagada»

    @browser
    Scenario: Deleting a note asks first
      When Rui Matos chooses «Apagar» on his note on "Regresso"
      Then he is asked «Apagar a nota sobre «Regresso»? Ana Ferreira deixa de a ver se devolver o pedido.», with «Apagar a nota» and «Cancelar»

  Rule: Until the reviewer decides, their notes are theirs alone
    # Decision with Pedro (2026-11-22). Releasing the task discards them.

    Background:
      Given Rui Matos left the note "Indicou 10/11/2026; o certificado de presença diz 09/11/2026." on "Regresso"

    Scenario: Another candidate does not see them
      When Segunda Chefia opens the request "AC-2026-0147"
      Then she sees no note on "Regresso"

    Scenario: Releasing the task discards them
      When he releases the task
      Then "Regresso" has no note of his

  Rule: Approving sends the notes on to the next reviewer
    # Decision with Pedro (2026-11-22), as in the prototype. «Desfazer» gives them back to the
    # reviewer, to change before deciding again.

    Background:
      Given Rui Matos left the note "Indicou 10/11/2026; o certificado de presença diz 09/11/2026." on "Regresso"
      And Rui Matos approved the request "AC-2026-0147"

    Scenario: The next reviewer sees them next to the fields
      When Carla Dias opens the request "AC-2026-0147"
      Then she sees next to "Regresso" «Nota de Rui Matos, Chefe de Divisão: Indicou 10/11/2026; o certificado de presença diz 09/11/2026.»

    Scenario: The history counts them
      When Ana Ferreira opens the request "AC-2026-0147"
      Then its history shows "Rui Matos, Chefe de Divisão, aprovou o passo «Validação da chefia» com 1 nota"

    Scenario: The requester does not see them while the request goes on
      When Ana Ferreira opens the request "AC-2026-0147"
      Then she sees no note on "Regresso"

    Scenario: «Desfazer» gives them back to the reviewer
      When he undoes his approval
      Then his note on "Regresso" can be changed again
      And Carla Dias no longer sees it

  # Needs «Devolver para correção» (UC18): it turns on with UC18.
  @wip
  Rule: Notes go to the requester together with a return
    # Only the notes of who returns the request: the notes that followed an approval stay between
    # reviewers. A rejection sends no notes: the requester sees its reasons and message (UC18).

    Background:
      Given Rui Matos left the note "Falta o certificado de presença com o carimbo da entidade formadora." on "Datas do certificado"

    Scenario: The returned request carries the reviewer's notes
      When Rui Matos returns the request "AC-2026-0147" for "Preenchido incorretamente"
      Then Ana Ferreira gets the note on "Datas do certificado"

    Scenario: A rejected request carries no notes
      When Rui Matos rejects the request "AC-2026-0147"
      Then Ana Ferreira gets no notes

  # Needs «Devolver para correção» (UC18): it turns on with UC18.
  @wip
  Rule: The requester sees each note next to its field, with the reviewer's name and job title
    # On the returned request. The correction screen with «Ir para o campo» is UC20 (Dinis).

    @browser
    Scenario: Ana reads Rui's note next to the field
      Given Rui Matos returned the request "AC-2026-0147" for "Preenchido incorretamente", with the note "Falta o certificado de presença com o carimbo da entidade formadora." on "Datas do certificado"
      When Ana Ferreira opens her request "AC-2026-0147"
      Then next to "Datas do certificado" she reads «Nota de Rui Matos, Chefe de Divisão: Falta o certificado de presença com o carimbo da entidade formadora.»
