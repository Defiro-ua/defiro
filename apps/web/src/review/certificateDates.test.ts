import { describe, expect, it } from 'vitest'
import { datesMessage } from './certificateDates'

const answers = (dates: Record<string, string>) => Object.entries(dates).map(([label, value]) => ({ label, value }))
const trip = { Partida: '08/11/2026 07:30', Regresso: '10/11/2026 19:40' }

describe('the dates that do not match', () => {
  it('says the dates of the certificate and of the trip', () => {
    expect(datesMessage(answers({ ...trip, 'Datas do certificado': '08/11/2026 a 09/11/2026' }))).toBe(
      'O certificado indica 08/11/2026 a 09/11/2026 e a deslocação foi de 08/11/2026 a 10/11/2026. Junte o certificado correto ou corrija as datas da deslocação.',
    )
  })

  it('takes a certificate of one day as starting and ending on it', () => {
    expect(datesMessage(answers({ ...trip, 'Datas do certificado': '08/11/2026' }))).toMatch(/^O certificado indica 08\/11\/2026 a 08\/11\/2026 e /)
  })

  it('notices when only the start differs', () => {
    expect(datesMessage(answers({ ...trip, 'Datas do certificado': '09/11/2026 a 10/11/2026' }))).not.toBe('')
  })

  it('says nothing when they match', () => {
    expect(datesMessage(answers({ ...trip, 'Datas do certificado': '08/11/2026 a 10/11/2026' }))).toBe('')
  })

  it.each(['Partida', 'Regresso', 'Datas do certificado'])('says nothing without «%s», or with no date in it', (label) => {
    const all: Record<string, string> = { ...trip, 'Datas do certificado': '08/11/2026 a 09/11/2026' }
    expect(datesMessage(answers({ ...all, [label]: '—' }))).toBe('')
    delete all[label]
    expect(datesMessage(answers(all))).toBe('')
  })
})
