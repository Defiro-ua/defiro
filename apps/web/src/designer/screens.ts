import { isRequest, type Draft } from './draft'

/**
 * The designer asks one question at a time. Its screens are «Nome», «Quem pode pedir», «Percurso»
 * (with three screens for each step: who handles it, its deadline and its reasons), «Formulário»
 * (with a screen for each question, UC04) and «Rever e criar».
 */
export type Kind = 'ident' | 'quem' | 'pn' | 'pp' | 'pm' | 'percurso' | 'campos' | 'campo' | 'rever'

export const SCREENS = 5

/**
 * The screens in the order «Continuar» goes through them: `pn:<step id>` is a step's first screen.
 * A question's screen, `campo:<field id>`, opens from «Formulário» and goes back to it.
 */
export function screenOrder(d: Draft): string[] {
  const steps = d.steps.filter((s) => !isRequest(s))
  return ['ident', 'quem', ...steps.flatMap((s) => [`pn:${s.id}`, `pp:${s.id}`, `pm:${s.id}`]), 'percurso', 'campos', 'rever']
}

const fieldsOf = (d: Draft) => d.steps.find(isRequest)?.fields ?? []

/** Whether the draft has this screen: a step's or a question's screen goes when they go */
export function isScreen(d: Draft, screen: string): boolean {
  const { kind, step } = kindOf(screen)
  if (kind === 'campo') return fieldsOf(d).some((q) => q.id === step)
  return screenOrder(d).includes(screen)
}

export function kindOf(screen: string): { kind: Kind; step: string } {
  const [kind, step = ''] = screen.split(':')
  return { kind: kind as Kind, step }
}

/** «Passo X de 5»: the steps' own screens are part of «Percurso», the questions' part of «Formulário» */
export function numberOf(kind: Kind): number {
  if (kind === 'ident') return 1
  if (kind === 'quem') return 2
  if (kind === 'campos' || kind === 'campo') return 4
  if (kind === 'rever') return 5
  return 3
}

export const ofPath = (screen: string) => /^(pn|pp|pm|percurso)/.test(screen)

export const ofForm = (screen: string) => /^campo/.test(screen)

/** The screen where a problem's field is answered (the fields are the Designer's: steps[1].deadline) */
export function screenOfField(d: Draft, field: string): string {
  if (field === 'name') return 'ident'
  const question = /^steps\[\d+\]\.fields(?:\[(\d+)\])?/.exec(field)
  if (question) {
    const q = question[1] === undefined ? undefined : fieldsOf(d)[Number(question[1])]
    return q ? `campo:${q.id}` : 'campos'
  }
  const m = /^steps\[(\d+)\]\.(\w+)/.exec(field)
  const step = m ? d.steps[Number(m[1])] : undefined
  if (!m || !step) return 'percurso'
  if (isRequest(step)) return 'quem'
  if (m[2] === 'name' || m[2] === 'handledBy' || m[2] === 'plugin') return `pn:${step.id}`
  if (m[2] === 'deadline') return `pp:${step.id}`
  return `pm:${step.id}`
}

/** The element id of a field, for its error and for the focus: steps[1].deadline is f-steps-1-deadline */
export const domId = (field: string) => 'f-' + field.replace(/\W+/g, '-').replace(/-$/, '')
