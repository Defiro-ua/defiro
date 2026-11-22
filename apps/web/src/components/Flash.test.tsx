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

  it('closes', async () => {
    const close = vi.fn()
    render(<Flash notice={{ titulo: 'Pedido aprovado' }} onClose={close} onUndo={() => {}} />)

    await userEvent.click(await screen.findByRole('button', { name: 'Fechar o aviso' }))

    expect(close).toHaveBeenCalledOnce()
  })
})
