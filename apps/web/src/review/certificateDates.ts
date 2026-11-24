/** The reason that fills in the message with the dates (UC18 #6), as the workflow names it */
export const DATES_REASON = 'Datas do certificado não coincidem'

const date = /\d{2}\/\d{2}\/\d{4}/g

/**
 * The prototype's message when the certificate's dates are not the trip's, or nothing when they
 * match or one is missing. The answers are found by their labels, as the request form has no type
 * for a certificate's dates (open question for UC04, UC13 and UC22).
 */
export function datesMessage(answers: { label: string; value: string }[]): string {
  const datesOf = (label: string): string[] => answers.find((a) => a.label === label)?.value.match(date) ?? []
  const certificate = datesOf('Datas do certificado')
  const start = certificate.at(0)
  const end = certificate.at(-1)
  const departure = datesOf('Partida').at(0)
  const back = datesOf('Regresso').at(0)
  if (!start || !end || !departure || !back) return ''
  if (start === departure && end === back) return ''
  return (
    `O certificado indica ${start} a ${end} e a deslocação foi de ${departure} a ${back}.` + ' Junte o certificado correto ou corrija as datas da deslocação.'
  )
}
