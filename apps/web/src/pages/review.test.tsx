import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Outlet, Route, Routes, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../api/engine'
import type { ReviewRequest } from '../api/engine'
import type { Profile } from '../api/session'
import { InboxPage } from './InboxPage'
import { RequestPage } from './RequestPage'

vi.mock('../api/engine', async (original) => ({
  ...(await original<typeof import('../api/engine')>()),
  listInbox: vi.fn(),
  listMyRequests: vi.fn(),
  openRequest: vi.fn(),
  approve: vi.fn(),
  take: vi.fn(),
  release: vi.fn(),
  saveNote: vi.fn(),
  deleteNote: vi.fn(),
  undoDecision: vi.fn(),
  returnToRequester: vi.fn(),
  reject: vi.fn(),
  fetchDocument: vi.fn(),
}))

// The mockup's people and request AC-2026-0147
const rui: Profile = {
  name: 'Rui Matos',
  unit: 'Divisão de Obras Municipais',
  jobTitle: 'Chefe de Divisão',
  manager: 'Helena Costa',
  menu: ['new-request', 'inbox'],
}

const request: ReviewRequest = {
  reference: 'AC-2026-0147',
  workflow: 'Ajudas de custo',
  status: 'in_progress',
  currentStep: 's2',
  path: [
    { id: 's1', name: 'Pedido', handledBy: 'Todos os trabalhadores', returnReasons: [], rejectReasons: [] },
    {
      id: 's2',
      name: 'Validação da chefia',
      handledBy: 'Chefias da unidade do requerente',
      returnReasons: ['Preenchido incorretamente', 'Falta documento comprovativo'],
      rejectReasons: ['Deslocação não autorizada', 'Fora do prazo legal'],
    },
    {
      id: 's3',
      name: 'Verificação financeira',
      handledBy: 'Divisão Financeira',
      returnReasons: ['Preenchido incorretamente', 'Falta documento comprovativo'],
      rejectReasons: ['Despesa não elegível'],
    },
    {
      id: 's4',
      name: 'Validação final',
      handledBy: 'RH · Formação',
      returnReasons: ['Datas do certificado não coincidem'],
      rejectReasons: ['Fora do prazo legal'],
    },
  ],
  version: 1,
  requester: { name: 'Ana Ferreira', jobTitle: 'Técnica Superior', unit: 'Divisão de Obras Municipais', manager: 'Rui Matos' },
  answers: [
    { field: 'destino', label: 'Destino', value: 'Lisboa', changed: false, previous: null },
    { field: 'regresso', label: 'Regresso', value: '10/11/2026 19:40', changed: false, previous: null },
  ],
  files: [{ id: 'f1', filename: 'bilhete_comboio.pdf', version: 1 }],
  history: [{ at: '2026-11-18T16:02:00Z', text: 'Ana Ferreira, Técnica Superior, submeteu o pedido', message: null }],
  task: 't1',
  reviewer: 'Rui Matos, Chefe de Divisão',
  notes: [],
  notesFrom: [],
  decision: null,
  mine: false,
}

const waiting: api.InboxItem = {
  task: 't1',
  reference: 'AC-2026-0147',
  requester: 'Ana Ferreira',
  step: 'Validação da chefia',
  reviewer: null,
  mine: false,
}

const approved: api.Approved = {
  task: 't1',
  reference: 'AC-2026-0147',
  nextStep: 'Verificação financeira',
  handledBy: 'Divisão Financeira',
  nextTask: 't2',
}

// What a reload or coming back would find in the history
function HistoryState() {
  return <p data-testid="history-state">{JSON.stringify(useLocation().state)}</p>
}

function openAt(pathname: string, state?: unknown) {
  render(
    <MemoryRouter initialEntries={[{ pathname, state }]}>
      <Routes>
        <Route element={<Outlet context={rui} />}>
          <Route path="/caixa-de-tarefas" element={<InboxPage />} />
          <Route path="/pedidos/:reference" element={<RequestPage />} />
        </Route>
      </Routes>
      <HistoryState />
    </MemoryRouter>,
  )
}

// jsdom has neither a modal <dialog> nor blob URLs
beforeEach(() => {
  vi.mocked(api.listMyRequests).mockResolvedValue([])
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
    this.removeAttribute('open')
    this.dispatchEvent(new Event('close'))
  }
  URL.createObjectURL = vi.fn(() => 'blob:http://localhost/0147')
  URL.revokeObjectURL = vi.fn()
})
afterEach(() => vi.resetAllMocks())

describe('the inbox (until UC27)', () => {
  it('lists the requests waiting for the person; «Rever» takes the task and opens the request', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([waiting])
    vi.mocked(api.take).mockResolvedValue({ done: true, result: { task: 't1', reference: 'AC-2026-0147' } })
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    openAt('/caixa-de-tarefas')

    expect(await screen.findByRole('heading', { level: 2, name: 'Tarefas à sua espera · 1' })).toBeInTheDocument()
    expect(screen.getByText('AC-2026-0147 · Validação da chefia')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^Rever\s*o pedido AC-2026-0147 de Ana Ferreira$/ }))

    expect(api.take).toHaveBeenCalledWith('t1')
    expect(await screen.findByRole('heading', { level: 1, name: 'Ajudas de custo · Ana Ferreira' })).toBeInTheDocument()
  })

  it('when another candidate took the request first, «Rever» opens it to look only', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([waiting])
    vi.mocked(api.take).mockResolvedValue({ done: false, reason: 'Em revisão por Segunda Chefia, Chefe de Divisão.' })
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request: { ...request, task: null, reviewer: 'Segunda Chefia, Chefe de Divisão' } })
    openAt('/caixa-de-tarefas')

    await userEvent.click(await screen.findByRole('button', { name: /^Rever/ }))

    expect(await screen.findByText('Em revisão por Segunda Chefia, Chefe de Divisão.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Aprovar' })).not.toBeInTheDocument()
  })

  it('marks a request another candidate took, with «Ver» only', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([{ ...waiting, reviewer: 'Rui Matos, Chefe de Divisão' }])
    openAt('/caixa-de-tarefas')

    expect(await screen.findByText('Em revisão por Rui Matos, Chefe de Divisão')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /^Ver\s*o pedido AC-2026-0147 de Ana Ferreira$/ })).toHaveAttribute('href', '/pedidos/AC-2026-0147')
    expect(screen.queryByRole('button', { name: /^Rever/ })).not.toBeInTheDocument()
  })

  it('keeps «Rever» on a request the person took themselves', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([{ ...waiting, reviewer: 'Rui Matos, Chefe de Divisão', mine: true }])
    openAt('/caixa-de-tarefas')

    expect(await screen.findByRole('button', { name: /^Rever/ })).toBeInTheDocument()
    expect(screen.queryByText(/^Em revisão por/)).not.toBeInTheDocument()
  })

  it('says so when the task cannot be taken', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([waiting])
    vi.mocked(api.take).mockRejectedValue(new Error('POST answered 500'))
    openAt('/caixa-de-tarefas')

    await userEvent.click(await screen.findByRole('button', { name: /^Rever/ }))

    expect(await screen.findByText('Não foi possível abrir a tarefa')).toBeInTheDocument()
  })

  it('says when nothing is waiting', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([])
    openAt('/caixa-de-tarefas')

    expect(await screen.findByText(/Não tem tarefas à sua espera/)).toBeInTheDocument()
  })

  it('says so when the tasks cannot be read', async () => {
    vi.mocked(api.listInbox).mockRejectedValue(new Error('GET /api/engine/inbox answered 500'))
    openAt('/caixa-de-tarefas')

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível ler as tarefas')
  })

  it('after approving, «Desfazer» goes back to the request with «Aprovação desfeita»', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([])
    vi.mocked(api.undoDecision).mockResolvedValue({
      done: true,
      result: { task: 't1', reference: 'AC-2026-0147', step: 'Validação da chefia', decision: 'approved' },
    })
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    openAt('/caixa-de-tarefas', { notice: { titulo: 'Pedido aprovado' }, undo: { task: 't1', reference: 'AC-2026-0147' } })

    await userEvent.click(await screen.findByRole('button', { name: 'Desfazer' }))

    expect(api.undoDecision).toHaveBeenCalledWith('t1')
    expect(await screen.findByRole('heading', { level: 1, name: 'Ajudas de custo · Ana Ferreira' })).toBeInTheDocument()
    expect(await screen.findByText('Aprovação desfeita')).toBeInTheDocument()
  })

  it('takes the notice and «Desfazer» out of the history, so a reload or coming back does not offer them again', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([])
    openAt('/caixa-de-tarefas', { notice: { titulo: 'Pedido aprovado' }, undo: { task: 't1', reference: 'AC-2026-0147' } })

    expect(await screen.findByRole('button', { name: 'Desfazer' })).toBeInTheDocument()
    expect(screen.getByTestId('history-state')).toHaveTextContent('null')
  })

  it('undoes once, however many times «Desfazer» is clicked', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([])
    vi.mocked(api.undoDecision).mockReturnValue(new Promise(() => {}))
    openAt('/caixa-de-tarefas', { notice: { titulo: 'Pedido aprovado' }, undo: { task: 't1', reference: 'AC-2026-0147' } })

    const undo = await screen.findByRole('button', { name: 'Desfazer' })
    await userEvent.click(undo)
    await userEvent.click(undo)

    expect(api.undoDecision).toHaveBeenCalledOnce()
  })

  it('when it is too late to undo, says why and offers «Desfazer» no more', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([])
    const reason = 'Já não é possível desfazer: o pedido já foi tratado no passo «Verificação financeira».'
    vi.mocked(api.undoDecision).mockResolvedValue({ done: false, reason })
    openAt('/caixa-de-tarefas', { notice: { titulo: 'Pedido aprovado' }, undo: { task: 't1', reference: 'AC-2026-0147' } })

    await userEvent.click(await screen.findByRole('button', { name: 'Desfazer' }))

    expect(await screen.findByText(reason)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Desfazer' })).not.toBeInTheDocument()
  })

  it('says so when undoing fails', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([])
    vi.mocked(api.undoDecision).mockRejectedValue(new Error('POST answered 500'))
    openAt('/caixa-de-tarefas', { notice: { titulo: 'Pedido aprovado' }, undo: { task: 't1', reference: 'AC-2026-0147' } })

    await userEvent.click(await screen.findByRole('button', { name: 'Desfazer' }))

    expect(await screen.findByText('Não foi possível desfazer')).toBeInTheDocument()
  })
})

describe('the notes on the fields (UC19)', () => {
  const note = 'Indicou 10/11/2026; o certificado de presença diz 09/11/2026.'
  const other = 'Falta o certificado de presença com o carimbo da entidade formadora.'

  it('saves a new note on a field, with «Nota guardada», and counts it in the decision', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    vi.mocked(api.saveNote).mockResolvedValue({ field: 'regresso', label: 'Regresso', text: note })
    openAt('/pedidos/AC-2026-0147')

    expect(await screen.findByText(/^Ainda não deixou notas\./)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^Nota\s*sobre Regresso$/ }))
    const box = screen.getByRole('textbox', { name: /^Nota sobre «Regresso»/ })
    expect(box).toHaveAttribute('maxlength', '1000')
    await userEvent.type(box, note)
    expect(screen.getByText(`${note.length} de 1000 caracteres`)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Guardar nota' }))

    expect(api.saveNote).toHaveBeenCalledWith('t1', 'regresso', note)
    expect(await screen.findByText('Nota guardada')).toBeInTheDocument()
    expect(screen.getByText(note)).toBeInTheDocument()
    expect(screen.getByText('Deixou 1 nota. Se devolver, as notas vão para Ana junto dos campos.')).toBeInTheDocument()
  })

  it('does not save an empty note: the error shows next to it and in a notice, and the box stays open', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    openAt('/pedidos/AC-2026-0147')

    await userEvent.click(await screen.findByRole('button', { name: /^Nota\s*sobre Regresso$/ }))
    await userEvent.type(screen.getByRole('textbox', { name: /^Nota sobre «Regresso»/ }), '   ')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar nota' }))

    const error = 'Escreva a nota antes de guardar. Se já não precisa dela, carregue em «Cancelar».'
    expect(within(screen.getByRole('alert')).getByText(error)).toBeInTheDocument()
    // and, once the notice is up, at the top too
    await waitFor(() => expect(screen.getAllByText(error)).toHaveLength(2))
    expect(screen.getByRole('textbox', { name: /^Nota sobre «Regresso»/ })).toBeInTheDocument()
    expect(api.saveNote).not.toHaveBeenCalled()
  })

  it('changes a note, with «Nota alterada»', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request: { ...request, notes: [{ field: 'regresso', label: 'Regresso', text: note }] } })
    vi.mocked(api.saveNote).mockResolvedValue({ field: 'regresso', label: 'Regresso', text: other })
    openAt('/pedidos/AC-2026-0147')

    await userEvent.click(await screen.findByRole('button', { name: /^Editar nota\s*sobre Regresso$/ }))
    const box = screen.getByRole('textbox', { name: /^Nota sobre «Regresso»/ })
    await userEvent.clear(box)
    await userEvent.type(box, other)
    await userEvent.click(screen.getByRole('button', { name: 'Guardar nota' }))

    expect(await screen.findByText('Nota alterada')).toBeInTheDocument()
    expect(screen.getByText(other)).toBeInTheDocument()
  })

  it('cancels the editor, keeping the note as it was', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request: { ...request, notes: [{ field: 'regresso', label: 'Regresso', text: note }] } })
    openAt('/pedidos/AC-2026-0147')

    await userEvent.click(await screen.findByRole('button', { name: /^Editar nota/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.getByText(note)).toBeInTheDocument()
  })

  it('asks before deleting a note, naming the requester, then deletes it with «Nota apagada»', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request: { ...request, notes: [{ field: 'regresso', label: 'Regresso', text: note }] } })
    vi.mocked(api.deleteNote).mockResolvedValue()
    openAt('/pedidos/AC-2026-0147')

    await userEvent.click(await screen.findByRole('button', { name: /^Apagar\s*a nota sobre Regresso$/ }))
    expect(screen.getByText('Apagar a nota sobre «Regresso»? Ana Ferreira deixa de a ver se devolver o pedido.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Apagar a nota' }))

    expect(api.deleteNote).toHaveBeenCalledWith('t1', 'regresso')
    expect(await screen.findByText('Nota apagada')).toBeInTheDocument()
    expect(screen.queryByText(note)).not.toBeInTheDocument()
  })

  it('keeps the note when the deletion is cancelled', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request: { ...request, notes: [{ field: 'regresso', label: 'Regresso', text: note }] } })
    openAt('/pedidos/AC-2026-0147')

    await userEvent.click(await screen.findByRole('button', { name: /^Apagar\s*a nota/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(api.deleteNote).not.toHaveBeenCalled()
    expect(screen.getByText(note)).toBeInTheDocument()
  })

  it('says so when a note cannot be saved or deleted', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request: { ...request, notes: [{ field: 'regresso', label: 'Regresso', text: note }] } })
    vi.mocked(api.saveNote).mockRejectedValue(new Error('PUT answered 500'))
    vi.mocked(api.deleteNote).mockRejectedValue(new Error('DELETE answered 500'))
    openAt('/pedidos/AC-2026-0147')

    await userEvent.click(await screen.findByRole('button', { name: /^Editar nota/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar nota' }))
    expect(await screen.findByText('Não foi possível guardar a nota')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    await userEvent.click(screen.getByRole('button', { name: /^Apagar\s*a nota/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Apagar a nota' }))
    expect(await screen.findByText('Não foi possível apagar a nota')).toBeInTheDocument()
  })

  it('takes notes on each file too', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    vi.mocked(api.saveNote).mockResolvedValue({ field: 'file:f1', label: 'bilhete_comboio.pdf', text: other })
    openAt('/pedidos/AC-2026-0147')

    await userEvent.click(await screen.findByRole('button', { name: /^Nota\s*sobre bilhete_comboio\.pdf$/ }))
    await userEvent.type(screen.getByRole('textbox', { name: /^Nota sobre «bilhete_comboio\.pdf»/ }), other)
    await userEvent.click(screen.getByRole('button', { name: 'Guardar nota' }))

    expect(api.saveNote).toHaveBeenCalledWith('t1', 'file:f1', other)
  })

  it('shows the previous reviewer’s notes next to their fields, and no «Nota» to whoever only looks', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({
      found: true,
      request: { ...request, task: null, notesFrom: [{ field: 'regresso', label: 'Regresso', text: note, author: 'Rui Matos, Chefe de Divisão' }] },
    })
    openAt('/pedidos/AC-2026-0147')

    expect(await screen.findByText('Nota de Rui Matos, Chefe de Divisão:')).toBeInTheDocument()
    expect(screen.getByText(note)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Nota/ })).not.toBeInTheDocument()
  })
})

describe('«Rever pedido» (UC17)', () => {
  it('shows the request: its requester, data, files, path and history', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    openAt('/pedidos/AC-2026-0147')

    expect(await screen.findByRole('heading', { level: 1, name: 'Ajudas de custo · Ana Ferreira' })).toBeInTheDocument()
    const data = screen.getByRole('region', { name: 'Dados do pedido' })
    expect(within(data).getByText('Lisboa')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /^Ver\s*bilhete_comboio\.pdf$/ })).toHaveAttribute('href', '/api/engine/procedures/AC-2026-0147/files/f1')
    expect(screen.getByText('Passo 2 de 4: Validação da chefia.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Ver histórico' }))
    expect(screen.getByText('Ana Ferreira, Técnica Superior, submeteu o pedido')).toBeInTheDocument()
  })

  it('marks what changed in a resubmitted request', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({
      found: true,
      request: {
        ...request,
        version: 2,
        answers: [
          { field: 'destino', label: 'Destino', value: 'Lisboa', changed: false, previous: null },
          { field: 'regresso', label: 'Regresso', value: '09/11/2026 19:10', changed: true, previous: '10/11/2026 19:40' },
        ],
        files: [...request.files, { id: 'f3', filename: 'certificado_presenca.pdf', version: 2 }],
      },
    })
    openAt('/pedidos/AC-2026-0147')

    expect(await screen.findByText('Antes: 10/11/2026 19:40')).toBeInTheDocument()
    expect(screen.getAllByText('Alterado')).toHaveLength(1)
    expect(screen.getAllByText('Novo na versão 2')).toHaveLength(1)
  })

  it('ends the decision with «Aprovar», after the two that end the review', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    openAt('/pedidos/AC-2026-0147')

    const decision = await screen.findByRole('group', { name: 'Decisão' })
    const buttons = within(decision).getAllByRole('button')
    expect(buttons.map((b) => b.textContent)).toEqual(['Devolver para correção', 'Rejeitar o pedido', 'Aprovar'])
    buttons.forEach((b) => expect(b).toBeEnabled())
    expect(screen.getByText('Segue para o passo «Verificação financeira», com Divisão Financeira.')).toBeInTheDocument()
  })

  it('approves in one click, with no confirmation, and goes back to the inbox with «Desfazer»', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    vi.mocked(api.approve).mockResolvedValue({ done: true, result: approved })
    vi.mocked(api.listInbox).mockResolvedValue([])
    openAt('/pedidos/AC-2026-0147')

    await userEvent.click(await screen.findByRole('button', { name: 'Aprovar' }))

    expect(api.approve).toHaveBeenCalledWith('t1')
    expect(await screen.findByRole('heading', { level: 1, name: 'Caixa de tarefas' })).toBeInTheDocument()
    expect(await screen.findByText('Pedido aprovado')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Desfazer' })).toBeInTheDocument()
  })

  it('lastApprovalShowsClosedNotice: approving the last step opens the inbox with the closing notice and «Ver documento»', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    vi.mocked(api.approve).mockResolvedValue({ done: true, result: { ...approved, nextStep: null, handledBy: null, nextTask: null } })
    vi.mocked(api.listInbox).mockResolvedValue([])
    vi.mocked(api.fetchDocument).mockResolvedValue({ blob: new Blob(['%PDF-1.4'], { type: 'application/pdf' }), filename: 'ordem_reembolso_AC-2026-0147.pdf' })
    openAt('/pedidos/AC-2026-0147')

    await userEvent.click(await screen.findByRole('button', { name: 'Aprovar' }))

    expect(await screen.findByText('Procedimento fechado')).toBeInTheDocument()
    expect(screen.getByText(/AC-2026-0147: foi gerado o documento final\. Ana Ferreira \(Técnica Superior\) já o pode transferir\./)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Ver documento' }))

    expect(api.fetchDocument).toHaveBeenCalledWith('AC-2026-0147')
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
  })

  const closing = { notice: { titulo: 'Procedimento fechado' }, undo: { task: 't1', reference: 'AC-2026-0147' }, document: 'AC-2026-0147' }

  it('«PDF guardado» never carries «Desfazer»: saving the closing PDF leaves no undo of the approval', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([])
    vi.mocked(api.fetchDocument).mockResolvedValue({ blob: new Blob(['%PDF-1.4'], { type: 'application/pdf' }), filename: 'ordem_reembolso_AC-2026-0147.pdf' })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    openAt('/caixa-de-tarefas', closing)

    await userEvent.click(await screen.findByRole('button', { name: 'Ver documento' }))
    await userEvent.click(await screen.findByRole('button', { name: /^Transferir PDF/ }))

    expect(screen.getAllByText('PDF guardado').length).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: 'Desfazer' })).not.toBeInTheDocument()
  })

  it('with no PDF viewer, «Ver documento» downloads at once and leaves no «Desfazer»', async () => {
    Object.defineProperty(navigator, 'pdfViewerEnabled', { value: false, configurable: true })
    vi.mocked(api.listInbox).mockResolvedValue([])
    vi.mocked(api.fetchDocument).mockResolvedValue({ blob: new Blob(['%PDF-1.4'], { type: 'application/pdf' }), filename: 'ordem_reembolso_AC-2026-0147.pdf' })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    try {
      openAt('/caixa-de-tarefas', closing)

      await userEvent.click(await screen.findByRole('button', { name: 'Ver documento' }))

      expect(await screen.findByText('PDF guardado')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Desfazer' })).not.toBeInTheDocument()
    } finally {
      Object.defineProperty(navigator, 'pdfViewerEnabled', { value: true, configurable: true })
    }
  })

  it('a refused undo of the closing replaces the notice, and «Ver documento» goes with it', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([])
    vi.mocked(api.undoDecision).mockResolvedValue({ done: false, reason: 'Já não é possível desfazer.' })
    openAt('/caixa-de-tarefas', closing)

    await userEvent.click(await screen.findByRole('button', { name: 'Desfazer' }))

    expect(await screen.findByText('Já não é possível desfazer.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ver documento' })).not.toBeInTheDocument()
  })

  it('a failed undo of the closing does not keep «Ver documento» either', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([])
    vi.mocked(api.undoDecision).mockRejectedValue(new Error('POST answered 500'))
    openAt('/caixa-de-tarefas', closing)

    await userEvent.click(await screen.findByRole('button', { name: 'Desfazer' }))

    expect(await screen.findByText('Não foi possível desfazer')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ver documento' })).not.toBeInTheDocument()
  })

  it('after undoing the closing, the procedure is open again and offers no document', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([])
    vi.mocked(api.undoDecision).mockResolvedValue({
      done: true,
      result: { task: 't1', reference: 'AC-2026-0147', step: 'Validação final', decision: 'approved' },
    })
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    openAt('/caixa-de-tarefas', closing)

    await userEvent.click(await screen.findByRole('button', { name: 'Desfazer' }))

    expect(await screen.findByText('Aprovação desfeita')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ver documento' })).not.toBeInTheDocument()
  })

  it('when someone approved first, goes back to the inbox saying who, with no «Desfazer»', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    vi.mocked(api.approve).mockResolvedValue({ done: false, reason: 'Este pedido já foi tratado por Segunda Chefia, Chefe de Divisão.' })
    vi.mocked(api.listInbox).mockResolvedValue([])
    openAt('/pedidos/AC-2026-0147')

    await userEvent.click(await screen.findByRole('button', { name: 'Aprovar' }))

    expect(await screen.findByText('Este pedido já foi tratado por Segunda Chefia, Chefe de Divisão.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Desfazer' })).not.toBeInTheDocument()
  })

  it('says so when approving fails, and stays on the request', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    vi.mocked(api.approve).mockRejectedValue(new Error('POST answered 500'))
    openAt('/pedidos/AC-2026-0147')

    await userEvent.click(await screen.findByRole('button', { name: 'Aprovar' }))

    expect(await screen.findByText('Não foi possível aprovar')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Aprovar' })).toBeEnabled()
  })

  it('shows no decision to someone who may not decide it now, but who has it in review', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request: { ...request, task: null } })
    openAt('/pedidos/AC-2026-0147')

    expect(await screen.findByRole('heading', { level: 2, name: 'Estado' })).toBeInTheDocument()
    expect(screen.getByText('Em revisão por Rui Matos, Chefe de Divisão.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Aprovar' })).not.toBeInTheDocument()
  })

  it('shows where the request is to someone who looks while nobody has it in review', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request: { ...request, task: null, reviewer: null } })
    openAt('/pedidos/AC-2026-0147')

    expect(await screen.findByText('No passo «Validação da chefia», com Chefias da unidade do requerente.')).toBeInTheDocument()
  })

  it('«Libertar a tarefa» gives it back and goes to the inbox with «Tarefa libertada»', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    vi.mocked(api.release).mockResolvedValue({ done: true, result: { task: 't1', reference: 'AC-2026-0147' } })
    vi.mocked(api.listInbox).mockResolvedValue([])
    openAt('/pedidos/AC-2026-0147')

    await userEvent.click(await screen.findByRole('button', { name: 'Libertar a tarefa' }))

    expect(api.release).toHaveBeenCalledWith('t1')
    expect(await screen.findByText('Tarefa libertada')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Desfazer' })).not.toBeInTheDocument()
  })

  it('says so when the task cannot be released', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    vi.mocked(api.release).mockRejectedValue(new Error('POST answered 500'))
    openAt('/pedidos/AC-2026-0147')

    await userEvent.click(await screen.findByRole('button', { name: 'Libertar a tarefa' }))

    expect(await screen.findByText('Não foi possível libertar a tarefa')).toBeInTheDocument()
  })

  it.each([403, 404] as const)('does not tell someone who takes no part in it that the request exists (%i)', async (status) => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: false, status })
    openAt('/pedidos/AC-2026-0147')
    expect(await screen.findByRole('alert')).toHaveTextContent('Não há nenhum pedido AC-2026-0147.')
  })

  it('says so when the request cannot be read', async () => {
    vi.mocked(api.openRequest).mockRejectedValue(new Error('GET answered 500'))
    openAt('/pedidos/AC-2026-0147')
    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível abrir o pedido')
  })
})

describe('returning and rejecting (UC18)', () => {
  const message = 'Corrija a data de regresso e junte o certificado, por favor.'
  const note = { field: 'regresso', label: 'Regresso', text: 'Indicou 10/11/2026; o certificado de presença diz 09/11/2026.' }

  // The button that opens the reasons; the one that confirms has the same name
  const opener = (name: string) => screen.findByRole('button', { name, expanded: false })
  const menu = (name: string) => within(screen.getByRole('region', { name }))

  async function open(name: string, opened: Partial<ReviewRequest> = {}) {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request: { ...request, ...opened } })
    openAt('/pedidos/AC-2026-0147')
    await userEvent.click(await opener(name))
    return menu(name)
  }

  const reasons = (m: ReturnType<typeof menu>) => m.getAllByRole('checkbox').map((box) => (box as HTMLInputElement).value)

  it('each button opens its own reasons, one menu at a time', async () => {
    const back = await open('Devolver para correção')
    expect(back.getByRole('group', { name: /^Porque devolve o pedido\?/ })).toBeInTheDocument()
    expect(reasons(back)).toEqual(['Preenchido incorretamente', 'Falta documento comprovativo'])

    await userEvent.click(await opener('Rejeitar o pedido'))
    expect(screen.queryByRole('region', { name: 'Devolver para correção' })).not.toBeInTheDocument()
    expect(menu('Rejeitar o pedido').getByRole('group', { name: /^Porque rejeita o pedido\?/ })).toBeInTheDocument()
    expect(reasons(menu('Rejeitar o pedido'))).toEqual(['Deslocação não autorizada', 'Fora do prazo legal'])
  })

  it('keeps «Rejeitar o pedido» even on a step with no reasons to reject', async () => {
    const path = request.path.map((s) => (s.id === 's2' ? { ...s, rejectReasons: [] } : s))
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request: { ...request, path } })
    openAt('/pedidos/AC-2026-0147')

    expect(await opener('Rejeitar o pedido')).toBeEnabled()
  })

  it.each(['Devolver para correção', 'Rejeitar o pedido'])('«%s» asks for at least one reason', async (name) => {
    const m = await open(name)
    await userEvent.click(m.getByRole('button', { name }))

    expect(m.getByText('Escolha pelo menos um motivo.')).toBeInTheDocument()
    expect(api.returnToRequester).not.toHaveBeenCalled()
    expect(api.reject).not.toHaveBeenCalled()
  })

  it('a return with no note needs a message', async () => {
    const m = await open('Devolver para correção')
    expect(m.getByText('Obrigatória: não deixou notas nos campos, por isso diga aqui o que tem de ser corrigido.')).toBeInTheDocument()
    await userEvent.click(m.getByRole('checkbox', { name: 'Preenchido incorretamente' }))
    await userEvent.type(m.getByRole('textbox'), '   ')
    await userEvent.click(m.getByRole('button', { name: 'Devolver para correção' }))

    expect(m.getByRole('alert')).toHaveTextContent('Diga a Ana o que tem de corrigir: escreva a mensagem ou deixe uma nota no campo.')
    expect(api.returnToRequester).not.toHaveBeenCalled()
  })

  it('with a note, the message may be left out, and the note goes too', async () => {
    vi.mocked(api.returnToRequester).mockResolvedValue({ done: true, result: { task: 't1', reference: 'AC-2026-0147' } })
    vi.mocked(api.listInbox).mockResolvedValue([])
    const m = await open('Devolver para correção', { notes: [note] })
    expect(m.getByText('As notas que deixou nos campos já explicam o que corrigir. Pode acrescentar uma mensagem.')).toBeInTheDocument()
    expect(m.getByText('Vai também a nota que deixou num campo.')).toBeInTheDocument()
    await userEvent.click(m.getByRole('checkbox', { name: 'Preenchido incorretamente' }))
    await userEvent.click(m.getByRole('button', { name: 'Devolver para correção' }))

    expect(api.returnToRequester).toHaveBeenCalledWith('t1', { reasons: ['Preenchido incorretamente'], message: '' })
    expect(await screen.findByText('Pedido devolvido para correção')).toBeInTheDocument()
  })

  it('returns for the reasons in the order of the step, and shows them in the notice with «Desfazer»', async () => {
    vi.mocked(api.returnToRequester).mockResolvedValue({ done: true, result: { task: 't1', reference: 'AC-2026-0147' } })
    vi.mocked(api.listInbox).mockResolvedValue([])
    const m = await open('Devolver para correção')
    await userEvent.click(m.getByRole('checkbox', { name: 'Falta documento comprovativo' }))
    await userEvent.click(m.getByRole('checkbox', { name: 'Preenchido incorretamente' }))
    await userEvent.type(m.getByRole('textbox', { name: /^Mensagem para Ana Ferreira, Técnica Superior/ }), message)
    await userEvent.click(m.getByRole('button', { name: 'Devolver para correção' }))

    expect(api.returnToRequester).toHaveBeenCalledWith('t1', { reasons: ['Preenchido incorretamente', 'Falta documento comprovativo'], message })
    expect(await screen.findByText('Pedido devolvido para correção')).toBeInTheDocument()
    expect(screen.getByText('Motivos: Preenchido incorretamente; Falta documento comprovativo.')).not.toHaveClass('sr-only')
    expect(screen.getByRole('button', { name: 'Desfazer' })).toBeInTheDocument()
  })

  it('a reason can be unticked', async () => {
    const m = await open('Devolver para correção')
    const box = m.getByRole('checkbox', { name: 'Preenchido incorretamente' })
    await userEvent.click(box)
    await userEvent.click(box)
    await userEvent.click(m.getByRole('button', { name: 'Devolver para correção' }))

    expect(m.getByText('Escolha pelo menos um motivo.')).toBeInTheDocument()
  })

  it('a rejection always needs a message, even with a note, and its confirm button is red', async () => {
    const m = await open('Rejeitar o pedido', { notes: [note] })
    expect(m.getByText('Explique a razão da rejeição.')).toBeInTheDocument()
    expect(m.queryByText('Vai também a nota que deixou num campo.')).not.toBeInTheDocument()
    await userEvent.click(m.getByRole('checkbox', { name: 'Fora do prazo legal' }))
    const confirm = m.getByRole('button', { name: 'Rejeitar o pedido' })
    expect(confirm).toHaveClass('bg-error')
    await userEvent.click(confirm)

    expect(m.getByRole('alert')).toHaveTextContent('Diga a Ana a razão da rejeição: escreva a mensagem.')
    expect(api.reject).not.toHaveBeenCalled()
  })

  it('rejects, with one reason in the notice', async () => {
    vi.mocked(api.reject).mockResolvedValue({ done: true, result: { task: 't1', reference: 'AC-2026-0147' } })
    vi.mocked(api.listInbox).mockResolvedValue([])
    const m = await open('Rejeitar o pedido')
    await userEvent.click(m.getByRole('checkbox', { name: 'Fora do prazo legal' }))
    await userEvent.type(m.getByRole('textbox'), 'Mensagem de rejeição')
    await userEvent.click(m.getByRole('button', { name: 'Rejeitar o pedido' }))

    expect(api.reject).toHaveBeenCalledWith('t1', { reasons: ['Fora do prazo legal'], message: 'Mensagem de rejeição' })
    expect(await screen.findByText('Pedido rejeitado')).toBeInTheDocument()
    expect(screen.getByText('Motivo: Fora do prazo legal.')).toBeInTheDocument()
  })

  describe('«Datas do certificado não coincidem»', () => {
    const answer = (field: string, label: string, value: string) => ({ field, label, value, changed: false, previous: null })
    const atTheEnd = (certificate: string): Partial<ReviewRequest> => ({
      currentStep: 's4',
      answers: [
        answer('partida', 'Partida', '08/11/2026 07:30'),
        answer('regresso', 'Regresso', '10/11/2026 19:40'),
        answer('cert', 'Datas do certificado', certificate),
      ],
    })
    const dates =
      'O certificado indica 08/11/2026 a 09/11/2026 e a deslocação foi de 08/11/2026 a 10/11/2026. Junte o certificado correto ou corrija as datas da deslocação.'
    const tick = (m: ReturnType<typeof menu>) => userEvent.click(m.getByRole('checkbox', { name: 'Datas do certificado não coincidem' }))

    it('fills in the message with the dates that do not match', async () => {
      const m = await open('Devolver para correção', atTheEnd('08/11/2026 a 09/11/2026'))
      await tick(m)
      expect(m.getByRole('textbox')).toHaveValue(dates)
    })

    it('keeps what the reviewer already wrote', async () => {
      const m = await open('Devolver para correção', atTheEnd('08/11/2026 a 09/11/2026'))
      await userEvent.type(m.getByRole('textbox'), message)
      await tick(m)
      expect(m.getByRole('textbox')).toHaveValue(message)
    })

    it('fills in nothing when the reason is unticked', async () => {
      const m = await open('Devolver para correção', atTheEnd('08/11/2026 a 09/11/2026'))
      await tick(m)
      await userEvent.clear(m.getByRole('textbox'))
      await tick(m)
      expect(m.getByRole('textbox')).toHaveValue('')
    })

    it('another reason fills in nothing', async () => {
      // The same dates, at a step with other reasons
      const m = await open('Devolver para correção', { ...atTheEnd('08/11/2026 a 09/11/2026'), currentStep: 's2' })
      await userEvent.click(m.getByRole('checkbox', { name: 'Preenchido incorretamente' }))
      expect(m.getByRole('textbox')).toHaveValue('')
    })

    it('fills in nothing when the dates match', async () => {
      const m = await open('Devolver para correção', atTheEnd('08/11/2026 a 10/11/2026'))
      await tick(m)
      expect(m.getByRole('textbox')).toHaveValue('')
    })
  })

  it('counts the message and stops at 1000 characters', async () => {
    const m = await open('Devolver para correção')
    const box = m.getByRole('textbox')
    expect(box).toHaveAttribute('maxlength', '1000')
    await userEvent.type(box, message)

    expect(m.getByText(`${message.length} de 1000 caracteres`)).toBeInTheDocument()
  })

  it.each(['Cancelar', 'Escape'])('closes the menu with %s, deciding nothing', async (how) => {
    const m = await open('Devolver para correção')
    await (how === 'Cancelar' ? userEvent.click(m.getByRole('button', { name: how })) : userEvent.type(m.getByRole('textbox'), '{Escape}'))

    expect(screen.queryByRole('region', { name: 'Devolver para correção' })).not.toBeInTheDocument()
    expect(api.returnToRequester).not.toHaveBeenCalled()
  })

  it('when someone decided first, goes back to the inbox saying who, with no «Desfazer»', async () => {
    const reason = 'Este pedido já foi tratado por Segunda Chefia, Chefe de Divisão.'
    vi.mocked(api.reject).mockResolvedValue({ done: false, reason })
    vi.mocked(api.listInbox).mockResolvedValue([])
    const m = await open('Rejeitar o pedido')
    await userEvent.click(m.getByRole('checkbox', { name: 'Fora do prazo legal' }))
    await userEvent.type(m.getByRole('textbox'), 'Mensagem de rejeição')
    await userEvent.click(m.getByRole('button', { name: 'Rejeitar o pedido' }))

    expect(await screen.findByText(reason)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Desfazer' })).not.toBeInTheDocument()
  })

  it.each([
    ['Devolver para correção', 'Preenchido incorretamente', 'Não foi possível devolver o pedido'],
    ['Rejeitar o pedido', 'Fora do prazo legal', 'Não foi possível rejeitar o pedido'],
  ])('says so when «%s» fails, keeping what was written', async (name, reason, failed) => {
    vi.mocked(api.returnToRequester).mockRejectedValue(new Error('POST answered 500'))
    vi.mocked(api.reject).mockRejectedValue(new Error('POST answered 500'))
    const m = await open(name)
    await userEvent.click(m.getByRole('checkbox', { name: reason }))
    await userEvent.type(m.getByRole('textbox'), message)
    await userEvent.click(m.getByRole('button', { name }))

    expect(await screen.findByText(failed)).toBeInTheDocument()
    expect(m.getByRole('textbox')).toHaveValue(message)
    expect(m.getByRole('button', { name })).toBeEnabled()
  })

  it.each([
    ['returned', 'Devolução desfeita'],
    ['rejected', 'Rejeição desfeita'],
  ])('undoing a %s request says «%s»', async (decision, undone) => {
    vi.mocked(api.listInbox).mockResolvedValue([])
    vi.mocked(api.undoDecision).mockResolvedValue({ done: true, result: { task: 't1', reference: 'AC-2026-0147', step: 'Validação da chefia', decision } })
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    openAt('/caixa-de-tarefas', { notice: { titulo: 'Pedido rejeitado' }, undo: { task: 't1', reference: 'AC-2026-0147' } })

    await userEvent.click(await screen.findByRole('button', { name: 'Desfazer' }))

    expect(await screen.findByText(undone)).toBeInTheDocument()
  })

  const by = 'Rui Matos, Chefe de Divisão'
  const returned = { status: 'returned', currentStep: 's1', task: null, reviewer: null, decision: { by, reasons: ['Preenchido incorretamente'], message } }
  const rejected = { status: 'rejected', task: null, reviewer: null, decision: { by, reasons: ['Deslocação não autorizada', 'Fora do prazo legal'], message } }

  it('shows the requester why the request was returned, and that the notes are next to the fields', async () => {
    const notesFrom = [{ ...note, author: by }]
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request: { ...request, ...returned, notesFrom, mine: true } })
    openAt('/pedidos/AC-2026-0147')

    const why = within(await screen.findByRole('region', { name: `Devolvido para correção por ${by} · motivo:` }))
    expect(why.getAllByRole('listitem').map((li) => li.textContent)).toEqual(['Preenchido incorretamente'])
    expect(why.getByText(`«${message}» As notas aparecem junto dos campos.`)).toBeInTheDocument()
    expect(screen.getByText(`Devolvido para correção por ${by}.`)).toBeInTheDocument()
    expect(screen.getByText('Passo 1 de 4: Pedido, a corrigir pelo requerente.')).toBeInTheDocument()
  })

  it('shows the requester why the request was rejected', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request: { ...request, ...rejected, mine: true } })
    openAt('/pedidos/AC-2026-0147')

    const why = within(await screen.findByRole('region', { name: `Rejeitado por ${by} · motivos:` }))
    expect(why.getAllByRole('listitem')).toHaveLength(2)
    expect(why.getByText(`«${message}»`)).toBeInTheDocument()
    expect(screen.getByText(`Rejeitado por ${by}: Deslocação não autorizada; Fora do prazo legal.`)).toBeInTheDocument()
    expect(screen.getByText('Passo 2 de 4: Validação da chefia, rejeitado.')).toBeInTheDocument()
  })

  it('a return with no message and no notes left shows the reasons alone', async () => {
    const decision = { ...returned.decision, message: null }
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request: { ...request, ...returned, decision, mine: true } })
    openAt('/pedidos/AC-2026-0147')

    const why = within(await screen.findByRole('region', { name: /^Devolvido para correção/ }))
    expect(why.queryByRole('paragraph')).not.toBeInTheDocument()
  })

  it('the others see who decided, but not that section', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request: { ...request, ...returned } })
    openAt('/pedidos/AC-2026-0147')

    expect(await screen.findByText(`Devolvido para correção por ${by}.`)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: /· motivo/ })).not.toBeInTheDocument()
  })

  it('shows the message in the history, under its entry', async () => {
    const text = `${by}, devolveu para correção: Preenchido incorretamente com 1 nota`
    const history = [...request.history, { at: '2026-11-22T18:32:00Z', text, message }]
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request: { ...request, ...returned, history } })
    openAt('/pedidos/AC-2026-0147')

    await userEvent.click(await screen.findByRole('button', { name: 'Ver histórico' }))
    expect(screen.getByText(text)).toBeInTheDocument()
    expect(screen.getByText(`«${message}»`)).toBeInTheDocument()
  })
})
