import type { ReactNode } from 'react'
import { cx } from '../lib/cx'
import { Icon } from './Icon'

/** The asterisk of a required field (the label says «obrigatório» to screen readers where needed) */
export const Req = () => (
  <span aria-hidden="true" className="text-accent-ink">
    *
  </span>
)

export function FieldError({ id, children, className }: { id?: string; children: ReactNode; className?: string }) {
  return (
    <span id={id} className={cx('error-line flex items-center gap-1.5 text-[14px] leading-5 font-semibold text-error', className)}>
      <Icon name="alertCircle" size={16} />
      {children}
    </span>
  )
}

/** What stops the person from moving on, at the top of the step */
export function StepError({ children, extra }: { children: ReactNode; extra?: ReactNode }) {
  return (
    <div
      id="passo-erro"
      tabIndex={-1}
      role="alert"
      className="flex flex-col gap-2.5 rounded-md border-2 border-error bg-error-soft px-4 py-3.5 text-error outline-none"
    >
      <p className="error-line m-0 flex gap-2.5 text-[15px] leading-[22px] font-semibold">
        <Icon name="alertCircle" size={20} />
        <span>{children}</span>
      </p>
      {extra}
    </div>
  )
}

/** Shown while a radio button has the keyboard focus */
export const ArrowHint = () => (
  <p className="arrow-hint m-0 items-center gap-1.5 text-label-sm leading-[18px] font-semibold text-accent-ink" aria-hidden="true">
    Use as setas ↑ ↓ para escolher outra opção
  </p>
)

/** «Anterior», the Enter hint and «Continuar» at the bottom of a form step */
export function WizardFooter({
  previous,
  previousText = 'Anterior',
  next,
  nextText,
  last,
  enterHint = true,
}: {
  previous?: () => void
  previousText?: string
  next: () => void
  nextText: string
  last?: boolean
  enterHint?: boolean
}) {
  return (
    <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-3 border-t border-line pt-6">
      {previous && (
        <button
          type="button"
          id="btn-anterior"
          onClick={previous}
          className="box-border inline-flex min-h-13 items-center justify-center gap-2 rounded-pill border-emph border-field bg-bg px-6.5 text-[16px] font-semibold text-ink hover:bg-soft"
        >
          <Icon name="arrowLeft" size={18} />
          {previousText}
        </button>
      )}
      <span className="flex-1" />
      {enterHint && (
        <span aria-hidden="true" className="text-[14px] text-ink-3">
          ou carregue em <kbd className="rounded-doc border border-line-strong bg-panel px-2 py-0.5 font-sans text-label-sm font-semibold">Enter ↵</kbd>
        </span>
      )}
      <button
        type="button"
        id="btn-continuar"
        onClick={next}
        className="box-border inline-flex min-h-13 items-center justify-center gap-2 rounded-pill border-0 bg-brand px-6.5 text-[16px] font-semibold text-on-brand hover:brightness-107"
      >
        {nextText}
        <Icon name={last ? 'check' : 'arrowRight'} size={18} />
      </button>
    </div>
  )
}
