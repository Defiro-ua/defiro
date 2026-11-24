import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Outlet, Route, Routes, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as api from '../api/designer'
import type { StoredDraft } from '../api/designer'
import type { Profile } from '../api/session'
import { WorkflowsPage } from './WorkflowsPage'

vi.mock('../api/designer')

const sofia: Profile = {
  name: 'Sofia Almeida',
  unit: 'Modernização Administrativa',
  jobTitle: 'Gestora de workflows',
  manager: 'Rui Matos',
  menu: ['create-workflow', 'new-request', 'inbox', 'workflows'],
}

const draft = (id: string, name: string, published?: { version: number; by: string; at: string }): StoredDraft => ({
  id,
  revision: 3,
  name,
  steps: [],
  publishedVersion: published?.version ?? null,
  publishedBy: published?.by ?? null,
  publishedAt: published?.at ?? null,
})

function Address() {
  const { pathname, search } = useLocation()
  return <p>Chegou a {pathname + search}</p>
}

function open(profile: Profile = sofia, state?: unknown) {
  render(
    <MemoryRouter initialEntries={[{ pathname: '/workflows', state }]}>
      <Routes>
        <Route element={<Outlet context={profile} />}>
          <Route path="/workflows" element={<WorkflowsPage />} />
        </Route>
        <Route path="*" element={<Address />} />
      </Routes>
    </MemoryRouter>,
  )
}

afterEach(() => vi.resetAllMocks())

const file = (text: string) => new File([text], 'horas-extraordinarias-v1.json', { type: 'application/json' })

describe('«Workflows»', () => {
  it('lists each workflow with its version, who created it and when, and the drafts still to create', async () => {
    vi.mocked(api.listDrafts).mockResolvedValue([
      draft('d1', 'Ajudas de custo', { version: 2, by: 'Sofia Almeida', at: '2026-11-19T10:02:00Z' }),
      draft('d2', 'Horas extraordinárias'),
    ])
    open()

    const list = await screen.findByRole('list', { name: 'Workflows' })
    const [published, unpublished] = within(list).getAllByRole('listitem')
    expect(published).toHaveTextContent('Ajudas de custo')
    expect(published).toHaveTextContent('Versão 2 · criado por Sofia Almeida a 19/11/2026')
    expect(within(published).getByRole('link', { name: 'Alterar Ajudas de custo' })).toHaveAttribute('href', '/workflows/criar/d1')
    expect(unpublished).toHaveTextContent('Rascunho por criar')
    expect(within(unpublished).getByRole('link', { name: 'Continuar Horas extraordinárias' })).toHaveAttribute('href', '/workflows/criar/d2')
  })

  it('downloads a created workflow as a file, but not a draft still to create', async () => {
    vi.mocked(api.listDrafts).mockResolvedValue([
      draft('d1', 'Ajudas de custo', { version: 2, by: 'Sofia Almeida', at: '2026-11-19T10:02:00Z' }),
      draft('d2', 'Horas extraordinárias'),
    ])
    vi.mocked(api.specificationUrl).mockImplementation((id) => `/files/${id}`)
    open()

    const [published, unpublished] = within(await screen.findByRole('list', { name: 'Workflows' })).getAllByRole('listitem')
    const download = within(published).getByRole('link', { name: 'Descarregar Ajudas de custo' })
    expect(download).toHaveAttribute('href', '/files/d1')
    expect(download).toHaveAttribute('download')
    expect(within(unpublished).queryByRole('link', { name: /Descarregar/ })).not.toBeInTheDocument()
  })

  it('loads a workflow file and opens it to review', async () => {
    vi.mocked(api.listDrafts).mockResolvedValue([])
    vi.mocked(api.importDraft).mockResolvedValue({ loaded: true, draft: draft('d9', 'Horas extraordinárias') })
    open()
    const user = userEvent.setup()

    await user.upload(await screen.findByLabelText('Carregar ficheiro'), file('{"schemaVersion":1}'))

    expect(await screen.findByText('Chegou a /workflows/criar/d9?ecra=rever')).toBeInTheDocument()
    expect(api.importDraft).toHaveBeenCalledWith('{"schemaVersion":1}')
  })

  it('says why a file was refused', async () => {
    vi.mocked(api.listDrafts).mockResolvedValue([])
    vi.mocked(api.importDraft).mockResolvedValue({ loaded: false, why: 'O ficheiro não é um workflow da defiro.' })
    open()
    const user = userEvent.setup()

    await user.upload(await screen.findByLabelText('Carregar ficheiro'), file('olá'))

    expect(await screen.findByRole('alert')).toHaveTextContent('O ficheiro não é um workflow da defiro.')
  })

  it('shows «Workflow criado» after creating one', async () => {
    vi.mocked(api.listDrafts).mockResolvedValue([])
    open(sofia, { notice: { tom: 'ok', titulo: 'Workflow criado', texto: '«Ajudas de custo» já aparece em «Novo pedido».' } })

    expect(await screen.findByText('Workflow criado', {}, { timeout: 2000 })).toBeInTheDocument()
  })

  it('says when there is no workflow yet, with «Criar workflow» in the menu', async () => {
    vi.mocked(api.listDrafts).mockResolvedValue([])
    open()

    expect(await screen.findByText(/Ainda não há workflows\./)).toBeInTheDocument()
    const menu = screen.getByRole('navigation', { name: 'Menu principal' })
    expect(within(menu).getByRole('link', { name: 'Criar workflow' })).toHaveAttribute('href', '/workflows/criar')
  })

  it('says so when the list cannot be read', async () => {
    vi.mocked(api.listDrafts).mockRejectedValue(new Error('GET answered 500'))
    open()

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível ler os workflows.')
  })

  it('is only for Workflow Managers', async () => {
    open({ ...sofia, menu: ['new-request', 'inbox'] })

    expect(await screen.findByText('Chegou a /caixa-de-tarefas')).toBeInTheDocument()
    expect(api.listDrafts).not.toHaveBeenCalled()
  })
})
