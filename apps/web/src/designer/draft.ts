/** A plugin at one version: what a step of a workflow runs (the Registry's catalogue) */
export type PluginRef = { name: string; version: string }

/** The answer types of a question (docs/workflow-specification.md) */
export type FieldType = 'short-text' | 'long-text' | 'number' | 'date' | 'date-time' | 'single-choice' | 'yes-no' | 'files'

/** One question of the request form (UC04): name, unit and manager are never questions, they come from the profile */
export type Field = {
  id: string
  /** The question, as the requester reads it */
  label: string
  type: FieldType
  required: boolean
  /** The answers to choose from, one per line as written, for a single choice */
  options: string[]
}

/** The cards of the answer types, in the order the designer shows them */
export const answerTypes: Array<{ type: FieldType; name: string; example: string }> = [
  { type: 'short-text', name: 'Texto curto', example: 'Uma linha. Ex.: local, matrícula' },
  { type: 'long-text', name: 'Texto longo', example: 'Várias linhas. Ex.: justificação' },
  { type: 'number', name: 'Número', example: 'Só algarismos. Ex.: quantidade' },
  { type: 'date', name: 'Data', example: 'Dia, mês e ano' },
  { type: 'date-time', name: 'Data e hora', example: 'Dia, mês, ano e hora' },
  { type: 'single-choice', name: 'Escolha única', example: 'Uma de várias opções que define' },
  { type: 'yes-no', name: 'Sim ou não', example: 'Duas opções: sim ou não' },
  { type: 'files', name: 'Ficheiros', example: 'Documentos que o requerente junta' },
]

/** How a question is named in the lists: by its label, or as a question with none */
export const fieldTitle = (q: Field) => q.label.trim() || 'Campo sem rótulo'

export const answerTypeName = (type: FieldType) => answerTypes.find((a) => a.type === type)?.name ?? type

/** What the form fills in from the requester's profile: shown, never asked */
export const profileFields = ['Nome', 'Unidade orgânica', 'Chefia direta']

/** One step of the path, as the Designer keeps it (GET and PUT /api/designer/drafts/{id}) */
export type Step = {
  id: string
  plugin: PluginRef
  name: string
  /** The group or unit that handles the step; for the request, who can make it */
  handledBy: string | null
  /** Legal deadline in working days; the request has none */
  deadline: number | null
  returnReasons: string[]
  rejectReasons: string[]
  /** The questions of the request form; only the request has them */
  fields: Field[]
}

export type Draft = { name: string; steps: Step[] }

/** A missing or invalid answer: the field it belongs to and what to tell the Workflow Manager */
export type Problem = { field: string; message: string }

export type ReasonList = 'returnReasons' | 'rejectReasons'

export const REQUEST = 'submission'
export const REVIEW = 'review'
export const FINAL_VALIDATION = 'final-validation'

export type DraftChange =
  | { t: 'name'; v: string }
  | { t: 'step'; step: string; k: 'name' | 'handledBy'; v: string }
  | { t: 'deadline'; step: string; v: string }
  | { t: 'addStep'; id: string; plugin: PluginRef }
  | { t: 'removeStep'; step: string }
  | { t: 'moveStep'; step: string; by: -1 | 1 }
  | { t: 'addReason'; step: string; list: ReasonList }
  | { t: 'renameReason'; step: string; list: ReasonList; index: number; v: string }
  | { t: 'removeReason'; step: string; list: ReasonList; index: number }
  | { t: 'addField'; id: string }
  | { t: 'field'; field: string; patch: Partial<Omit<Field, 'id' | 'options'>> }
  | { t: 'options'; field: string; v: string }
  | { t: 'moveField'; field: string; by: -1 | 1 }
  | { t: 'removeField'; field: string }

export const isRequest = (s: Step) => s.plugin.name === REQUEST
export const isFinalValidation = (s: Step) => s.plugin.name === FINAL_VALIDATION

/** Every change to a draft, as a pure function: the browser edits the draft, the Designer keeps it */
export function changeDraft(d: Draft, c: DraftChange): Draft {
  const steps = d.steps
  const update = (id: string, f: (s: Step) => Step) => ({ ...d, steps: steps.map((s) => (s.id === id ? f(s) : s)) })
  const form = (f: (fields: Field[]) => Field[]) => ({ ...d, steps: steps.map((s) => (isRequest(s) ? { ...s, fields: f(s.fields) } : s)) })
  const question = (id: string, f: (q: Field) => Field) => form((fields) => fields.map((q) => (q.id === id ? f(q) : q)))
  switch (c.t) {
    case 'name':
      return { ...d, name: c.v }
    case 'step':
      return update(c.step, (s) => ({ ...s, [c.k]: c.v }))
    case 'deadline': {
      const digits = c.v.replace(/\D/g, '')
      return update(c.step, (s) => ({ ...s, deadline: digits ? Number(digits) : null }))
    }
    case 'addStep':
      return { ...d, steps: withStep(steps, c.id, c.plugin) }
    case 'removeStep':
      return { ...d, steps: steps.filter((s) => s.id !== c.step || isRequest(s)) }
    case 'moveStep':
      return { ...d, steps: moved(steps, c.step, c.by) }
    case 'addReason':
      return update(c.step, (s) => ({ ...s, [c.list]: [...s[c.list], ''] }))
    case 'renameReason':
      return update(c.step, (s) => ({ ...s, [c.list]: s[c.list].map((r, i) => (i === c.index ? c.v : r)) }))
    case 'removeReason':
      return update(c.step, (s) => ({ ...s, [c.list]: s[c.list].filter((_, i) => i !== c.index) }))
    case 'addField':
      return form((fields) => [...fields, { id: c.id, label: '', type: 'short-text', required: false, options: [] }])
    case 'field':
      return question(c.field, (q) => ({ ...q, ...c.patch }))
    case 'options':
      return question(c.field, (q) => ({ ...q, options: c.v.split(/\r?\n/) }))
    case 'moveField':
      return form((fields) => movedField(fields, c.field, c.by))
    case 'removeField':
      return form((fields) => fields.filter((q) => q.id !== c.field))
  }
}

/** The final validation closes the procedure: there is at most one, and it stays the last step */
function withStep(steps: Step[], id: string, plugin: PluginRef): Step[] {
  const final = steps.findIndex(isFinalValidation)
  const finalValidation = plugin.name === FINAL_VALIDATION
  if (finalValidation && final >= 0) return steps
  const step: Step = {
    id,
    plugin,
    name: finalValidation ? 'Validação final' : 'Revisão',
    handledBy: null,
    deadline: null,
    returnReasons: ['Preenchido incorretamente'],
    rejectReasons: ['Pedido não autorizado'],
    fields: [],
  }
  const at = final >= 0 ? final : steps.length
  return [...steps.slice(0, at), step, ...steps.slice(at)]
}

/** A review step swaps with its neighbour, if that neighbour is a review step too */
function moved(steps: Step[], id: string, by: -1 | 1): Step[] {
  const from = steps.findIndex((s) => s.id === id)
  const to = from + by
  const isReview = (i: number) => i >= 0 && i < steps.length && steps[i].plugin.name === REVIEW
  if (from < 0 || !isReview(from) || !isReview(to)) return steps
  const path = [...steps]
  ;[path[from], path[to]] = [path[to], path[from]]
  return path
}

export const canMove = (steps: Step[], id: string, by: -1 | 1) => moved(steps, id, by) !== steps

function movedField(fields: Field[], id: string, by: -1 | 1): Field[] {
  const from = fields.findIndex((q) => q.id === id)
  const to = from + by
  if (from < 0 || to < 0 || to >= fields.length) return fields
  const form = [...fields]
  ;[form[from], form[to]] = [form[to], form[from]]
  return form
}

export const canMoveField = (d: Draft, id: string, by: -1 | 1) => {
  const fields = d.steps.find(isRequest)?.fields ?? []
  return movedField(fields, id, by) !== fields
}

/** What is wrong with a question, in a few words for the list of questions; empty when nothing is */
export function fieldProblem(q: Field): string {
  if (blank(q.label)) return 'Falta o rótulo'
  if (q.type === 'single-choice' && q.options.filter((o) => !blank(o)).length < 2) return 'Faltam opções de resposta (pelo menos duas)'
  return ''
}

const FORM = 'Formulário do pedido'

/** The request form asks at least one question, and each has a label and, for a single choice, two options */
function problemsOfForm(fields: Field[]): Problem[] {
  if (!fields.length)
    return [
      {
        field: 'steps[0].fields',
        message: `${FORM}: ainda não tem perguntas para quem faz o pedido… crie pelo menos uma pergunta, por exemplo «Material pedido»`,
      },
    ]
  return fields.flatMap((q, j) => {
    const at = `steps[0].fields[${j}]`
    const where = `${FORM}, campo ${j + 1}${blank(q.label) ? '' : ` («${q.label}»)`}: `
    const problems: Problem[] = []
    if (blank(q.label)) problems.push({ field: `${at}.label`, message: where + 'falta o rótulo.' })
    if (q.type === 'single-choice' && q.options.filter((o) => !blank(o)).length < 2)
      problems.push({ field: `${at}.options`, message: where + 'faltam opções de resposta (pelo menos duas).' })
    return problems
  })
}

const blank = (text: string | null | undefined) => !text || !text.trim()

/**
 * The same checks the Designer makes when publishing, so the Workflow Manager sees each problem
 * next to its field before going on. The Designer stays the judge: it checks again.
 */
export function problemsOf(d: Draft): Problem[] {
  const problems: Problem[] = []
  if (blank(d.name)) problems.push({ field: 'name', message: 'Dê um nome ao workflow.' })
  d.steps.forEach((s, k) => {
    const field = `steps[${k}]`
    const where = `Passo ${k + 1}${blank(s.name) ? '' : ` («${s.name}»)`}: `
    if (blank(s.name)) problems.push({ field: `${field}.name`, message: where + 'falta o nome.' })
    if (blank(s.handledBy)) problems.push({ field: `${field}.handledBy`, message: where + 'escolha quem trata.' })
    if (isRequest(s)) return
    if (s.deadline === null) problems.push({ field: `${field}.deadline`, message: where + 'indique o prazo legal, de 1 a 90 dias úteis.' })
    else if (s.deadline < 1 || s.deadline > 90) problems.push({ field: `${field}.deadline`, message: where + 'o prazo legal tem de ser de 1 a 90 dias úteis.' })
    if (!s.returnReasons.length && !s.rejectReasons.length)
      problems.push({ field: `${field}.reasons`, message: where + 'acrescente pelo menos um motivo para devolver ou rejeitar.' })
    const unnamed = (list: ReasonList, kind: string) =>
      s[list].forEach((r, j) => {
        if (blank(r)) problems.push({ field: `${field}.${list}[${j}]`, message: `${where}dê um nome ao motivo de ${kind} ${j + 1}.` })
      })
    unnamed('returnReasons', 'devolução')
    unnamed('rejectReasons', 'rejeição')
  })
  const request = d.steps.find(isRequest)
  if (request) problems.push(...problemsOfForm(request.fields))
  return problems
}

/** How long the whole path can take, in working days */
export const totalDeadline = (d: Draft) => d.steps.reduce((total, s) => total + (s.deadline ?? 0), 0)
