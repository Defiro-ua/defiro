import { IconButton } from '../components/Button'
import { Chip } from '../components/Chip'
import { ArrowHint, FieldError, Req } from '../components/form'
import { Icon } from '../components/Icon'
import { answerTypeName, answerTypes, canMoveField, fieldProblem, fieldTitle, profileFields, type Draft, type DraftChange, type Field } from '../designer/draft'
import { cx } from '../lib/cx'
import { cardClass, inputClass, radioClass, secondaryMedium, secondarySmall } from '../lib/formStyles'

/** A field's id, error and description, as the designer gives them (DesignerPage) */
export type FieldState = (field: string, help?: string) => { id: string; wrong: boolean; error: string; desc?: string }

/**
 * «Formulário» (UC04): the questions of the request form, in order. Name, unit and manager come
 * first, from the profile, and cannot be changed; each question can be opened, moved and removed,
 * and shows what is wrong with it.
 */
export function FieldList({
  draft,
  fields,
  change,
  open,
  add,
}: {
  draft: Draft
  fields: Field[]
  change: (c: DraftChange, focus?: string) => void
  open: (id: string) => void
  add: () => void
}) {
  const first = profileFields.length
  return (
    <div className="flex flex-col gap-4">
      <ol aria-label="Campos do formulário" className="m-0 list-none overflow-hidden rounded-md border border-line bg-panel p-0">
        {profileFields.map((name, i) => (
          <li key={name} className={cx('flex flex-wrap items-center gap-x-3.5 gap-y-2.5 px-4 py-3.5', i > 0 && 'border-t border-line')}>
            <span aria-hidden="true" className="w-7 shrink-0 text-center text-[14px] font-extrabold text-ink-3">
              {i + 1}
            </span>
            <span className="flex min-w-0 flex-[1_1_220px] flex-col gap-1">
              <span className="text-[15px] leading-[22px] font-semibold">{name}</span>
              <span className="flex flex-wrap items-center gap-1.5 text-label-sm text-ink-2">
                <Chip icon="lock">Do perfil (IdP)</Chip>
              </span>
            </span>
          </li>
        ))}
        {fields.map((q, j) => {
          const title = fieldTitle(q)
          const problem = fieldProblem(q)
          return (
            <li key={q.id} className="flex flex-wrap items-center gap-x-3.5 gap-y-2.5 border-t border-line px-4 py-3.5">
              <span aria-hidden="true" className="w-7 shrink-0 text-center text-[14px] font-extrabold text-ink-3">
                {first + j + 1}
              </span>
              <span className="flex min-w-0 flex-[1_1_220px] flex-col gap-1">
                <span className="text-[15px] leading-[22px] font-semibold">
                  {title}
                  {q.required && <span className="font-normal text-ink-3"> · obrigatório</span>}
                </span>
                <span className="flex flex-wrap items-center gap-1.5 text-label-sm text-ink-2">
                  {answerTypeName(q.type)}{' '}
                  <Chip tone="info" icon="pen">
                    Requerente
                  </Chip>
                </span>
                {problem && (
                  <span className="flex items-center gap-1.5 text-label-sm leading-[18px] font-semibold text-error">
                    <Icon name="alertCircle" size={14} />
                    {problem}
                  </span>
                )}
              </span>
              <span className="inline-flex gap-1">
                <button type="button" id={'ed-' + q.id} className={secondarySmall} onClick={() => open(q.id)}>
                  <Icon name="pen" size={16} />
                  {'Alterar '}
                  <span className="sr-only">{title}</span>
                </button>
                <IconButton
                  id={'cu-' + q.id}
                  icon="arrowUp"
                  label={`Mover «${title}» para cima`}
                  disabled={!canMoveField(draft, q.id, -1)}
                  onClick={() => change({ t: 'moveField', field: q.id, by: -1 }, j - 1 === 0 ? 'cd-' + q.id : 'cu-' + q.id)}
                />
                <IconButton
                  id={'cd-' + q.id}
                  icon="arrowDown"
                  label={`Mover «${title}» para baixo`}
                  disabled={!canMoveField(draft, q.id, 1)}
                  onClick={() => change({ t: 'moveField', field: q.id, by: 1 }, j + 2 === fields.length ? 'cu-' + q.id : 'cd-' + q.id)}
                />
                <IconButton icon="trash" danger label={`Remover o campo «${title}»`} onClick={() => change({ t: 'removeField', field: q.id }, 'add-campo')} />
              </span>
            </li>
          )
        })}
      </ol>
      <button type="button" id="add-campo" className={cx(secondaryMedium, 'self-start')} onClick={add}>
        <Icon name="plus" size={18} />
        Acrescentar campo
      </button>
      <p className="m-0 flex items-start gap-2 text-[14px] leading-[21px] text-ink-2">
        <Icon name="lock" size={16} className="mt-px text-accent-ink" />
        <span>Nome, unidade orgânica e chefia vêm do fornecedor de identidade (IdP). Entram sozinhos no pedido e não se editam aqui.</span>
      </p>
    </div>
  )
}

/** A question on its own screen: its label, its answer type from the cards and whether it is mandatory */
export function FieldEditor({
  question: q,
  at,
  fieldState,
  change,
}: {
  question: Field
  /** The question's field in the draft, steps[0].fields[j] */
  at: string
  fieldState: FieldState
  change: (c: DraftChange) => void
}) {
  const label = fieldState(`${at}.label`, 'ajuda-cp-rot')
  const options = fieldState(`${at}.options`, 'ajuda-cp-opcoes')
  return (
    <div className="flex flex-col gap-6">
      <div className="flex max-w-[560px] flex-col gap-1.5">
        <label htmlFor={label.id} className="text-[16px] font-semibold">
          Rótulo <Req />
        </label>
        <span id="ajuda-cp-rot" className="text-[14px] text-ink-3">
          A pergunta que o requerente vê. Ex.: Material pedido
        </span>
        <input
          id={label.id}
          type="text"
          autoComplete="off"
          required
          value={q.label}
          onChange={(e) => change({ t: 'field', field: q.id, patch: { label: e.target.value } })}
          aria-invalid={label.wrong || undefined}
          aria-describedby={label.desc}
          className={inputClass(label.wrong, 'px-3.5')}
        />
        {label.error && <FieldError id={'erro-' + label.id}>{label.error}</FieldError>}
      </div>
      <fieldset className="m-0 flex flex-col gap-2.5 border-0 p-0">
        <legend className="mb-1 p-0 text-[16px] leading-[22px] font-semibold">
          Tipo de resposta <Req />
          <span className="sr-only"> (obrigatório)</span>
        </legend>
        <ArrowHint />
        <div className="flex flex-wrap gap-2.5">
          {answerTypes.map((a) => (
            <label key={a.type} className={cardClass(q.type === a.type, 'min-h-16 flex-[1_1_240px]')}>
              <input
                type="radio"
                name="cp-tipo"
                value={a.type}
                checked={q.type === a.type}
                onChange={() => change({ t: 'field', field: q.id, patch: { type: a.type } })}
                className={cx(radioClass, 'mt-0.5')}
              />
              <span className="flex flex-col gap-0.5">
                <span className="text-[15px] leading-[21px] font-semibold">{a.name}</span>
                <span className="text-label-sm leading-[18px] text-ink-3">{a.example}</span>
              </span>
            </label>
          ))}
        </div>
      </fieldset>
      {q.type === 'single-choice' && (
        <div className="flex max-w-[560px] flex-col gap-1.5">
          <label htmlFor={options.id} className="text-[16px] font-semibold">
            Opções de resposta <Req />
          </label>
          <span id="ajuda-cp-opcoes" className="text-[14px] text-ink-3">
            Pelo menos duas, uma por linha.
          </span>
          <textarea
            id={options.id}
            rows={4}
            required
            value={q.options.join('\n')}
            onChange={(e) => change({ t: 'options', field: q.id, v: e.target.value })}
            aria-invalid={options.wrong || undefined}
            aria-describedby={options.desc}
            className={cx(
              'box-border min-h-28 w-full rounded-md bg-panel px-3.5 py-3 text-[16px] leading-6 text-ink',
              options.wrong ? 'border-2 border-error' : 'border border-field',
            )}
          />
          {options.error && <FieldError id={'erro-' + options.id}>{options.error}</FieldError>}
        </div>
      )}
      <label className={cardClass(q.required, 'max-w-[560px] items-center self-start')}>
        <input
          type="checkbox"
          checked={q.required}
          onChange={(e) => change({ t: 'field', field: q.id, patch: { required: e.target.checked } })}
          className={radioClass}
        />
        <span className="flex flex-col gap-0.5">
          <span className="text-[15px] leading-[21px] font-semibold">Resposta obrigatória</span>
          <span className="text-label-sm leading-[18px] text-ink-3">O requerente não pode avançar sem responder.</span>
        </span>
      </label>
    </div>
  )
}
