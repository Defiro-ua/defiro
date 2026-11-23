import { useEffect, useState } from 'react'
import { cx } from '../lib/cx'
import { Button, IconButton } from './Button'
import { Icon, type IconName } from './Icon'

export type Tone = 'ok' | 'warn' | 'bad' | 'info' | 'neutro'

/** A short message about what just happened; `texto` is read out to screen readers only, `detalhe` is shown */
export type Notice = { titulo: string; texto?: string; detalhe?: string; tom?: Tone }

const toneColor = { ok: 'bg-success', warn: 'bg-warning', bad: 'bg-error', info: 'bg-info', neutro: 'bg-ink-2' } as const
const toneGlyph: Partial<Record<Tone, IconName>> = { ok: 'check', warn: 'reset', bad: 'x' }

type FlashProps = {
  notice: Notice | null
  onClose: () => void
  /** Reverses what the notice is about; with it, the notice stays until it is closed (UC17 #6) */
  onUndo?: () => void
}

/**
 * The small card at the top of the screen after an action. A short title; the
 * details are read out to screen readers. Without buttons it fades away by
 * itself after a few seconds.
 */
export function Flash({ notice, onClose, onUndo }: FlashProps) {
  // The live region exists before the text arrives, so screen readers announce it
  const [ready, setReady] = useState(false)
  useEffect(() => {
    const t = window.setTimeout(() => setReady(true), 150)
    return () => window.clearTimeout(t)
  }, [])

  const tom = notice?.tom || 'ok'

  return (
    <div role="status" aria-live="polite">
      {notice && ready && (
        <div
          className={cx(
            'fixed inset-x-0 top-4 z-[120] mx-auto box-border flex w-max max-w-screen-gutter flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-line-strong bg-panel py-2 pr-2 pl-3 text-ink shadow-toast',
            onUndo ? 'animate-toast' : 'animate-notice',
          )}
        >
          <span aria-hidden="true" className={cx('grid size-7 shrink-0 place-items-center rounded-full text-bg', toneColor[tom])}>
            <Icon name={toneGlyph[tom] || 'check'} size={16} />
          </span>
          <p className="m-0 min-w-0 text-[15px] leading-[22px] font-semibold">
            {notice.titulo}
            {notice.texto && <span className="sr-only">. {notice.texto}</span>}
            {notice.detalhe && <span className="block text-[14px] leading-[21px] font-normal">{notice.detalhe}</span>}
          </p>
          {onUndo && (
            <Button variant="secondary" size="s" icon="reset" className="min-h-9 px-3" onClick={onUndo}>
              Desfazer
            </Button>
          )}
          <IconButton icon="x" iconSize={16} label="Fechar o aviso" onClick={onClose} className="size-9 border-transparent" />
        </div>
      )}
    </div>
  )
}
