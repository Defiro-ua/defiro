import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, Navigate, useNavigate, useParams, useSearchParams } from 'react-router'
import {
  listGroups,
  listStepTypes,
  loadDraft,
  publishDraft,
  saveDraft,
  startDraft,
  type GroupChoices,
  type GroupOption,
  type Publication,
  type StepType,
} from '../api/designer'
import { IconButton } from '../components/Button'
import { Chip } from '../components/Chip'
import { ArrowHint, FieldError, Req, StepError, WizardFooter } from '../components/form'
import { Icon } from '../components/Icon'
import { WizardLayout } from '../components/layout/WizardLayout'
import {
  answerTypeName,
  canMove,
  changeDraft,
  fieldTitle,
  FINAL_VALIDATION,
  isFinalValidation,
  isRequest,
  problemsOf,
  REVIEW,
  totalDeadline,
  type Draft,
  type DraftChange,
  type PluginRef,
  type Problem,
  type ReasonList,
  type Step,
} from '../designer/draft'
import { domId, isScreen, kindOf, numberOf, ofForm, ofPath, screenOfField, screenOrder, SCREENS } from '../designer/screens'
import { cx } from '../lib/cx'
import { focusSoon } from '../lib/focus'
import { cardClass, describedBy, enterMovesOn, inputClass, primarySmall, radioClass, secondaryMedium, secondarySmall } from '../lib/formStyles'
import { useProfile } from '../lib/useProfile'
import { paths } from '../routes'
import { FieldEditor, FieldList } from './RequestFormScreens'

const stepName = (s: Step) => s.name || 'Passo sem nome'

type Loaded = { id: string; draft: Draft; types: StepType[]; groups: GroupChoices }

/**
 * «Criar workflow» (UC03): a new draft at /workflows/criar, an existing one at /workflows/criar/:id.
 * `?ecra=` opens a given screen, for example `percurso`. Only for Workflow Managers.
 */
export function DesignerPage() {
  const profile = useProfile()
  const { id } = useParams()
  const navigate = useNavigate()
  const [loaded, setLoaded] = useState<Loaded | null | 'missing' | 'failed'>(null)
  const started = useRef(false)
  const allowed = profile.menu.includes('create-workflow')

  // A new draft: the Designer creates it, and its address replaces /workflows/criar
  useEffect(() => {
    if (!allowed || id || started.current) return
    started.current = true
    startDraft('').then(
      (draft) => navigate(`${paths.designer}/${draft.id}`, { replace: true }),
      () => setLoaded('failed'),
    )
  }, [allowed, id, navigate])

  useEffect(() => {
    if (!allowed || !id) return
    let current = true
    Promise.all([loadDraft(id), listStepTypes(), listGroups()]).then(
      ([draft, types, groups]) => current && setLoaded(draft ? { id, draft: { name: draft.name, steps: draft.steps }, types, groups } : 'missing'),
      () => current && setLoaded('failed'),
    )
    return () => {
      current = false
    }
  }, [allowed, id])

  if (!allowed) return <Navigate to={paths.inbox} replace />
  if (loaded === 'missing' || loaded === 'failed')
    return (
      <div role="alert" className="flex flex-col gap-2 p-10 text-ink">
        <p className="m-0 text-[18px] font-semibold">{loaded === 'missing' ? 'Este rascunho já não existe.' : 'Não foi possível abrir o rascunho.'}</p>
        <Link to={paths.workflows}>Voltar a «Workflows»</Link>
      </div>
    )
  if (!loaded)
    return (
      <p role="status" className="m-0 p-10 text-ink-2">
        A carregar…
      </p>
    )
  return <Designer key={loaded.id} {...loaded} />
}

function Designer({ id, draft: first, types, groups }: Loaded) {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [draft, setDraft] = useState(first)
  const [view, setView] = useState(params.get('ecra') || 'ident')
  const [volta, setVolta] = useState('')
  const [fromReview, setFromReview] = useState(false)
  const [tried, setTried] = useState<ReadonlySet<string>>(new Set())
  const [triedAll, setTriedAll] = useState(false)
  const [refused, setRefused] = useState<Problem[]>([])
  /** What could not reach the Designer: saving an answer or creating the workflow */
  const [failed, setFailed] = useState('')

  const steps = draft.steps
  const request = steps[0]
  const decisions = steps.filter((s) => !isRequest(s))
  const order = screenOrder(draft)
  const known = isScreen(draft, view) ? view : 'percurso'
  const { kind, step: stepId } = kindOf(known)
  const step = kind === 'campo' ? null : steps.find((s) => s.id === stepId) || null
  const k = step ? steps.indexOf(step) : -1
  const fields = request?.fields ?? []
  const editing = kind === 'campo' ? fields.find((q) => q.id === stepId) || null : null

  const local = problemsOf(draft)
  const problems = [...local, ...refused.filter((r) => !local.some((p) => p.field === r.field))]
  const screenOf = (field: string) => screenOfField(draft, field)
  const visible = problems.filter(
    (p) =>
      triedAll || tried.has(screenOf(p.field)) || (tried.has('percurso') && ofPath(screenOf(p.field))) || (tried.has('campos') && ofForm(screenOf(p.field))),
  )
  /** What a screen answers: «Percurso» and «Formulário» answer their own screens too */
  const answers = (screen: string) => (kind === 'percurso' ? ofPath(screen) : kind === 'campos' ? ofForm(screen) : screen === known)
  const listScreen = kind === 'percurso' || kind === 'campos'
  const errorOf = (field: string) => visible.find((p) => p.field === field)?.message || ''
  const fieldState = (field: string, help?: string) => {
    const error = errorOf(field)
    return { id: domId(field), wrong: !!error, error, desc: describedBy(help, error && 'erro-' + domId(field)) }
  }
  const complete = (s: Step) => !problems.some((p) => screenOf(p.field).split(':')[1] === s.id)
  const plugin = (name: string): PluginRef | undefined => {
    const found = types.filter((t) => t.name === name).at(-1)
    return found && { name: found.name, version: found.version }
  }

  const change = (c: DraftChange, focus?: string) => {
    setDraft((d) => changeDraft(d, c))
    setRefused([])
    if (focus) focusSoon(focus)
  }
  const go = (screen: string, opts: { volta?: string; fromReview?: boolean; focus?: string } = {}) => {
    setView(screen)
    if (opts.volta !== undefined) setVolta(opts.volta)
    if (opts.fromReview !== undefined) setFromReview(opts.fromReview)
    setFailed('')
    focusSoon(opts.focus || 'passo-titulo')
  }
  const fieldOf = (s: Step) => `steps[${steps.indexOf(s)}]`

  async function save() {
    try {
      await saveDraft(id, draft)
      return true
    } catch {
      setFailed('Não foi possível guardar o rascunho. Tente outra vez.')
      focusSoon('passo-erro')
      return false
    }
  }

  const startSection = kind === 'ident' || kind === 'quem' || kind === 'percurso' || kind === 'campos'

  async function next() {
    if (kind === 'rever') return create()
    const mine = problems.filter((p) => answers(screenOf(p.field)))
    if (mine.length) {
      setTried((t) => new Set(t).add(listScreen ? kind : known))
      focusSoon(listScreen ? 'passo-erro' : domId(mine[0].field))
      return
    }
    if (!(await save())) return
    if (kind === 'campo') return go('campos', { focus: 'ed-' + stepId })
    if (volta === 'percurso' && (kind === 'pm' || kind === 'quem')) return go('percurso', { volta: '', focus: 'pc-' + (kind === 'quem' ? request.id : stepId) })
    if (fromReview && startSection) return go('rever', { fromReview: false })
    go(order[order.indexOf(known) + 1])
  }

  function previous() {
    if (kind === 'campo') return go('campos', { focus: 'ed-' + stepId })
    if (fromReview && startSection) return go('rever', { fromReview: false })
    if (volta === 'percurso') {
      if (kind === 'pn' || kind === 'quem') return go('percurso', { volta: '', focus: 'pc-' + (kind === 'quem' ? request.id : stepId) })
      if (kind === 'pp') return go('pn:' + stepId)
      if (kind === 'pm') return go('pp:' + stepId)
    }
    const i = order.indexOf(known)
    if (i > 0) go(order[i - 1])
  }

  async function create() {
    if (local.length) {
      setTriedAll(true)
      focusSoon('passo-erro')
      return
    }
    if (!(await save())) return
    let publication: Publication
    try {
      publication = await publishDraft(id)
    } catch {
      setFailed('Não foi possível criar o workflow. Tente outra vez.')
      focusSoon('passo-erro')
      return
    }
    if (!publication.published) {
      setRefused(publication.problems)
      setTriedAll(true)
      focusSoon('passo-erro')
      return
    }
    navigate(paths.workflows, {
      state: {
        notice: {
          tom: 'ok',
          titulo: 'Workflow criado',
          texto: `«${draft.name.trim()}» já aparece em «Novo pedido» para ${request.handledBy || 'os trabalhadores'}.`,
        },
      },
    })
  }

  function addStep(name: string) {
    const ref = plugin(name)
    if (!ref) return
    const newId = crypto.randomUUID()
    change({ t: 'addStep', id: newId, plugin: ref })
    go('pn:' + newId, { volta: 'percurso' })
  }

  function addField() {
    const newId = crypto.randomUUID()
    change({ t: 'addField', id: newId })
    go('campo:' + newId)
  }

  // ——— the texts of each screen
  const questions: Record<string, { q: string; ajuda: string }> = {
    ident: { q: 'Como se chama o workflow?', ajuda: 'É o nome que os trabalhadores veem em «Novo pedido».' },
    quem: { q: 'Quem pode fazer este pedido?', ajuda: 'Escolha o grupo ou a unidade. Só essas pessoas veem o pedido em «Novo pedido».' },
    pn: {
      q: step ? `Como se chama o passo ${k + 1} e quem o trata?` : '',
      ajuda:
        step && isFinalValidation(step)
          ? 'É o último passo do percurso: quem o trata confirma, fecha o procedimento e gera o documento final.'
          : 'Neste passo, alguém aprova o pedido, devolve-o para correção ou rejeita-o.',
    },
    pp: {
      q: step ? `Em quanto tempo tem de ser tratado «${stepName(step)}»?` : '',
      ajuda: 'Indique o prazo legal em dias úteis. A defiro avisa quem trata o passo quando o prazo está perto do fim.',
    },
    pm: {
      q: step ? `Porque pode «${stepName(step)}» devolver ou rejeitar o pedido?` : '',
      ajuda: 'Quem trata este passo escolhe um ou mais destes motivos quando devolve ou rejeita. Precisa de pelo menos um.',
    },
    percurso: {
      q: 'O percurso está completo?',
      ajuda: 'O pedido passa por estes passos, por esta ordem. Pode alterar um passo, mudar a ordem ou acrescentar outro no fim.',
    },
    campos: { q: 'Que dados pede ao requerente?', ajuda: 'São as perguntas do formulário do pedido, por esta ordem. Acrescente um campo de cada vez.' },
    campo: {
      q: editing?.label.trim() ? `Como é o campo «${editing.label.trim()}»?` : 'Como é o novo campo?',
      ajuda: 'O rótulo é a pergunta que o requerente vê. Escolha o tipo de resposta que espera.',
    },
    rever: { q: 'Confira o workflow antes de o criar', ajuda: 'Pode alterar qualquer parte antes de criar.' },
  }
  const names: Record<string, string> = {
    ident: 'Nome',
    quem: 'Quem pode pedir',
    pn: 'quem trata',
    pp: 'prazo',
    pm: 'motivos',
    percurso: 'Percurso',
    campos: 'Formulário',
    campo: 'Formulário',
    rever: 'Rever e criar',
  }
  const kicker = [`Passo ${numberOf(kind)} de ${SCREENS}`, step ? `«${stepName(step)}»: ${names[kind]}` : names[kind]]
  const pct = Math.round(((order.indexOf(kind === 'campo' ? 'campos' : known) + 1) / order.length) * 100)
  const total = totalDeadline(draft)

  // ——— what stops the person at the top of the screen
  const screenProblems = visible.filter((p) => (listScreen ? tried.has(kind) : true) && answers(screenOf(p.field)))
  const incomplete = kind === 'percurso' && tried.has('percurso') ? decisions.filter((s) => !complete(s)) : []
  let stepError = ''
  if (failed) stepError = failed
  else if (kind === 'rever' && triedAll && problems.length)
    stepError =
      problems.length === 1
        ? 'Ainda não pode criar o workflow: falta corrigir 1 coisa, assinalada abaixo.'
        : `Ainda não pode criar o workflow: faltam corrigir ${problems.length} coisas, assinaladas abaixo.`
  else if (kind === 'percurso' && screenProblems.length)
    stepError = incomplete.length ? 'Para continuar, complete ' + (incomplete.length === 1 ? 'este passo:' : 'estes passos:') : screenProblems[0].message
  else if (kind === 'campos' && screenProblems.length)
    stepError = screenProblems.length === 1 ? screenProblems[0].message : `Para continuar, corrija as ${screenProblems.length} coisas assinaladas na lista.`
  else if (kind !== 'rever' && screenProblems.length)
    stepError =
      screenProblems.length === 1 ? 'Para continuar, corrija o que está assinalado.' : `Para continuar, corrija as ${screenProblems.length} coisas assinaladas.`

  let nextText = 'Continuar'
  if (kind === 'rever') nextText = 'Criar workflow'
  else if (kind === 'campo') nextText = 'Guardar o campo'
  else if (kind === 'pm' && volta === 'percurso') nextText = 'Guardar o passo'
  else if (kind === 'quem' && volta === 'percurso') nextText = 'Guardar e voltar ao percurso'
  else if (fromReview && startSection) nextText = 'Guardar e voltar à revisão'
  else if (order[order.indexOf(known) + 1] === 'rever') nextText = 'Rever o workflow'

  /** The Designer's options: who can request, or who can handle a step (UC05) */
  const groupCards = (radioName: string, chosen: string | null, pick: (g: string) => void, options: GroupOption[]) => (
    <div className="flex flex-wrap gap-2.5">
      {options.map((g) => (
        <label key={g.name} className={cardClass(chosen === g.name, 'min-h-16 flex-[1_1_300px]')}>
          <input type="radio" name={radioName} value={g.name} checked={chosen === g.name} onChange={() => pick(g.name)} className={cx(radioClass, 'mt-0.5')} />
          <span className="text-[16px] leading-[22px] font-semibold">
            {g.name} <span className="mt-0.5 block text-label-sm leading-[18px] font-normal text-ink-3">{g.note}</span>
          </span>
        </label>
      ))}
    </div>
  )

  // ——— the screens
  let body: ReactNode = null
  if (kind === 'ident') {
    const f = fieldState('name', 'ajuda-nome')
    body = (
      <div className="flex max-w-form flex-col gap-1.5">
        <label htmlFor={f.id} className="text-[16px] font-semibold">
          Nome do workflow <Req />
        </label>
        <span id="ajuda-nome" className="text-[14px] text-ink-3">
          Ex.: Pedido de material de escritório
        </span>
        <input
          id={f.id}
          type="text"
          autoComplete="off"
          required
          value={draft.name}
          onChange={(e) => change({ t: 'name', v: e.target.value })}
          aria-invalid={f.wrong || undefined}
          aria-describedby={f.desc}
          className={inputClass(f.wrong, 'px-3.5')}
        />
        {f.error && <FieldError id={'erro-' + f.id}>{f.error}</FieldError>}
      </div>
    )
  } else if (kind === 'quem') {
    const f = fieldState('steps[0].handledBy')
    body = (
      <fieldset id={f.id} tabIndex={-1} aria-describedby={f.desc} className="m-0 flex flex-col gap-2.5 border-0 p-0 outline-none">
        <legend className="sr-only">Quem pode fazer o pedido (obrigatório)</legend>
        <ArrowHint />
        {f.error && <FieldError id={'erro-' + f.id}>{f.error}</FieldError>}
        {groupCards('quem-pedido', request.handledBy, (g) => change({ t: 'step', step: request.id, k: 'handledBy', v: g }), groups.requesters)}
      </fieldset>
    )
  } else if (step && kind === 'pn') {
    const fn = fieldState(fieldOf(step) + '.name', 'ajuda-passo-nome')
    const fq = fieldState(fieldOf(step) + '.handledBy', 'ajuda-passo-quem')
    const final = isFinalValidation(step)
    body = (
      <div className="flex flex-col gap-6">
        <div className="flex flex-wrap items-center gap-2">
          <Chip tone={final ? 'ok' : 'info'} icon={final ? 'shieldCheck' : 'eye'}>
            {final ? 'Validação final' : 'Revisão'}
          </Chip>
        </div>
        <div className="flex max-w-[520px] flex-col gap-1.5">
          <label htmlFor={fn.id} className="text-[16px] font-semibold">
            Nome do passo <Req />
          </label>
          <span id="ajuda-passo-nome" className="text-[14px] text-ink-3">
            É o que aparece no percurso do pedido. Ex.: Validação da chefia
          </span>
          <input
            id={fn.id}
            type="text"
            autoComplete="off"
            required
            value={step.name}
            onChange={(e) => change({ t: 'step', step: step.id, k: 'name', v: e.target.value })}
            aria-invalid={fn.wrong || undefined}
            aria-describedby={fn.desc}
            className={inputClass(fn.wrong, 'px-3.5')}
          />
          {fn.error && <FieldError id={'erro-' + fn.id}>{fn.error}</FieldError>}
        </div>
        <fieldset id={fq.id} tabIndex={-1} aria-describedby={fq.desc} className="m-0 flex flex-col gap-2.5 border-0 p-0 outline-none">
          <legend className="mb-1 p-0 text-[16px] leading-[22px] font-semibold">
            Quem trata este passo? <Req />
            <span className="sr-only"> (obrigatório)</span>
          </legend>
          <ArrowHint />
          <span id="ajuda-passo-quem" className="text-[14px] leading-5 text-ink-3">
            Os grupos são os da organização, guardados na defiro.
          </span>
          {fq.error && <FieldError id={'erro-' + fq.id}>{fq.error}</FieldError>}
          {groupCards('quem-' + step.id, step.handledBy, (g) => change({ t: 'step', step: step.id, k: 'handledBy', v: g }), groups.handlers)}
        </fieldset>
      </div>
    )
  } else if (step && kind === 'pp') {
    const f = fieldState(fieldOf(step) + '.deadline', 'ajuda-prazo dias-uteis')
    const others = total - (step.deadline ?? 0)
    body = (
      <div className="flex flex-col gap-2.5">
        <label htmlFor={f.id} className="text-[16px] font-semibold">
          Prazo legal <Req />
        </label>
        <span id="ajuda-prazo" className="text-[14px] leading-5 text-ink-3">
          De 1 a 90 dias úteis. Conta a partir do momento em que o pedido chega a este passo.
        </span>
        <span className="flex items-center gap-3.5">
          <input
            id={f.id}
            type="text"
            inputMode="numeric"
            autoComplete="off"
            required
            maxLength={3}
            value={step.deadline === null ? '' : String(step.deadline)}
            onChange={(e) => change({ t: 'deadline', step: step.id, v: e.target.value })}
            aria-invalid={f.wrong || undefined}
            aria-describedby={f.desc}
            className={cx(
              'box-border h-16 w-30 rounded-item bg-panel px-4 text-center text-[28px] font-extrabold text-ink tabular-nums',
              f.wrong ? 'border-2 border-error' : 'border border-field',
            )}
          />
          <span id="dias-uteis" className="text-[20px] leading-7 font-semibold text-ink-2">
            dias úteis
          </span>
        </span>
        {f.error && <FieldError id={'erro-' + f.id}>{f.error}</FieldError>}
        <p aria-live="polite" className="edge mt-2.5 mb-0 flex items-start gap-2 rounded-md bg-soft px-4 py-3.5 text-[15px] leading-[22px]">
          <Icon name="clock" size={18} className="mt-px text-accent-ink" />
          <span>
            {step.deadline ? `Com este prazo, o percurso todo pode demorar até ${total} dias úteis.` : `Os outros passos somam ${others} dias úteis.`}
          </span>
        </p>
      </div>
    )
  } else if (step && kind === 'pm') {
    const fm = fieldState(fieldOf(step) + '.reasons')
    body = (
      <div className="flex flex-col gap-5">
        <div id={fm.id} tabIndex={-1} className="outline-none">
          {fm.error && <FieldError id={'erro-' + fm.id}>{fm.error}</FieldError>}
        </div>
        {(['returnReasons', 'rejectReasons'] as ReasonList[]).map((list) => {
          const forReturn = list === 'returnReasons'
          const addId = `add-${list}`
          return (
            <fieldset
              key={list}
              aria-describedby={'ajuda-' + list}
              className={cx(
                'm-0 flex flex-col gap-3 rounded-item border border-line-strong bg-panel px-5 pt-4.5 pb-5',
                forReturn ? 'shadow-[inset_4px_0_0_var(--color-warning)]' : 'shadow-[inset_4px_0_0_var(--color-error)]',
              )}
            >
              <legend className="float-left m-0 flex w-full items-center gap-2 p-0 text-[17px] leading-6 font-semibold">
                <span
                  aria-hidden="true"
                  className={cx('grid size-7 place-items-center rounded-full', forReturn ? 'bg-warning-soft text-warning' : 'bg-error-soft text-error')}
                >
                  <Icon name={forReturn ? 'reset' : 'x'} size={16} />
                </span>
                {forReturn ? 'Para devolver ao requerente, para corrigir' : 'Para rejeitar e encerrar o pedido'}
              </legend>
              <span id={'ajuda-' + list} className="text-[14px] leading-5 text-ink-2">
                {forReturn
                  ? 'O pedido volta para quem o fez, com o motivo e as notas. Ex.: Falta documento comprovativo'
                  : 'O pedido fica encerrado e não pode ser corrigido. Ex.: Pedido não autorizado'}
              </span>
              {step[list].length === 0 && (
                <p className="m-0 text-[14px] leading-5 text-ink-3">
                  {forReturn
                    ? 'Sem motivos para devolver: neste passo não se pode devolver o pedido.'
                    : 'Sem motivos para rejeitar: neste passo não se pode rejeitar o pedido.'}
                </p>
              )}
              <ul className="m-0 flex list-none flex-col gap-2 p-0">
                {step[list].map((reason, j) => {
                  const f = fieldState(`${fieldOf(step)}.${list}[${j}]`)
                  return (
                    <li key={j} className="flex flex-wrap items-center gap-2">
                      <label htmlFor={f.id} className="sr-only">
                        {forReturn ? 'Motivo para devolver' : 'Motivo para rejeitar'} {j + 1}
                      </label>
                      <input
                        id={f.id}
                        type="text"
                        required
                        placeholder={forReturn ? 'Ex.: Falta documento comprovativo' : 'Ex.: Pedido não autorizado'}
                        value={reason}
                        onChange={(e) => change({ t: 'renameReason', step: step.id, list, index: j, v: e.target.value })}
                        aria-invalid={f.wrong || undefined}
                        aria-describedby={f.desc}
                        className={cx(
                          'box-border h-12 min-w-0 flex-[1_1_260px] rounded-md bg-panel px-3.5 text-[16px] text-ink',
                          f.wrong ? 'border-2 border-error' : 'border border-field',
                        )}
                      />
                      <IconButton
                        icon="trash"
                        danger
                        label={`Remover o motivo «${reason}»`}
                        onClick={() => change({ t: 'removeReason', step: step.id, list, index: j }, addId)}
                      />
                      {f.error && (
                        <FieldError id={'erro-' + f.id} className="flex-[0_0_100%]">
                          {f.error}
                        </FieldError>
                      )}
                    </li>
                  )
                })}
              </ul>
              <button
                type="button"
                id={addId}
                className={cx(secondarySmall, 'self-start')}
                onClick={() => change({ t: 'addReason', step: step.id, list }, domId(`${fieldOf(step)}.${list}[${step[list].length}]`))}
              >
                <Icon name="plus" size={16} />
                {forReturn ? 'Acrescentar motivo para devolver' : 'Acrescentar motivo para rejeitar'}
              </button>
            </fieldset>
          )
        })}
      </div>
    )
  } else if (kind === 'percurso') {
    const hasFinal = steps.some(isFinalValidation)
    body = (
      <div className="flex flex-col gap-4.5">
        <ol aria-label="Passos do percurso" className="m-0 flex list-none flex-col gap-3.5 p-0">
          {steps.map((s, i) => {
            const first = isRequest(s)
            const ok = first ? !!s.handledBy : complete(s)
            return (
              <li key={s.id}>
                <article
                  id={'pc-' + s.id}
                  tabIndex={-1}
                  aria-labelledby={'pch-' + s.id}
                  className={cx(
                    'box-border flex w-full flex-col gap-3 rounded-item bg-panel px-5 py-4.5 outline-none',
                    ok ? 'border border-line-strong' : 'border-2 border-warning',
                  )}
                >
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
                    <span
                      aria-hidden="true"
                      className={cx(
                        'box-border grid size-9 shrink-0 place-items-center rounded-full text-[15px] font-extrabold',
                        ok ? 'border-2 border-orange bg-bg' : 'border-2 border-dashed border-warning bg-warning-soft',
                      )}
                    >
                      {i + 1}
                    </span>
                    <h3 id={'pch-' + s.id} className="m-0 flex-[1_1_200px] text-title-sm font-semibold">
                      <span className="sr-only">Passo {i + 1}: </span>
                      {stepName(s)}
                    </h3>
                    {ok ? (
                      <Chip tone="ok" icon="check">
                        Completo
                      </Chip>
                    ) : (
                      <Chip tone="warn" icon="alertTriangle">
                        Por completar
                      </Chip>
                    )}
                  </div>
                  <dl className="m-0 grid grid-cols-[minmax(110px,150px)_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-[15px] leading-[22px]">
                    <dt className="text-ink-2">{first ? 'Quem pode pedir' : 'Quem trata'}</dt>
                    <dd className={cx('m-0', !s.handledBy && 'font-semibold text-error')}>{s.handledBy || 'Falta escolher'}</dd>
                    {!first && (
                      <>
                        <dt className="text-ink-2">Prazo legal</dt>
                        <dd className={cx('m-0', !s.deadline && 'font-semibold text-error')}>{s.deadline ? s.deadline + ' dias úteis' : 'Falta indicar'}</dd>
                        <dt className="text-ink-2">Motivos</dt>
                        <dd className="m-0">
                          {s.returnReasons.length} para devolver · {s.rejectReasons.length} para rejeitar
                        </dd>
                      </>
                    )}
                  </dl>
                  <div className="flex flex-wrap items-center gap-2">
                    <button
                      type="button"
                      className={ok ? secondarySmall : primarySmall}
                      onClick={() => go(first ? 'quem' : 'pn:' + s.id, { volta: 'percurso' })}
                    >
                      <Icon name="pen" size={16} />
                      {ok ? 'Alterar ' : 'Completar '}
                      <span className="sr-only">o passo {stepName(s)}</span>
                    </button>
                    <span className="flex-1" />
                    {!first && (
                      <>
                        <IconButton
                          icon="arrowUp"
                          label={`Mover «${stepName(s)}» para cima`}
                          disabled={!canMove(steps, s.id, -1)}
                          onClick={() => change({ t: 'moveStep', step: s.id, by: -1 })}
                        />
                        <IconButton
                          icon="arrowDown"
                          label={`Mover «${stepName(s)}» para baixo`}
                          disabled={!canMove(steps, s.id, 1)}
                          onClick={() => change({ t: 'moveStep', step: s.id, by: 1 })}
                        />
                        <IconButton
                          icon="trash"
                          danger
                          label={`Remover o passo «${stepName(s)}»`}
                          onClick={() => change({ t: 'removeStep', step: s.id }, 'add-review')}
                        />
                      </>
                    )}
                  </div>
                </article>
              </li>
            )
          })}
        </ol>
        <div className="box-border flex w-full flex-col gap-2.5 rounded-item border-2 border-dashed border-line-strong px-5 py-4.5">
          <p className="m-0 text-[15px] leading-[22px] font-semibold">
            {hasFinal ? 'Acrescentar uma revisão antes da validação final' : 'Acrescentar um passo no fim'}
          </p>
          <div className="flex flex-wrap gap-2.5">
            <button type="button" id="add-review" className={secondaryMedium} onClick={() => addStep(REVIEW)}>
              <Icon name="plus" size={18} />
              {'Revisão '}
              <span className="sr-only">(alguém aprova, devolve ou rejeita)</span>
            </button>
            {!hasFinal && (
              <button type="button" className={secondaryMedium} onClick={() => addStep(FINAL_VALIDATION)}>
                <Icon name="plus" size={18} />
                {'Validação final '}
                <span className="sr-only">(confirma, fecha e gera o documento)</span>
              </button>
            )}
          </div>
          <p className="m-0 text-label-sm leading-[19px] text-ink-3">
            Revisão: alguém aprova, devolve ou rejeita. Validação final: confirma, fecha o procedimento e gera o documento final; só há uma e é sempre o último
            passo.
          </p>
        </div>
        <p className="m-0 flex items-center gap-2 text-[15px] leading-[22px] text-ink-2">
          <Icon name="clock" size={18} className="text-accent-ink" />
          Prazo total: {total} dias úteis. Disponível para: {request.handledBy || '—'}.
        </p>
      </div>
    )
  } else if (kind === 'campos') {
    body = <FieldList draft={draft} fields={fields} change={change} open={(q) => go('campo:' + q)} add={addField} />
  } else if (editing) {
    body = <FieldEditor question={editing} at={`steps[0].fields[${fields.indexOf(editing)}]`} fieldState={fieldState} change={change} />
  } else if (kind === 'rever') {
    const section = (key: string, title: string, rows: Array<{ rot: string; v: string }>, owns: (screen: string) => boolean) => {
      const errors = triedAll ? problems.filter((p) => owns(screenOf(p.field))) : []
      return (
        <section
          key={key}
          aria-labelledby={'rv-' + key}
          className={cx('flex flex-col gap-3 rounded-md bg-panel px-5 py-4.5', errors.length ? 'border-2 border-error' : 'border border-line')}
        >
          <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
            <h3 id={'rv-' + key} className="m-0 flex-[1_1_200px] text-[17px] leading-6 font-semibold">
              {title}
            </h3>
            <button type="button" className={secondarySmall} onClick={() => go(key, { fromReview: true })}>
              <Icon name="pen" size={16} />
              {errors.length ? 'Corrigir ' : 'Alterar '}
              <span className="sr-only">{title}</span>
            </button>
          </div>
          <dl className="m-0 grid grid-cols-[minmax(130px,220px)_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-[15px] leading-[22px]">
            {rows.map((r, i) => (
              <div key={i + r.rot} className="contents">
                <dt className="text-ink-2">{r.rot}</dt>
                <dd className="m-0 [overflow-wrap:anywhere]">{r.v}</dd>
              </div>
            ))}
          </dl>
          {errors.map((p) => (
            <p key={p.field + p.message} className="m-0">
              <FieldError>{p.message}</FieldError>
            </p>
          ))}
        </section>
      )
    }
    body = (
      <div className="flex max-w-reading flex-col gap-4">
        {section('ident', 'Nome', [{ rot: 'Nome', v: draft.name || '—' }], (s) => s === 'ident')}
        {section('quem', 'Quem pode pedir', [{ rot: 'Grupo ou unidade', v: request.handledBy || 'Falta escolher' }], (s) => s === 'quem')}
        {section(
          'percurso',
          'Percurso',
          decisions.map((s, i) => ({
            rot: `${i + 2}. ${stepName(s)}`,
            v: `${s.handledBy || 'Falta escolher quem trata'} · ${s.deadline ? s.deadline + ' dias úteis' : 'sem prazo'} · ${s.returnReasons.length} para devolver, ${s.rejectReasons.length} para rejeitar`,
          })),
          ofPath,
        )}
        {section(
          'campos',
          'Formulário do pedido',
          fields.map((q) => ({ rot: fieldTitle(q), v: `${answerTypeName(q.type)} · ${q.required ? 'obrigatório' : 'opcional'}` })),
          ofForm,
        )}
        <p className="m-0 flex items-start gap-2 text-[15px] leading-[23px] text-ink-2">
          <Icon name="send" size={16} className="mt-0.5 text-accent-ink" />
          <span>
            Prazo total: {total} dias úteis. Disponível para: {request.handledBy || '—'}. Fica logo disponível em «Novo pedido».
          </span>
        </p>
      </div>
    )
  }

  const question = questions[kind]
  return (
    <WizardLayout title={draft.name ? 'Novo workflow · ' + draft.name : 'Novo workflow'} pct={pct} exitTo={paths.workflows} exitSr="sem criar o workflow">
      <div className={cx('flex w-full flex-wrap items-start gap-x-10 gap-y-8', step ? 'max-w-[1180px]' : 'max-w-[900px]')}>
        <form
          noValidate
          aria-labelledby="passo-titulo"
          onSubmit={(e) => {
            e.preventDefault()
            void next()
          }}
          onKeyDown={(e) => enterMovesOn(e, () => void next(), kind === 'pm' || listScreen)}
          className="flex min-w-0 flex-[999_1_560px] flex-col gap-7"
        >
          <div className="flex max-w-reading flex-col gap-3">
            <p aria-hidden="true" className="m-0 flex flex-wrap items-center gap-x-2.5 gap-y-1 text-[14px] leading-5 font-semibold text-accent-ink">
              <span>{kicker[0]}</span>
              <span>·</span>
              <span>{kicker[1]}</span>
            </p>
            <h2 id="passo-titulo" tabIndex={-1} className="m-0 text-[32px] leading-10 font-extrabold tracking-question outline-none">
              <span className="sr-only">{kicker.join(', ')}: </span>
              {question.q}
            </h2>
            <p className="m-0 text-[17px] leading-[27px] text-ink-2">{question.ajuda}</p>
          </div>
          {stepError && (
            <StepError
              extra={
                incomplete.length > 0 && (
                  <ul className="m-0 flex list-none flex-col gap-2 p-0 pl-7.5">
                    {incomplete.map((s) => (
                      <li key={s.id} className="flex flex-wrap items-center gap-x-3 gap-y-2 text-[15px] leading-[22px] text-ink">
                        <span className="flex-[1_1_260px]">O passo «{stepName(s)}» está por completar.</span>
                        <button type="button" className={secondarySmall} onClick={() => go('pn:' + s.id, { volta: 'percurso' })}>
                          <Icon name="pen" size={16} />
                          {'Completar '}
                          <span className="sr-only">{stepName(s)}</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )
              }
            >
              {stepError}
            </StepError>
          )}
          {body}
          <WizardFooter
            previous={known !== 'ident' || fromReview ? previous : undefined}
            previousText={fromReview && startSection ? 'Voltar à revisão' : 'Anterior'}
            next={() => void next()}
            nextText={nextText}
            last={kind === 'rever'}
            enterHint={kind !== 'pm' && !listScreen}
          />
        </form>

        {step && (
          <aside
            aria-labelledby="titulo-percurso-mini"
            className="sticky top-6 flex max-w-full flex-[1_1_280px] flex-col gap-3 rounded-lg border border-line-strong bg-tint px-5 pt-5 pb-4"
          >
            <h3 id="titulo-percurso-mini" className="m-0 text-[16px] leading-[22px] font-semibold">
              O percurso até agora
            </h3>
            <ol className="m-0 flex list-none flex-col p-0">
              {steps.map((s, i) => {
                const on = s.id === step.id
                const ok = isRequest(s) ? !!s.handledBy : complete(s)
                return (
                  <li key={s.id} aria-current={on ? 'step' : undefined} className="flex gap-3">
                    <div aria-hidden="true" className="flex w-7 shrink-0 flex-col items-center">
                      <span
                        className={cx(
                          'grid size-7 place-items-center rounded-full text-label-sm font-extrabold',
                          on
                            ? 'bg-brand text-on-brand'
                            : ok
                              ? 'bg-bg shadow-[inset_0_0_0_2px_var(--color-orange)]'
                              : 'bg-bg shadow-[inset_0_0_0_2px_var(--color-line-strong)]',
                        )}
                      >
                        {i + 1}
                      </span>
                      {i < steps.length - 1 && <span className="min-h-2.5 w-0.5 flex-1 bg-line-strong" />}
                    </div>
                    <div
                      className={cx(
                        'mb-2.5 flex min-w-0 flex-1 flex-col rounded-tile px-2.5 py-1.5',
                        on && 'bg-accent-soft shadow-[inset_0_0_0_1.5px_var(--color-orange)]',
                      )}
                    >
                      <span className="text-[14px] leading-5 font-semibold">
                        <span className="sr-only">{on ? 'A editar agora: ' : ok ? 'Completo: ' : 'Por completar: '}</span>
                        {stepName(s)}
                      </span>
                      <span className={cx('text-label-sm leading-[18px]', s.handledBy ? 'text-ink-2' : 'text-warning')}>
                        {s.handledBy || 'Falta escolher quem trata'}
                      </span>
                    </div>
                  </li>
                )
              })}
            </ol>
            <p className="m-0 text-label-sm leading-[19px] text-ink-3">Depois de completar os passos, pode mudar a ordem ou acrescentar outros.</p>
          </aside>
        )}
      </div>
    </WizardLayout>
  )
}
