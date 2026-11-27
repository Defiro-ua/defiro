import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as api from '../api/engine'
import type { MyRequest } from '../api/engine'
import { MyRequests } from './MyRequests'

vi.mock('../api/engine', async (original) => ({ ...(await original<typeof import('../api/engine')>()), listMyRequests: vi.fn() }))

// The prototype's requests: AC-2026-0098 closed on 17/10/2026 at 15:30, AC-2026-0147 waiting
const closed: MyRequest = {
  reference: 'AC-2026-0098',
  workflow: 'Ajudas de custo',
  status: 'closed',
  currentStep: null,
  closedAt: '2026-10-17T15:30:00+01:00',
  hasDocument: true,
}
const inProgress: MyRequest = {
  reference: 'AC-2026-0147',
  workflow: 'Ajudas de custo',
  status: 'in_progress',
  currentStep: 'Verificação financeira',
  closedAt: null,
  hasDocument: false,
}

function show(requests: MyRequest[], onDocument = vi.fn()) {
  vi.mocked(api.listMyRequests).mockResolvedValue(requests)
  render(
    <MemoryRouter>
      <MyRequests onDocument={onDocument} />
    </MemoryRouter>,
  )
  return onDocument
}

afterEach(() => vi.resetAllMocks())

describe('«Os meus pedidos»', () => {
  it('closedOffersDocumento', async () => {
    show([closed])

    expect(await screen.findByRole('heading', { name: 'Os meus pedidos' })).toBeInTheDocument()
    expect(screen.getByText('Fechado a 17/10/2026')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Documento do pedido AC-2026-0098' })).toBeInTheDocument()
  })

  it('inProgressShowsItsStep', async () => {
    show([inProgress])

    expect(await screen.findByText(/Verificação financeira/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Documento/ })).not.toBeInTheDocument()
  })

  it('documentoCallsOnDocument', async () => {
    const onDocument = show([closed])

    await userEvent.click(await screen.findByRole('button', { name: 'Documento do pedido AC-2026-0098' }))

    expect(onDocument).toHaveBeenCalledWith('AC-2026-0098')
  })

  it('says nothing while the person has made no request', async () => {
    show([])

    await waitFor(() => expect(api.listMyRequests).toHaveBeenCalled())
    expect(screen.queryByRole('heading', { name: 'Os meus pedidos' })).not.toBeInTheDocument()
  })
})
