import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Flash } from './Flash'

describe('Flash', () => {
  it('shows the notice, which fades away by itself when it has no button', async () => {
    render(<Flash notice={{ titulo: 'Sessão terminada' }} onClose={() => {}} />)

    const notice = await screen.findByText('Sessão terminada')
    expect(notice.parentElement).toHaveClass('animate-notice')
    expect(screen.queryByRole('button', { name: 'Desfazer' })).not.toBeInTheDocument()
  })

  it('offers «Desfazer» and stays until it is closed', async () => {
    const undo = vi.fn()
    render(<Flash notice={{ titulo: 'Pedido aprovado' }} onClose={() => {}} onUndo={undo} />)

    await userEvent.click(await screen.findByRole('button', { name: 'Desfazer' }))

    expect(undo).toHaveBeenCalledOnce()
    expect(screen.getByText('Pedido aprovado').parentElement).not.toHaveClass('animate-notice')
  })

  it('reads `texto` to screen readers only, and shows `detalhe` to everyone', async () => {
    const notice = { titulo: 'Pedido rejeitado', texto: 'AC-2026-0147 foi encerrado.', detalhe: 'Motivo: Fora do prazo legal.' }
    render(<Flash notice={notice} onClose={() => {}} />)

    expect(await screen.findByText(/AC-2026-0147 foi encerrado\./)).toHaveClass('sr-only')
    expect(screen.getByText('Motivo: Fora do prazo legal.')).not.toHaveClass('sr-only')
  })

  it('closes', async () => {
    const close = vi.fn()
    render(<Flash notice={{ titulo: 'Pedido aprovado' }} onClose={close} onUndo={() => {}} />)

    await userEvent.click(await screen.findByRole('button', { name: 'Fechar o aviso' }))

    expect(close).toHaveBeenCalledOnce()
  })
})
