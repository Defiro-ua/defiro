import type { Notice } from '../components/Flash'

/**
 * What a page passes to the next one in the router's state: the notice to show there and, after a
 * decision, the task whose decision «Desfazer» reverses.
 */
export type ReviewState = { notice?: Notice; undo?: { task: string; reference: string } }

/** The prototype's text, read out to screen readers; closing the procedure is UC22's */
export const approvedNotice = (reference: string, nextStep: string | null, handledBy: string | null): Notice => ({
  tom: 'ok',
  titulo: 'Pedido aprovado',
  texto: nextStep ? `${reference} seguiu para o passo «${nextStep}», com ${handledBy}.` : undefined,
})

const reasonsText = (reasons: string[]) => `${reasons.length === 1 ? 'Motivo' : 'Motivos'}: ${reasons.join('; ')}.`

/** The reasons are shown too, unlike the other notices' details (UC18 #8) */
export const returnedNotice = (reference: string, requester: string, reasons: string[]): Notice => ({
  tom: 'warn',
  titulo: 'Pedido devolvido para correção',
  texto: `${reference} voltou para ${requester}.`,
  detalhe: reasonsText(reasons),
})

export const rejectedNotice = (reference: string, reasons: string[]): Notice => ({
  tom: 'bad',
  titulo: 'Pedido rejeitado',
  texto: `${reference} foi encerrado.`,
  detalhe: reasonsText(reasons),
})

const undone: Record<string, string> = { approved: 'Aprovação desfeita', returned: 'Devolução desfeita', rejected: 'Rejeição desfeita' }

export const undoneNotice = (decision: string): Notice => ({ tom: 'ok', titulo: undone[decision] })

/** The server's refusal, as the person is shown it: someone acted first, or it is too late to undo */
export const refusedNotice = (reason: string): Notice => ({ tom: 'warn', titulo: reason })

// When the server cannot be reached; worded as the sign-out failure (AppShell).
export const undoFailed: Notice = { tom: 'bad', titulo: 'Não foi possível desfazer', texto: 'Tente outra vez daqui a pouco.' }
export const approveFailed: Notice = { tom: 'bad', titulo: 'Não foi possível aprovar', texto: 'Tente outra vez daqui a pouco.' }
export const returnFailed: Notice = { tom: 'bad', titulo: 'Não foi possível devolver o pedido', texto: 'Tente outra vez daqui a pouco.' }
export const rejectFailed: Notice = { tom: 'bad', titulo: 'Não foi possível rejeitar o pedido', texto: 'Tente outra vez daqui a pouco.' }
export const takeFailed: Notice = { tom: 'bad', titulo: 'Não foi possível abrir a tarefa', texto: 'Tente outra vez daqui a pouco.' }
export const releaseFailed: Notice = { tom: 'bad', titulo: 'Não foi possível libertar a tarefa', texto: 'Tente outra vez daqui a pouco.' }

export const documentFailed: Notice = { tom: 'bad', titulo: 'Não foi possível abrir o documento', texto: 'Tente outra vez daqui a pouco.' }

export const savedNotice = (filename: string): Notice => ({ tom: 'ok', titulo: 'PDF guardado', texto: `${filename} foi guardado.` })

export const releasedNotice: Notice = { tom: 'ok', titulo: 'Tarefa libertada' }

/** «22/11/2026», in the organization's time */
export function date(iso: string) {
  return new Date(iso).toLocaleDateString('pt-PT', { timeZone: 'Europe/Lisbon', day: '2-digit', month: '2-digit', year: 'numeric' })
}

/** «22/11/2026 18:32», in the organization's time, as the prototype shows dates (fmtDateTime) */
export function dateTime(iso: string) {
  const at = new Date(iso)
  const time = at.toLocaleTimeString('pt-PT', { timeZone: 'Europe/Lisbon', hour: '2-digit', minute: '2-digit' })
  return `${date(iso)} ${time}`
}
