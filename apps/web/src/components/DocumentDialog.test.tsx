import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../api/engine'
import { documentFailed } from '../review/notices'
import { DocumentDialog } from './DocumentDialog'

vi.mock('../api/engine', async (original) => ({ ...(await original<typeof import('../api/engine')>()), fetchDocument: vi.fn() }))

const filename = 'ordem_reembolso_AC-2026-0098.pdf'
const pdf = () => ({ blob: new Blob(['%PDF-1.4'], { type: 'application/pdf' }), filename })

// jsdom has neither a modal <dialog> nor blob URLs
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
    this.removeAttribute('open')
    this.dispatchEvent(new Event('close'))
  }
  URL.createObjectURL = vi.fn(() => 'blob:http://localhost/0098')
  URL.revokeObjectURL = vi.fn()
})

afterEach(() => vi.resetAllMocks())

function show(reference: string | null = 'AC-2026-0098') {
  return render(<DocumentDialog reference={reference} onClose={vi.fn()} onSaved={vi.fn()} />)
}

describe('DocumentDialog', () => {
  it('showsThePdf', async () => {
    vi.mocked(api.fetchDocument).mockResolvedValue(pdf())
    show()

    const frame = await screen.findByTitle(filename)
    expect(frame.tagName).toBe('IFRAME')
    expect(frame.getAttribute('src')).toMatch(/^blob:/)
  })

  it('barHasBothButtons', async () => {
    vi.mocked(api.fetchDocument).mockResolvedValue(pdf())
    show()

    const bar = (await screen.findByRole('dialog')).firstElementChild?.firstElementChild as HTMLElement
    expect(within(bar).getByRole('button', { name: /^Fechar/ })).toBeInTheDocument()
    expect(within(bar).getByRole('button', { name: /^Transferir PDF/ })).toBeInTheDocument()
  })

  it('fetchesOnce', async () => {
    vi.mocked(api.fetchDocument).mockResolvedValue(pdf())
    show()

    await screen.findByTitle(filename)
    expect(api.fetchDocument).toHaveBeenCalledTimes(1)
    expect(api.fetchDocument).toHaveBeenCalledWith('AC-2026-0098')
  })

  it('failureShowsNotice', async () => {
    vi.mocked(api.fetchDocument).mockRejectedValue(new Error('offline'))
    show()

    expect(await screen.findByText(documentFailed.titulo)).toBeInTheDocument()
    expect(screen.queryByTitle(filename)).not.toBeInTheDocument()
  })

  it('revokesTheBlobUrlOnClose', async () => {
    vi.mocked(api.fetchDocument).mockResolvedValue(pdf())
    const { rerender } = show()
    await screen.findByTitle(filename)

    rerender(<DocumentDialog reference={null} onClose={vi.fn()} onSaved={vi.fn()} />)

    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/0098'), { timeout: 3000 })
  })

  it('fecharReturnsFocus', async () => {
    vi.mocked(api.fetchDocument).mockResolvedValue(pdf())
    // As «Os meus pedidos» opens it: «Documento» gives the reference, «Fechar» takes it away
    function Opener() {
      const [reference, setReference] = useState<string | null>(null)
      return (
        <>
          <button type="button" onClick={() => setReference('AC-2026-0098')}>
            Documento do pedido AC-2026-0098
          </button>
          <DocumentDialog reference={reference} onClose={() => setReference(null)} onSaved={vi.fn()} />
        </>
      )
    }
    render(<Opener />)
    const documento = screen.getByRole('button', { name: 'Documento do pedido AC-2026-0098' })

    await userEvent.click(documento)
    await screen.findByTitle(filename)
    await userEvent.click(screen.getByRole('button', { name: /^Fechar/ }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await waitFor(() => expect(documento).toHaveFocus())
  })

  it('fecharFocusesTheHeadingWhenTheOpenerIsGone', async () => {
    vi.mocked(api.fetchDocument).mockResolvedValue(pdf())
    // As the closing notice opens it: «Ver documento» is gone once the dialog is open
    function Opener() {
      const [reference, setReference] = useState<string | null>(null)
      const [notice, setNotice] = useState(true)
      return (
        <main>
          <h1 tabIndex={-1}>Caixa de tarefas</h1>
          {notice && (
            <button
              type="button"
              onClick={() => {
                setReference('AC-2026-0098')
                setNotice(false)
              }}
            >
              Ver documento
            </button>
          )}
          <DocumentDialog reference={reference} onClose={() => setReference(null)} onSaved={vi.fn()} />
        </main>
      )
    }
    render(<Opener />)

    await userEvent.click(screen.getByRole('button', { name: 'Ver documento' }))
    await screen.findByTitle(filename)
    await userEvent.click(screen.getByRole('button', { name: /^Fechar/ }))

    await waitFor(() => expect(screen.getByRole('heading', { level: 1 })).toHaveFocus())
  })

  describe('«Transferir PDF»', () => {
    let clicked: HTMLAnchorElement[]
    beforeEach(() => {
      clicked = []
      vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
        clicked.push(this)
      })
    })
    afterEach(() => {
      vi.restoreAllMocks()
      Object.defineProperty(navigator, 'pdfViewerEnabled', { value: true, configurable: true })
    })

    it('transferirSaves', async () => {
      vi.mocked(api.fetchDocument).mockResolvedValue(pdf())
      const onSaved = vi.fn()
      render(<DocumentDialog reference="AC-2026-0098" onClose={vi.fn()} onSaved={onSaved} />)
      await screen.findByTitle(filename)

      await userEvent.click(screen.getByRole('button', { name: /^Transferir PDF/ }))

      expect(clicked).toHaveLength(1)
      expect(clicked[0].getAttribute('download')).toBe(filename)
      expect(clicked[0].getAttribute('href')).toBe('blob:http://localhost/0098')
      expect(onSaved).toHaveBeenCalledExactlyOnceWith(filename)
      expect(api.fetchDocument).toHaveBeenCalledTimes(1)
    })

    it('transferirSaysSoInTheDialog', async () => {
      vi.mocked(api.fetchDocument).mockResolvedValue(pdf())
      render(<DocumentDialog reference="AC-2026-0098" onClose={vi.fn()} onSaved={vi.fn()} />)
      await screen.findByTitle(filename)

      await userEvent.click(screen.getByRole('button', { name: /^Transferir PDF/ }))

      const status = within(screen.getByRole('dialog')).getByRole('status')
      expect(status).toHaveTextContent('PDF guardado')
      expect(status).toHaveTextContent(`${filename} foi guardado.`)
    })

    it('noViewerDownloadsAtOnce', async () => {
      Object.defineProperty(navigator, 'pdfViewerEnabled', { value: false, configurable: true })
      const showModal = vi.spyOn(HTMLDialogElement.prototype, 'showModal')
      vi.mocked(api.fetchDocument).mockResolvedValue(pdf())
      const onSaved = vi.fn()
      const onClose = vi.fn()
      render(<DocumentDialog reference="AC-2026-0098" onClose={onClose} onSaved={onSaved} />)

      await waitFor(() => expect(onSaved).toHaveBeenCalledExactlyOnceWith(filename))

      expect(showModal).not.toHaveBeenCalled()
      expect(document.querySelector('iframe')).toBeNull()
      expect(clicked).toHaveLength(1)
      expect(clicked[0].getAttribute('download')).toBe(filename)
      expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('noViewerRevokesTheBlobUrlOnlyAfterTheDownloadHasStarted', async () => {
      Object.defineProperty(navigator, 'pdfViewerEnabled', { value: false, configurable: true })
      vi.mocked(api.fetchDocument).mockResolvedValue(pdf())
      const onSaved = vi.fn()
      function Host() {
        const [reference, setReference] = useState<string | null>('AC-2026-0098')
        return <DocumentDialog reference={reference} onClose={() => setReference(null)} onSaved={onSaved} />
      }
      render(<Host />)
      await waitFor(() => expect(onSaved).toHaveBeenCalled())
      expect(clicked).toHaveLength(1)

      expect(URL.revokeObjectURL).not.toHaveBeenCalled()
      await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/0098'), { timeout: 3000 })
    })
  })
})
