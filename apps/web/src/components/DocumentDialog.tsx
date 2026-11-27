import { useEffect, useRef, useState } from 'react'
import { fetchDocument } from '../api/engine'
import { documentFailed, savedNotice } from '../review/notices'
import { Button } from './Button'
import type { Notice } from './Flash'
import { Icon } from './Icon'

type Props = {
  /** The request whose final document is shown; the dialog is open while it is not null */
  reference: string | null
  onClose: () => void
  onSaved: (filename: string) => void
}

type Pdf = { url: string; filename: string } | 'failed' | null

/** Saves the PDF that is already here, through a link to its blob URL: no second request */
function save({ url, filename }: { url: string; filename: string }) {
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
}

/**
 * The final document of a closed request (UC23): the stored PDF itself, as the browser shows it,
 * under a bar with «Fechar» and «Transferir PDF» that stays at the top while it scrolls.
 */
export function DocumentDialog({ reference, onClose, onSaved }: Props) {
  const ref = useRef<HTMLDialogElement>(null)
  const returnTo = useRef<Element | null>(null)
  const [pdf, setPdf] = useState<Pdf>(null)
  // A device with no PDF viewer cannot show the document, so it downloads it at once and shows no dialog
  const noViewer = navigator.pdfViewerEnabled === false
  const saved = useRef<string | null>(null)
  // What the person was told in the dialog itself, which covers the page behind it
  const [told, setTold] = useState<Notice | null>(null)
  const callbacks = useRef({ onSaved, onClose })
  useEffect(() => {
    callbacks.current = { onSaved, onClose }
  })
  const shown = !!reference && (!noViewer || pdf === 'failed')

  // Fetched once for each opening; its blob URL lives until the dialog closes
  useEffect(() => {
    if (!reference) return
    let current = true
    let url = ''
    fetchDocument(reference).then(
      ({ blob, filename }) => {
        if (!current) return
        url = URL.createObjectURL(blob)
        setPdf({ url, filename })
      },
      () => current && setPdf('failed'),
    )
    return () => {
      current = false
      if (url) URL.revokeObjectURL(url)
      setPdf(null)
      setTold(null)
    }
  }, [reference])

  useEffect(() => {
    if (!noViewer || !pdf || pdf === 'failed' || saved.current === pdf.url) return
    saved.current = pdf.url
    save(pdf)
    callbacks.current.onSaved(pdf.filename)
    callbacks.current.onClose()
  }, [noViewer, pdf])

  useEffect(() => {
    const d = ref.current
    if (!d) return
    if (shown && !d.open) {
      returnTo.current = document.activeElement
      d.showModal()
    } else if (!shown && d.open) d.close()
  }, [shown])

  function closed() {
    if (reference) onClose()
    const el = returnTo.current as HTMLElement | null
    returnTo.current = null
    if (el?.focus && document.contains(el)) window.setTimeout(() => el.focus(), 0)
  }

  return (
    <dialog ref={ref} onClose={closed} aria-labelledby="doc-titulo" className="w-[680px] font-sans">
      {shown && (
        <div className="box-border flex max-h-[calc(100vh-48px)] flex-col gap-4.5 overflow-auto rounded-menu border border-edge bg-bg px-7 pb-7 text-ink">
          <div className="sticky top-0 z-2 -mx-7 flex flex-wrap items-center gap-2.5 border-b border-line bg-bg px-7 pt-4 pb-3.5">
            <Button variant="secondary" icon="x" onClick={() => ref.current?.close()}>
              Fechar <span className="sr-only">o documento</span>
            </Button>
            <span className="flex-1" />
            <Button
              icon="download"
              disabled={!pdf || pdf === 'failed'}
              onClick={() => {
                if (!pdf || pdf === 'failed') return
                save(pdf)
                setTold(savedNotice(pdf.filename))
                onSaved(pdf.filename)
              }}
            >
              Transferir PDF
            </Button>
            <p role="status" className="m-0 basis-full text-body-sm text-ink empty:hidden">
              {told && (
                <>
                  <span className="font-semibold text-success">{told.titulo}</span> {told.texto}
                </>
              )}
            </p>
          </div>
          <div className="flex items-start gap-4">
            <span aria-hidden="true" className="grid size-12 shrink-0 place-items-center rounded-md bg-soft text-accent-ink">
              <Icon name="fileText" size={24} />
            </span>
            <div className="flex min-w-0 flex-1 flex-col gap-0.5">
              <h2 id="doc-titulo" className="m-0 text-[22px] leading-[30px] font-extrabold tracking-heading">
                Documento do pedido {reference}
              </h2>
              {pdf && pdf !== 'failed' && <p className="m-0 text-body-sm text-ink-3 [overflow-wrap:anywhere]">{pdf.filename}</p>}
            </div>
          </div>
          {pdf === null && (
            <p role="status" className="m-0 text-ink-2">
              A carregar…
            </p>
          )}
          {pdf === 'failed' && (
            <p role="alert" className="m-0 text-ink">
              <span className="block font-semibold text-error">{documentFailed.titulo}</span>
              {documentFailed.texto}
            </p>
          )}
          {pdf && pdf !== 'failed' && <iframe title={pdf.filename} src={pdf.url} className="h-[80vh] w-full shrink-0 rounded-md border border-line-strong" />}
        </div>
      )}
    </dialog>
  )
}
