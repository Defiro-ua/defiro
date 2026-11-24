import { useState } from 'react'
import { Button } from '../components/Button'
import { FieldError, Req } from '../components/form'
import { Icon } from '../components/Icon'
import { cx } from '../lib/cx'
import { focusSoon } from '../lib/focus'
import { describedBy, textareaClass } from '../lib/formStyles'
import { DATES_REASON } from './certificateDates'
import { LONGEST } from './FieldNotes'

export type EndKind = 'devolver' | 'rejeitar'
export type Why = { reasons: string[]; message: string }

type Props = {
  requester: { name: string; jobTitle: string | null }
  /** The reasons of the step, as the workflow has them */
  returnReasons: string[]
  rejectReasons: string[]
  /** How many notes the reviewer left: they go with a return */
  notes: number
  /** What «Datas do certificado não coincidem» writes in the message, or nothing */
  datesMessage: string
  busy: boolean
  onDecide: (kind: EndKind, why: Why) => void
}

const label = { devolver: 'Devolver para correção', rejeitar: 'Rejeitar o pedido' } as const

/**
 * Returning and rejecting (UC18), as in the prototype: two buttons, each opening its own list of
 * reasons and the message. Both are always there: rejecting is always possible (Pedro, 2026-11-23).
 */
export function EndMenus({ requester, returnReasons, rejectReasons, notes, datesMessage, busy, onDecide }: Props) {
  const [menu, setMenu] = useState<EndKind | null>(null)
  const toggle = (kind: EndKind) => setMenu((open) => (open === kind ? null : kind))
  const common = { requester, notes, datesMessage, busy, onDecide }
  return (
    <>
      <EndMenu kind="devolver" reasons={returnReasons} open={menu === 'devolver'} onToggle={() => toggle('devolver')} {...common} />
      <EndMenu kind="rejeitar" reasons={rejectReasons} open={menu === 'rejeitar'} onToggle={() => toggle('rejeitar')} {...common} />
    </>
  )
}

type MenuProps = {
  kind: EndKind
  reasons: string[]
  open: boolean
  onToggle: () => void
} & Pick<Props, 'requester' | 'notes' | 'datesMessage' | 'busy' | 'onDecide'>

// What is chosen and written stays while the menu is closed
function EndMenu({ kind, reasons, open, onToggle, requester, notes, datesMessage, busy, onDecide }: MenuProps) {
  const [chosen, setChosen] = useState<string[]>([])
  const [message, setMessage] = useState('')
  const [wrong, setWrong] = useState<'reasons' | 'message' | null>(null)
  const fix = kind === 'devolver'
  const first = requester.name.split(' ')[0]
  // A rejection always needs a message: the notes do not go with it
  const messageRequired = !fix || notes === 0
  const id = (part: string) => `${part}-${kind}`

  function close() {
    onToggle()
    setWrong(null)
    focusSoon(id('btn'))
  }

  function confirm() {
    const picked = reasons.filter((reason) => chosen.includes(reason))
    if (picked.length === 0) {
      setWrong('reasons')
      focusSoon(id('motivos'))
      return
    }
    const text = message.trim()
    if (messageRequired && !text) {
      setWrong('message')
      focusSoon(id('msg'))
      return
    }
    onDecide(kind, { reasons: picked, message: text })
  }

  return (
    <div className="flex flex-col gap-2.5">
      <button
        type="button"
        id={id('btn')}
        aria-expanded={open}
        aria-controls={id('menu')}
        disabled={busy}
        onClick={() => {
          onToggle()
          setWrong(null)
        }}
        className={cx(
          'box-border inline-flex min-h-13 w-full items-center justify-start gap-2 rounded-pill border-emph px-4.5 text-[15px] font-semibold text-ink',
          open ? 'border-orange bg-accent-soft' : 'border-field bg-bg hover:bg-soft',
        )}
      >
        <Icon name={fix ? 'reset' : 'x'} size={18} />
        <span className="flex-1 text-left">{label[kind]}</span>
        <Icon name={open ? 'chevronUp' : 'chevronDown'} size={18} />
      </button>
      {open && (
        <div
          id={id('menu')}
          role="region"
          aria-labelledby={id('btn')}
          onKeyDown={(e) => {
            if (e.key === 'Escape') {
              e.stopPropagation()
              close()
            }
          }}
          className={cx('flex animate-surge flex-col gap-4 rounded-lg border-2 bg-bg p-4.5', fix ? 'border-orange' : 'border-error')}
        >
          <fieldset
            id={id('motivos')}
            tabIndex={-1}
            aria-describedby={describedBy(id('ajuda'), wrong === 'reasons' && id('erro'))}
            className="m-0 flex flex-col gap-2 border-0 p-0 outline-none"
          >
            <legend className="mb-1 p-0 text-[15px] leading-[22px] font-semibold">
              {fix ? 'Porque devolve o pedido?' : 'Porque rejeita o pedido?'} <Req />
              <span className="sr-only"> (obrigatório)</span>
            </legend>
            <span id={id('ajuda')} className="-mt-0.5 text-label-sm leading-[19px] text-ink-3">
              {fix ? `O pedido volta para ${first} corrigir.` : 'O pedido é encerrado e não pode ser corrigido.'} Escolha um ou mais motivos.
            </span>
            {wrong === 'reasons' && <FieldError id={id('erro')}>Escolha pelo menos um motivo.</FieldError>}
            {reasons.map((reason) => {
              const on = chosen.includes(reason)
              return (
                <label
                  key={reason}
                  className={cx(
                    'choice-card box-border flex min-h-13 cursor-pointer items-center gap-3 rounded-item px-3.5 py-2.5',
                    on ? 'border-2 border-orange bg-accent-soft' : 'border border-line-strong bg-panel',
                  )}
                >
                  <input
                    type="checkbox"
                    value={reason}
                    checked={on}
                    onChange={() => {
                      setChosen((all) => (on ? all.filter((x) => x !== reason) : [...all, reason]))
                      if (!on && reason === DATES_REASON && !message.trim()) setMessage(datesMessage)
                      setWrong(null)
                    }}
                    className="m-0 size-5.5 shrink-0 accent-orange"
                  />
                  <span className="flex-1 text-[15px] leading-[21px] font-semibold">{reason}</span>
                </label>
              )
            })}
          </fieldset>
          <div className="flex flex-col gap-1.5">
            <label htmlFor={id('msg')} className="text-[15px] leading-[22px] font-semibold">
              Mensagem para {[requester.name, requester.jobTitle].filter(Boolean).join(', ')}{' '}
              {messageRequired ? (
                <>
                  <Req />
                  <span className="sr-only"> (obrigatório)</span>
                </>
              ) : (
                <span className="font-normal text-ink-3">(opcional)</span>
              )}
            </label>
            <span id={id('msg-ajuda')} className="text-label-sm leading-[19px] text-ink-3">
              {messageHelp(fix, notes)}
            </span>
            <textarea
              id={id('msg')}
              rows={3}
              required={messageRequired}
              maxLength={LONGEST}
              value={message}
              onChange={(e) => {
                setMessage(e.target.value)
                setWrong(null)
              }}
              aria-invalid={wrong === 'message' || undefined}
              aria-describedby={describedBy(id('msg-ajuda'), id('msg-contador'), wrong === 'message' && id('msg-erro'))}
              className={textareaClass(wrong === 'message', 'min-h-24 text-[15px] leading-[22px]')}
            />
            <span id={id('msg-contador')} className="text-label-sm leading-[19px] text-ink-3 tabular-nums">
              {message.length} de {LONGEST} caracteres
            </span>
            {wrong === 'message' && (
              <span role="alert">
                <FieldError id={id('msg-erro')}>
                  {fix
                    ? `Diga a ${first} o que tem de corrigir: escreva a mensagem ou deixe uma nota no campo.`
                    : `Diga a ${first} a razão da rejeição: escreva a mensagem.`}
                </FieldError>
              </span>
            )}
          </div>
          {fix && (
            <p className="m-0 flex items-start gap-2 text-[14px] leading-[21px] text-ink-2">
              <Icon name="message" size={16} className="mt-px text-accent-ink" />
              <span>{notesThatGo(notes)}</span>
            </p>
          )}
          <div className="flex flex-col gap-2.5">
            <Button variant="secondary" className="w-full" onClick={close}>
              Cancelar
            </Button>
            <Button id={id('confirmar')} variant={fix ? 'primary' : 'danger'} className="w-full" disabled={busy} onClick={confirm}>
              {label[kind]}
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}

function messageHelp(fix: boolean, notes: number) {
  if (!fix) return 'Explique a razão da rejeição.'
  return notes > 0
    ? 'As notas que deixou nos campos já explicam o que corrigir. Pode acrescentar uma mensagem.'
    : 'Obrigatória: não deixou notas nos campos, por isso diga aqui o que tem de ser corrigido.'
}

function notesThatGo(notes: number) {
  if (notes === 0) return 'Não deixou notas nos campos. Pode acrescentá-las com «Nota», junto de cada campo.'
  return notes === 1 ? 'Vai também a nota que deixou num campo.' : `Vão também as ${notes} notas que deixou nos campos.`
}
