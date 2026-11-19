/** A plugin at one version: what a step of a workflow runs (the Registry's catalogue) */
export type PluginRef = { name: string; version: string }

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

export const isRequest = (s: Step) => s.plugin.name === REQUEST
export const isFinalValidation = (s: Step) => s.plugin.name === FINAL_VALIDATION

/** Every change to a draft, as a pure function: the browser edits the draft, the Designer keeps it */
export function changeDraft(d: Draft, c: DraftChange): Draft {
  const steps = d.steps
  const update = (id: string, f: (s: Step) => Step) => ({ ...d, steps: steps.map((s) => (s.id === id ? f(s) : s)) })
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
  return problems
}

/** How long the whole path can take, in working days */
export const totalDeadline = (d: Draft) => d.steps.reduce((total, s) => total + (s.deadline ?? 0), 0)
