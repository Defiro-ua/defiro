import { describe, expect, it } from 'vitest'
import { approvedNotice, dateTime } from './notices'

describe('the review notices and dates', () => {
  it('shows a date and time as the prototype does, in the organization’s time', () => {
    expect(dateTime('2026-11-22T18:32:00Z')).toBe('22/11/2026 18:32')
    // Summer time in Lisbon is an hour ahead of UTC
    expect(dateTime('2026-07-01T08:05:00Z')).toBe('01/07/2026 09:05')
  })

  it('tells screen readers where the approved request went', () => {
    expect(approvedNotice('AC-2026-0147', 'Verificação financeira', 'Divisão Financeira')).toEqual({
      tom: 'ok',
      titulo: 'Pedido aprovado',
      texto: 'AC-2026-0147 seguiu para o passo «Verificação financeira», com Divisão Financeira.',
    })
    expect(approvedNotice('AC-2026-0147', null, null).texto).toBeUndefined()
  })
})
