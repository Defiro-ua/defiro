import { useEffect, useState } from 'react'
import { listMyRequests, type MyRequest } from '../api/engine'
import { Button } from '../components/Button'
import { Chip } from '../components/Chip'
import type { Tone } from '../components/Flash'
import type { IconName } from '../components/Icon'
import { date } from '../review/notices'

const states: Record<string, { tone: Tone; icon: IconName; text: string }> = {
  in_progress: { tone: 'info', icon: 'clock', text: 'Em curso' },
  returned: { tone: 'warn', icon: 'alertTriangle', text: 'Devolvido' },
  rejected: { tone: 'bad', icon: 'x', text: 'Rejeitado' },
  closed: { tone: 'ok', icon: 'check', text: 'Fechado' },
}

function detail(request: MyRequest) {
  if (request.status === 'closed' && request.closedAt) return `Fechado a ${date(request.closedAt)}`
  return request.currentStep
}

/**
 * «Os meus pedidos» (UC23): the requests this person made, and the state of each. A closed one
 * offers «Documento», which asks the page to show its final document.
 */
export function MyRequests({ onDocument }: { onDocument: (reference: string) => void }) {
  const [requests, setRequests] = useState<MyRequest[] | null | 'failed'>(null)

  useEffect(() => {
    let current = true
    listMyRequests().then(
      (list) => current && setRequests(list),
      () => current && setRequests('failed'),
    )
    return () => {
      current = false
    }
  }, [])

  if (requests === 'failed') {
    return (
      <p role="alert" className="m-0 font-semibold text-error">
        Não foi possível ler os seus pedidos. Recarregue a página daqui a pouco.
      </p>
    )
  }
  if (requests === null || requests.length === 0) return null

  return (
    <section aria-labelledby="titulo-meus" className="flex flex-col gap-4">
      <h2 id="titulo-meus" className="m-0 text-title-lg font-semibold">
        Os meus pedidos
      </h2>
      <div className="overflow-x-auto rounded-md border border-line bg-panel">
        <table className="w-full min-w-[640px] border-collapse text-[15px] leading-[22px]">
          <caption className="sr-only">Os meus pedidos e o estado de cada um</caption>
          <thead>
            <tr className="bg-soft text-left">
              <th scope="col" className="px-5 py-3 text-label-sm font-semibold text-ink-2">
                Pedido
              </th>
              <th scope="col" className="px-4 py-3 text-label-sm font-semibold text-ink-2">
                Estado
              </th>
              <th scope="col" className="px-5 py-3 text-right text-label-sm font-semibold text-ink-2">
                Ações
              </th>
            </tr>
          </thead>
          <tbody>
            {requests.map((request) => {
              const state = states[request.status] ?? { tone: 'neutro' as Tone, icon: undefined, text: request.status }
              return (
                <tr key={request.reference} className="border-t border-line">
                  <th scope="row" className="px-5 py-3.5 text-left align-top font-normal">
                    <span className="block font-semibold">{request.workflow}</span>
                    <span className="block text-label-sm text-ink-3 tabular-nums">{request.reference}</span>
                  </th>
                  <td className="px-4 py-3.5 align-top">
                    <Chip tone={state.tone} icon={state.icon}>
                      {state.text}
                    </Chip>
                    <span className="mt-1 block text-label-sm leading-[19px] text-ink-3">{detail(request)}</span>
                  </td>
                  <td className="px-5 py-2 text-right align-top">
                    {request.hasDocument && (
                      <Button size="s" icon="download" onClick={() => onDocument(request.reference)}>
                        Documento <span className="sr-only">do pedido {request.reference}</span>
                      </Button>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </section>
  )
}
