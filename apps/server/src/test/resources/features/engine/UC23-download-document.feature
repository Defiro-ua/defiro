# language: pt
# Exemplos: critérios de aceitação de UC23, diagrama de sequência («5 · Download») e protótipo web
# (pedido AC-2026-0098, já fechado, e DocumentDialog). Desenho: docs/uc23-download-document-design.md.
# @wip até a UC22 guardar o documento e os passos existirem.
@UC23 @wip
Funcionalidade: Transferir um documento gerado
  Como participante de um workflow,
  quero ver e transferir em PDF o documento final de um pedido fechado,
  para que a Divisão Financeira o possa usar fora da defiro.

  # Pessoas: as personas do protótipo. As datas do protótipo são relativas ao dia em que é aberto
  #   (fechado 41 dias antes, às 15:30); aqui estão fixadas a partir de 27/11/2026.
  Contexto:
    Dado que o Entra ID da organização tem estas contas
      | nome          | unidade                     | cargo                         | chefia       | grupos                                                       |
      | Ana Ferreira  | Divisão de Obras Municipais | Técnica Superior              | Rui Matos    | Todos os trabalhadores, Divisão de Obras Municipais          |
      | Rui Matos     | Divisão de Obras Municipais | Chefe de Divisão              | Helena Costa | Todos os trabalhadores, Chefias, Divisão de Obras Municipais |
      | Carla Dias    | Divisão Financeira          | Técnica da Divisão Financeira | Rui Matos    | Todos os trabalhadores, Divisão Financeira                   |
      | Joana Pires   | Recursos Humanos            | Técnica de Recursos Humanos   | Rui Matos    | Todos os trabalhadores, Recursos Humanos, RH · Formação      |
      | Sofia Almeida | Modernização Administrativa | Gestora de workflows          | Rui Matos    | Todos os trabalhadores                                       |
    E o workflow "Ajudas de custo" tem o percurso
      | passo                  | tratado por                      |
      | Pedido                 | Todos os trabalhadores           |
      | Validação da chefia    | Chefias da unidade do requerente |
      | Verificação financeira | Divisão Financeira               |
      | Validação final        | RH · Formação                    |
    E o pedido "AC-2026-0098" de Ana Ferreira para "Ajudas de custo" foi aprovado por Rui Matos e Carla Dias
    E Joana Pires validou-o e fechou-o a 17/10/2026 às 15:30
    E ao fechar foi gerada a ordem de reembolso "ordem_reembolso_AC-2026-0098.pdf"

  Regra: Com o procedimento fechado, o requerente abre o documento final em «Os meus pedidos»

    @browser
    Cenário: Um pedido fechado oferece o seu documento em «Os meus pedidos»
      Quando Ana Ferreira abre «Os meus pedidos»
      Então o pedido "AC-2026-0098" mostra "Fechado a 17/10/2026"
      E oferece «Documento»

    @browser
    Cenário: Um pedido que não está fechado não oferece documento
      Dado que o pedido "AC-2026-0147" de Ana Ferreira está em «Verificação financeira»
      Quando Ana Ferreira abre «Os meus pedidos»
      Então o pedido "AC-2026-0147" não oferece «Documento»

    Cenário: Um procedimento que não está fechado não tem documento
      Dado que o pedido "AC-2026-0147" de Ana Ferreira está em «Verificação financeira»
      Quando Ana Ferreira pede o documento de "AC-2026-0147"
      Então não há documento

    @browser
    Cenário: Quem valida abre o documento a partir do aviso de fecho
      Dado que o pedido "AC-2026-0147" de Ana Ferreira está em «Validação final»
      Quando Joana Pires o valida e fecha
      Então vê "Procedimento fechado" com «Ver documento»
      E «Ver documento» abre o documento de "AC-2026-0147"

  Regra: O documento abre no ecrã, com «Fechar» e «Transferir PDF» sempre visíveis no topo
    # O ecrã mostra o próprio PDF, tal como foi guardado ao fechar. Se o dispositivo não consegue
    #   mostrar um PDF no ecrã, não há pré-visualização e o PDF é logo transferido.

    @browser
    Cenário: O documento abre no ecrã
      Quando Ana Ferreira abre o documento de "AC-2026-0098" em «Os meus pedidos»
      Então vê no ecrã o PDF "ordem_reembolso_AC-2026-0098.pdf"
      E vê «Fechar» e «Transferir PDF» no topo

    @browser
    Esquema do Cenário: «Fechar» e «Transferir PDF» ficam visíveis em qualquer tamanho de ecrã
      Dado que o ecrã de Ana Ferreira tem <largura> píxeis de largura
      Quando ela abre o documento de "AC-2026-0098" e desce até ao fim
      Então continua a ver «Fechar» e «Transferir PDF»

      Exemplos:
        | largura |
        | 360     |
        | 768     |
        | 1440    |

    @browser
    Cenário: «Fechar» fecha o documento e volta ao sítio de onde foi aberto
      Dado que Ana Ferreira abriu o documento de "AC-2026-0098" em «Os meus pedidos»
      Quando escolhe «Fechar»
      Então volta a «Os meus pedidos», em «Documento» de "AC-2026-0098"

    @browser
    Cenário: Um dispositivo que não mostra o PDF transfere-o
      Dado que o navegador de Ana Ferreira não consegue mostrar um PDF no ecrã
      Quando ela abre o documento de "AC-2026-0098" em «Os meus pedidos»
      Então "ordem_reembolso_AC-2026-0098.pdf" é transferido
      E é-lhe dito que foi guardado

  Regra: «Transferir PDF» transfere o documento em PDF e confirma no ecrã que foi guardado

    Cenário: O documento é o PDF gerado quando o procedimento foi fechado
      Quando Ana Ferreira pede o documento de "AC-2026-0098"
      Então recebe "ordem_reembolso_AC-2026-0098.pdf" como "application/pdf"
      E é a ordem de reembolso gerada quando o procedimento foi fechado

    @browser
    Cenário: «Transferir PDF» guarda o PDF e di-lo
      Dado que Ana Ferreira abriu o documento de "AC-2026-0098"
      Quando escolhe «Transferir PDF»
      Então "ordem_reembolso_AC-2026-0098.pdf" é transferido
      E é-lhe dito que foi guardado

  Regra: O documento mostra cada pessoa com o nome e o cargo
    # Tal como eram quando o procedimento foi fechado: o PDF é gerado uma vez e nunca mais.

    Cenário: Cada pessoa no documento tem o nome e o cargo
      Quando Ana Ferreira recebe o documento de "AC-2026-0098"
      Então mostra "Requerente" como "Ana Ferreira (Técnica Superior)"
      E mostra "Validado por" como "Joana Pires (Técnica de Recursos Humanos) a 17/10/2026"

    Cenário: Uma mudança de cargo mais tarde não muda o documento
      Dado que o cargo de Ana Ferreira passou a "Técnica Superior Especialista"
      Quando Ana Ferreira recebe o documento de "AC-2026-0098"
      Então mostra "Requerente" como "Ana Ferreira (Técnica Superior)"

  Regra: Só os participantes do procedimento abrem o seu documento
    # Participantes, no MVP: o requerente e quem fechou o procedimento.

    Cenário: Quem fechou o procedimento recebe o documento
      Quando Joana Pires pede o documento de "AC-2026-0098"
      Então recebe "ordem_reembolso_AC-2026-0098.pdf"

    Esquema do Cenário: Mais ninguém recebe o documento, mesmo com o endereço
      Quando <pessoa> pede o documento de "AC-2026-0098"
      Então é recusado

      Exemplos:
        | pessoa        |
        | Sofia Almeida |
        | Rui Matos     |
        | Carla Dias    |

    Cenário: Sem iniciar sessão não se pede o documento
      Quando alguém sem sessão iniciada pede o documento de "AC-2026-0098"
      Então é-lhe pedido que inicie sessão
