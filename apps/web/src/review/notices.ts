import type { Notice } from '../components/Flash'

/**
 * What a page passes to the next one in the router's state: the notice to show there and, after an
 * approval, the task whose approval «Desfazer» reverses.
 */
export type ReviewState = { notice?: Notice; undo?: { task: string; reference: string } }

/** The prototype's text, read out to screen readers; closing the procedure is UC22's */
export const approvedNotice = (reference: string, nextStep: string | null, handledBy: string | null): Notice => ({
  tom: 'ok',
  titulo: 'Pedido aprovado',
  texto: nextStep ? `${reference} seguiu para o passo «${nextStep}», com ${handledBy}.` : undefined,
})

export const undoneNotice: Notice = { tom: 'ok', titulo: 'Aprovação desfeita' }

/** The server's refusal, as the person is shown it: someone acted first, or it is too late to undo */
export const refusedNotice = (reason: string): Notice => ({ tom: 'warn', titulo: reason })

// When the server cannot be reached; worded as the sign-out failure (AppShell).
export const undoFailed: Notice = { tom: 'bad', titulo: 'Não foi possível desfazer', texto: 'Tente outra vez daqui a pouco.' }
export const approveFailed: Notice = { tom: 'bad', titulo: 'Não foi possível aprovar', texto: 'Tente outra vez daqui a pouco.' }

/** «22/11/2026, 18:32», in the organization's time */
export const dateTime = (iso: string) => new Date(iso).toLocaleString('pt-PT', { timeZone: 'Europe/Lisbon', dateStyle: 'short', timeStyle: 'short' })
