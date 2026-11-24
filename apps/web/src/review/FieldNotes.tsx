import { useState } from 'react'
import { deleteNote, saveNote, type Note, type NoteFrom as SentNote } from '../api/engine'
import { Button } from '../components/Button'
import type { Notice } from '../components/Flash'
import { FieldError, NoteFrom, Req } from '../components/form'
import { Icon } from '../components/Icon'
import { focusSoon } from '../lib/focus'
import { describedBy, textareaClass } from '../lib/formStyles'

/** The longest note, as the server takes it */
export const LONGEST = 1000

const secondarySmall =
  'box-border inline-flex min-h-11 shrink-0 items-center justify-center gap-2 rounded-pill border-emph border-field bg-bg px-4 text-[14px] font-semibold text-ink hover:bg-soft'

const emptyNote = 'Escreva a nota antes de guardar. Se já não precisa dela, carregue em «Cancelar».'

type Props = {
  /** The task the reviewer took: with it, they write notes; without it, they only read them */
  task: string | null
  field: string
  label: string
  /** The reviewer's own note on this field, if any */
  mine?: Note
  /** The notes sent on this field: the previous reviewer's or, for the requester, of who returned it */
  from: SentNote[]
  /** The requester's full name, for the delete confirmation */
  requester: string
  onChange: (field: string, note: Note | null) => void
  onNotice: (notice: Notice) => void
}

/**
 * The notes on one field of the request (UC19), as in the prototype: the previous reviewer's, and
 * the reviewer's own with «Nota» / «Editar nota», the editor, and «Apagar» after a confirmation.
 */
export function FieldNotes({ task, field, label, mine, from, requester, onChange, onNotice }: Props) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')
  const [empty, setEmpty] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const id = (part: string) => `nota-${part}-${field}`

  function open() {
    setEditing(true)
    setDraft(mine?.text ?? '')
    setEmpty(false)
    focusSoon(id('editor'))
  }

  function cancel() {
    setEditing(false)
    setEmpty(false)
    focusSoon(id('botao'))
  }

  async function save() {
    // An empty note is not saved: deleting has its own button
    if (!task || busy) return
    const text = draft.trim()
    if (!text) {
      setEmpty(true)
      onNotice({ tom: 'bad', titulo: emptyNote })
      focusSoon(id('editor'))
      return
    }
    setBusy(true)
    try {
      const saved = await saveNote(task, field, text)
      onChange(field, saved)
      onNotice({ tom: 'ok', titulo: mine ? 'Nota alterada' : 'Nota guardada' })
      setEditing(false)
      focusSoon(id('botao'))
    } catch {
      onNotice({ tom: 'bad', titulo: 'Não foi possível guardar a nota', texto: 'Tente outra vez daqui a pouco.' })
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    if (!task || busy) return
    setBusy(true)
    try {
      await deleteNote(task, field)
      onChange(field, null)
      onNotice({ tom: 'ok', titulo: 'Nota apagada' })
      setConfirming(false)
      focusSoon(id('botao'))
    } catch {
      onNotice({ tom: 'bad', titulo: 'Não foi possível apagar a nota', texto: 'Tente outra vez daqui a pouco.' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      {from.map((note) => (
        <NoteFrom key={note.author} quem={note.author} texto={note.text} />
      ))}
      {task && !editing && (
        <button type="button" id={id('botao')} className={`${secondarySmall} self-start`} onClick={open}>
          <Icon name="message" size={16} />
          {mine ? 'Editar nota' : 'Nota'}
          <span className="sr-only"> sobre {label}</span>
        </button>
      )}
      {mine && !editing && (
        <div className="edge flex flex-col gap-2 rounded-md bg-accent-soft py-1.5 pr-1.5 pl-3 text-[14px] leading-[21px] text-ink">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <Icon name="message" size={16} className="text-accent-ink" />
            <span className="flex-[1_1_200px]">
              <strong className="font-semibold">A sua nota:</strong> {mine.text}
            </span>
            {task && !confirming && (
              <button type="button" className={secondarySmall} onClick={() => setConfirming(true)}>
                Apagar<span className="sr-only"> a nota sobre {label}</span>
              </button>
            )}
          </div>
          {confirming && (
            <div role="group" aria-labelledby={id('confirmar')} className="flex flex-col gap-2 pb-1.5">
              <p id={id('confirmar')} className="m-0 font-semibold">
                Apagar a nota sobre «{label}»? {requester} deixa de a ver se devolver o pedido.
              </p>
              <div className="flex flex-wrap gap-2">
                <Button variant="danger" size="s" onClick={remove} disabled={busy}>
                  Apagar a nota
                </Button>
                <button type="button" className={secondarySmall} onClick={() => setConfirming(false)}>
                  Cancelar
                </button>
              </div>
            </div>
          )}
        </div>
      )}
      {editing && (
        <div className="flex flex-col gap-2 rounded-md border border-line-strong bg-tint p-3.5">
          <label htmlFor={id('editor')} className="text-[14px] leading-5 font-semibold">
            Nota sobre «{label}» <Req />
            <span className="sr-only"> (obrigatório)</span>
          </label>
          <span id={id('ajuda')} className="text-label-sm leading-[19px] text-ink-3">
            Diga o que está mal e como corrigir. Quem fez o pedido vê a nota junto deste campo.
          </span>
          <textarea
            id={id('editor')}
            rows={4}
            required
            maxLength={LONGEST}
            value={draft}
            onChange={(e) => {
              setDraft(e.target.value)
              setEmpty(false)
            }}
            onKeyDown={(e) => {
              if (e.key === 'Escape') {
                e.stopPropagation()
                cancel()
              }
            }}
            aria-invalid={empty || undefined}
            aria-describedby={describedBy(id('ajuda'), id('contador'), empty && id('erro'))}
            className={textareaClass(empty, 'min-h-26 text-[15px] leading-[22px]')}
          />
          <span id={id('contador')} className="text-label-sm leading-[19px] text-ink-3 tabular-nums">
            {draft.length} de {LONGEST} caracteres
          </span>
          {empty && (
            <span role="alert">
              <FieldError id={id('erro')}>{emptyNote}</FieldError>
            </span>
          )}
          <div className="flex flex-wrap gap-2">
            <button type="button" className={secondarySmall} onClick={cancel}>
              Cancelar
            </button>
            <Button size="s" icon="check" onClick={save} disabled={busy}>
              Guardar nota
            </Button>
          </div>
        </div>
      )}
    </>
  )
}
