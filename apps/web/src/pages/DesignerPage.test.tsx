import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Outlet, Route, Routes, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../api/designer'
import type { StoredDraft } from '../api/designer'
import type { Profile } from '../api/session'
import type { Step } from '../designer/draft'
import { DesignerPage } from './DesignerPage'

vi.mock('../api/designer')

const submission = { name: 'submission', version: '0.1.0' }
const review = { name: 'review', version: '0.1.0' }
const finalValidation = { name: 'final-validation', version: '0.1.0' }
const decisions = ['approved', 'returned', 'rejected']

const request: Step = {
  id: 'pedido',
  plugin: submission,
  name: 'Pedido',
  handledBy: 'Todos os trabalhadores',
  deadline: null,
  returnReasons: [],
  rejectReasons: [],
}

const reviewStep = (id: string, name: string): Step => ({
  id,
  plugin: review,
  name,
  handledBy: 'Chefias da unidade do requerente',
  deadline: 5,
  returnReasons: ['Preenchido incorretamente'],
  rejectReasons: ['Pedido não autorizado'],
})

const stored = (name: string, ...steps: Step[]): StoredDraft => ({
  id: 'd1',
  revision: 1,
  name,
  steps: [request, ...steps],
  publishedVersion: null,
  publishedBy: null,
  publishedAt: null,
})

const sofia: Profile = {
  name: 'Sofia Almeida',
  unit: 'Modernização Administrativa',
  jobTitle: 'Gestora de workflows',
  manager: 'Rui Matos',
  menu: ['create-workflow', 'new-request', 'inbox', 'workflows'],
}

function Arrived() {
  const location = useLocation()
  const notice = (location.state as { notice?: { titulo: string } } | null)?.notice
  return <p>Chegou a {location.pathname + (notice ? ` com «${notice.titulo}»` : '')}</p>
}

function open(at: string, profile: Profile = sofia) {
  render(
    <MemoryRouter initialEntries={[at]}>
      <Routes>
        <Route element={<Outlet context={profile} />}>
          <Route path="/workflows/criar" element={<DesignerPage />} />
          <Route path="/workflows/criar/:id" element={<DesignerPage />} />
        </Route>
        <Route path="*" element={<Arrived />} />
      </Routes>
    </MemoryRouter>,
  )
  return userEvent.setup()
}

const question = () => screen.findByRole('heading', { level: 2 })
const goOn = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole('button', { name: /^(Continuar|Rever o workflow|Guardar o passo|Guardar e voltar)/ }))

beforeEach(() => {
  vi.mocked(api.listStepTypes).mockResolvedValue([
    { ...submission, outcomes: ['submitted'] },
    { ...review, outcomes: decisions },
    { ...finalValidation, outcomes: decisions },
  ])
  vi.mocked(api.saveDraft).mockImplementation(async (id, draft) => ({ ...stored(''), ...draft, id }))
})

afterEach(() => vi.resetAllMocks())

describe('creating a workflow', () => {
  it('starts a draft and opens it on the name, the first of four screens', async () => {
    vi.mocked(api.startDraft).mockResolvedValue(stored(''))
    vi.mocked(api.loadDraft).mockResolvedValue(stored(''))
    open('/workflows/criar')

    expect(await question()).toHaveTextContent('Como se chama o workflow?')
    expect(screen.getByText('Passo 1 de 4')).toBeInTheDocument()
    expect(api.startDraft).toHaveBeenCalledWith('')
    expect(api.loadDraft).toHaveBeenCalledWith('d1')
  })

  it('does not move on without a name, and says why next to the field', async () => {
    vi.mocked(api.loadDraft).mockResolvedValue(stored(''))
    const user = open('/workflows/criar/d1')
    await question()

    await goOn(user)

    expect(screen.getByLabelText(/Nome do workflow/)).toHaveAccessibleDescription(/Dê um nome ao workflow\./)
    expect(screen.getByRole('alert')).toHaveTextContent('Para continuar, corrija o que está assinalado.')
    expect(api.saveDraft).not.toHaveBeenCalled()
  })

  it('saves each answer on the way, and «Anterior» goes back without losing it', async () => {
    vi.mocked(api.loadDraft).mockResolvedValue(stored(''))
    const user = open('/workflows/criar/d1')
    await user.type(await screen.findByLabelText(/Nome do workflow/), 'Ajudas de custo')

    await goOn(user)

    expect(await question()).toHaveTextContent('Quem pode fazer este pedido?')
    expect(screen.getByText('Passo 2 de 4')).toBeInTheDocument()
    expect(api.saveDraft).toHaveBeenCalledWith('d1', expect.objectContaining({ name: 'Ajudas de custo' }))
    await user.click(screen.getByRole('button', { name: 'Anterior' }))
    expect(await screen.findByLabelText(/Nome do workflow/)).toHaveValue('Ajudas de custo')
  })

  it('asks the questions of a new review step, then goes back to the path', async () => {
    vi.mocked(api.loadDraft).mockResolvedValue(stored('Ajudas de custo'))
    const user = open('/workflows/criar/d1?ecra=percurso')
    expect(await question()).toHaveTextContent('O percurso está completo?')

    await user.click(screen.getByRole('button', { name: /^Revisão/ }))
    expect(await question()).toHaveTextContent('Como se chama o passo 2 e quem o trata?')
    expect(screen.getByText('Passo 3 de 4')).toBeInTheDocument()
    const name = screen.getByLabelText(/Nome do passo/)
    await user.clear(name)
    await user.type(name, 'Validação da chefia')
    await user.click(screen.getByLabelText(/Chefias da unidade do requerente/))
    await goOn(user)

    expect(await question()).toHaveTextContent('Em quanto tempo tem de ser tratado «Validação da chefia»?')
    await user.type(screen.getByLabelText(/Prazo legal/), '200')
    await goOn(user)
    expect(screen.getByLabelText(/Prazo legal/)).toHaveAccessibleDescription(/o prazo legal tem de ser de 1 a 90 dias úteis/)
    await user.clear(screen.getByLabelText(/Prazo legal/))
    await user.type(screen.getByLabelText(/Prazo legal/), '5')
    await goOn(user)

    expect(await question()).toHaveTextContent('Porque pode «Validação da chefia» devolver ou rejeitar o pedido?')
    await goOn(user)

    expect(await question()).toHaveTextContent('O percurso está completo?')
    const path = screen.getByRole('list', { name: 'Passos do percurso' })
    expect(
      within(path)
        .getAllByRole('heading')
        .map((h) => h.textContent),
    ).toEqual(['Passo 1: Pedido', 'Passo 2: Validação da chefia'])
    expect(screen.getByText(/Prazo total: 5 dias úteis/)).toBeInTheDocument()
  })

  it('keeps return and reject reasons in their own lists, and asks each a name', async () => {
    vi.mocked(api.loadDraft).mockResolvedValue(stored('Ajudas de custo', reviewStep('a', 'Validação da chefia')))
    const user = open('/workflows/criar/d1?ecra=pm:a')
    await question()

    await user.click(screen.getByRole('button', { name: 'Acrescentar motivo para rejeitar' }))
    await user.click(screen.getByRole('button', { name: 'Remover o motivo «Preenchido incorretamente»' }))
    await goOn(user)

    expect(screen.getByLabelText('Motivo para rejeitar 2')).toHaveAccessibleDescription(/dê um nome ao motivo de rejeição 2/)
    expect(screen.getByText('Sem motivos para devolver: neste passo não se pode devolver o pedido.')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Motivo para rejeitar 2'), 'Fora de prazo')
    await goOn(user)

    expect(api.saveDraft).toHaveBeenLastCalledWith(
      'd1',
      expect.objectContaining({ steps: [request, expect.objectContaining({ returnReasons: [], rejectReasons: ['Pedido não autorizado', 'Fora de prazo'] })] }),
    )
  })

  it('moves and removes review steps, and keeps one final validation, last', async () => {
    vi.mocked(api.loadDraft).mockResolvedValue(stored('Ajudas de custo', reviewStep('a', 'Validação da chefia'), reviewStep('b', 'Verificação financeira')))
    const user = open('/workflows/criar/d1?ecra=percurso')
    await question()
    const path = () =>
      within(screen.getByRole('list', { name: 'Passos do percurso' }))
        .getAllByRole('heading')
        .map((h) => h.textContent?.replace(/^Passo \d+: /, ''))

    expect(screen.getByRole('button', { name: 'Mover «Validação da chefia» para cima' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Mover «Verificação financeira» para cima' }))
    expect(path()).toEqual(['Pedido', 'Verificação financeira', 'Validação da chefia'])
    await user.click(screen.getByRole('button', { name: 'Mover «Verificação financeira» para baixo' }))
    expect(path()).toEqual(['Pedido', 'Validação da chefia', 'Verificação financeira'])
    await user.click(screen.getByRole('button', { name: 'Remover o passo «Verificação financeira»' }))
    expect(path()).toEqual(['Pedido', 'Validação da chefia'])

    await user.click(screen.getByRole('button', { name: /^Validação final/ }))
    expect(await question()).toHaveTextContent('Como se chama o passo 3 e quem o trata?')
    const sofar = screen.getByRole('complementary', { name: 'O percurso até agora' })
    expect(within(sofar).getAllByRole('listitem').at(-1)).toHaveTextContent('Validação final')
    await user.click(screen.getByRole('button', { name: 'Anterior' }))
    expect(await question()).toHaveTextContent('O percurso está completo?')
    expect(screen.queryByRole('button', { name: /^Validação final/ })).not.toBeInTheDocument()
    expect(screen.getByText('Acrescentar uma revisão antes da validação final')).toBeInTheDocument()
  })

  it('does not leave the path while a step is incomplete', async () => {
    vi.mocked(api.loadDraft).mockResolvedValue(stored('Ajudas de custo', { ...reviewStep('a', 'Validação da chefia'), deadline: null }))
    const user = open('/workflows/criar/d1?ecra=percurso')
    await question()

    await goOn(user)

    expect(screen.getByRole('alert')).toHaveTextContent('Para continuar, complete este passo:')
    await user.click(within(screen.getByRole('alert')).getByRole('button', { name: /Completar/ }))
    expect(await question()).toHaveTextContent('Como se chama o passo 2 e quem o trata?')
  })

  it('creates the workflow from the review and shows «Workflow criado» in «Workflows»', async () => {
    vi.mocked(api.loadDraft).mockResolvedValue(stored('Ajudas de custo', reviewStep('a', 'Validação da chefia')))
    vi.mocked(api.publishDraft).mockResolvedValue({ published: true, version: 1 })
    const user = open('/workflows/criar/d1?ecra=rever')
    expect(await question()).toHaveTextContent('Confira o workflow antes de o criar')
    expect(screen.getByText('Passo 4 de 4')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Criar workflow' }))

    expect(await screen.findByText('Chegou a /workflows com «Workflow criado»')).toBeInTheDocument()
    expect(api.saveDraft).toHaveBeenCalled()
    expect(api.publishDraft).toHaveBeenCalledWith('d1')
  })

  it('shows what the Designer refused when publishing', async () => {
    vi.mocked(api.loadDraft).mockResolvedValue(stored('Ajudas de custo', reviewStep('a', 'Validação da chefia')))
    vi.mocked(api.publishDraft).mockResolvedValue({
      published: false,
      problems: [{ field: 'steps', message: 'O caminho começa pelo pedido, e só tem um pedido.' }],
    })
    const user = open('/workflows/criar/d1?ecra=rever')
    await question()

    await user.click(screen.getByRole('button', { name: 'Criar workflow' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ainda não pode criar o workflow: falta corrigir 1 coisa, assinalada abaixo.')
    expect(screen.getByText('O caminho começa pelo pedido, e só tem um pedido.')).toBeInTheDocument()
  })

  it('goes back to the review after changing a part of it', async () => {
    vi.mocked(api.loadDraft).mockResolvedValue(stored('Ajudas de custo', reviewStep('a', 'Validação da chefia')))
    const user = open('/workflows/criar/d1?ecra=rever')
    await question()

    await user.click(screen.getByRole('button', { name: 'Alterar Nome' }))
    expect(await question()).toHaveTextContent('Como se chama o workflow?')
    await user.click(screen.getByRole('button', { name: 'Guardar e voltar à revisão' }))

    expect(await question()).toHaveTextContent('Confira o workflow antes de o criar')
  })

  it('moves on with Enter, as the screen says, but not from a button', async () => {
    vi.mocked(api.loadDraft).mockResolvedValue(stored(''))
    const user = open('/workflows/criar/d1')
    await user.type(await screen.findByLabelText(/Nome do workflow/), 'Ajudas de custo{Enter}')

    expect(await question()).toHaveTextContent('Quem pode fazer este pedido?')
    screen.getByRole('button', { name: 'Anterior' }).focus()
    await user.keyboard('{Enter}')
    expect(await question()).toHaveTextContent('Como se chama o workflow?')
  })

  it('jumps to the question from the top of the page', async () => {
    vi.mocked(api.loadDraft).mockResolvedValue(stored('Ajudas de custo'))
    const user = open('/workflows/criar/d1')
    await question()

    await user.click(screen.getByRole('link', { name: 'Saltar para a pergunta' }))

    expect(screen.getByRole('heading', { level: 2 })).toHaveFocus()
  })

  it('says so when an answer cannot be saved', async () => {
    vi.mocked(api.loadDraft).mockResolvedValue(stored('Ajudas de custo'))
    vi.mocked(api.saveDraft).mockRejectedValue(new Error('PUT answered 500'))
    const user = open('/workflows/criar/d1')
    await question()

    await goOn(user)

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível guardar o rascunho. Tente outra vez.')
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('Como se chama o workflow?')
  })

  it('says so when the draft no longer exists', async () => {
    vi.mocked(api.loadDraft).mockResolvedValue(null)
    open('/workflows/criar/gone')

    expect(await screen.findByText('Este rascunho já não existe.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Workflows/ })).toHaveAttribute('href', '/workflows')
  })

  it('is only for Workflow Managers', async () => {
    open('/workflows/criar', { ...sofia, menu: ['new-request', 'inbox'] })

    expect(await screen.findByText('Chegou a /caixa-de-tarefas')).toBeInTheDocument()
    await waitFor(() => expect(api.startDraft).not.toHaveBeenCalled())
  })

  it('leaves with «Sair», back to «Workflows»', async () => {
    vi.mocked(api.loadDraft).mockResolvedValue(stored('Ajudas de custo'))
    open('/workflows/criar/d1')
    await question()

    expect(screen.getByRole('link', { name: /Sair/ })).toHaveAttribute('href', '/workflows')
  })
})
