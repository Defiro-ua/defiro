import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Outlet, Route, Routes, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as api from '../api/engine'
import type { ReviewRequest } from '../api/engine'
import type { Profile } from '../api/session'
import { InboxPage } from './InboxPage'
import { RequestPage } from './RequestPage'

vi.mock('../api/engine', async (original) => ({
  ...(await original<typeof import('../api/engine')>()),
  listInbox: vi.fn(),
  openRequest: vi.fn(),
  approve: vi.fn(),
  undoApproval: vi.fn(),
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
    { id: 's1', name: 'Pedido', handledBy: 'Todos os trabalhadores' },
    { id: 's2', name: 'Validação da chefia', handledBy: 'Chefias da unidade do requerente' },
    { id: 's3', name: 'Verificação financeira', handledBy: 'Divisão Financeira' },
    { id: 's4', name: 'Validação final', handledBy: 'RH · Formação' },
  ],
  version: 1,
  requester: { name: 'Ana Ferreira', jobTitle: 'Técnica Superior', unit: 'Divisão de Obras Municipais', manager: 'Rui Matos' },
  answers: [
    { field: 'destino', label: 'Destino', value: 'Lisboa', changed: false, previous: null },
    { field: 'regresso', label: 'Regresso', value: '10/11/2026 19:40', changed: false, previous: null },
  ],
  files: [{ id: 'f1', filename: 'bilhete_comboio.pdf', version: 1 }],
  history: [{ at: '2026-11-18T16:02:00Z', text: 'Ana Ferreira, Técnica Superior, submeteu o pedido' }],
  task: 't1',
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

afterEach(() => vi.resetAllMocks())

describe('the inbox (until UC27)', () => {
  it('lists the requests waiting for the person, each with «Rever»', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([{ task: 't1', reference: 'AC-2026-0147', requester: 'Ana Ferreira', step: 'Validação da chefia' }])
    openAt('/caixa-de-tarefas')

    expect(await screen.findByRole('heading', { level: 2, name: 'Tarefas à sua espera · 1' })).toBeInTheDocument()
    expect(screen.getByText('AC-2026-0147 · Validação da chefia')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /^Rever\s*o pedido AC-2026-0147 de Ana Ferreira$/ })).toHaveAttribute('href', '/pedidos/AC-2026-0147')
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
    vi.mocked(api.undoApproval).mockResolvedValue({ done: true, result: { task: 't1', reference: 'AC-2026-0147', step: 'Validação da chefia' } })
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    openAt('/caixa-de-tarefas', { notice: { titulo: 'Pedido aprovado' }, undo: { task: 't1', reference: 'AC-2026-0147' } })

    await userEvent.click(await screen.findByRole('button', { name: 'Desfazer' }))

    expect(api.undoApproval).toHaveBeenCalledWith('t1')
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
    vi.mocked(api.undoApproval).mockReturnValue(new Promise(() => {}))
    openAt('/caixa-de-tarefas', { notice: { titulo: 'Pedido aprovado' }, undo: { task: 't1', reference: 'AC-2026-0147' } })

    const undo = await screen.findByRole('button', { name: 'Desfazer' })
    await userEvent.click(undo)
    await userEvent.click(undo)

    expect(api.undoApproval).toHaveBeenCalledOnce()
  })

  it('when it is too late to undo, says why and offers «Desfazer» no more', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([])
    const reason = 'Já não é possível desfazer: o pedido já foi tratado no passo «Verificação financeira».'
    vi.mocked(api.undoApproval).mockResolvedValue({ done: false, reason })
    openAt('/caixa-de-tarefas', { notice: { titulo: 'Pedido aprovado' }, undo: { task: 't1', reference: 'AC-2026-0147' } })

    await userEvent.click(await screen.findByRole('button', { name: 'Desfazer' }))

    expect(await screen.findByText(reason)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Desfazer' })).not.toBeInTheDocument()
  })

  it('says so when undoing fails', async () => {
    vi.mocked(api.listInbox).mockResolvedValue([])
    vi.mocked(api.undoApproval).mockRejectedValue(new Error('POST answered 500'))
    openAt('/caixa-de-tarefas', { notice: { titulo: 'Pedido aprovado' }, undo: { task: 't1', reference: 'AC-2026-0147' } })

    await userEvent.click(await screen.findByRole('button', { name: 'Desfazer' }))

    expect(await screen.findByText('Não foi possível desfazer')).toBeInTheDocument()
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

  it('ends the decision with «Aprovar», with return and reject waiting for UC18', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request })
    openAt('/pedidos/AC-2026-0147')

    const decision = await screen.findByRole('group', { name: 'Decisão' })
    const buttons = within(decision).getAllByRole('button')
    expect(buttons.map((b) => b.textContent)).toEqual(['Devolver para correção', 'Rejeitar o pedido', 'Aprovar'])
    expect(buttons[0]).toBeDisabled()
    expect(buttons[1]).toBeDisabled()
    expect(buttons[2]).toBeEnabled()
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

  it('shows no decision to someone who may not decide it now', async () => {
    vi.mocked(api.openRequest).mockResolvedValue({ found: true, request: { ...request, task: null } })
    openAt('/pedidos/AC-2026-0147')

    expect(await screen.findByRole('heading', { level: 2, name: 'Estado' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Aprovar' })).not.toBeInTheDocument()
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
