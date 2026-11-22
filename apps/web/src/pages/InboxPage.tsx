import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { listInbox, undoApproval, type InboxItem } from '../api/engine'
import { Button } from '../components/Button'
import { Flash, type Notice } from '../components/Flash'
import { Icon } from '../components/Icon'
import { AppShell } from '../components/layout/AppShell'
import { useFocusOnArrival } from '../lib/useFocusOnArrival'
import { useProfile } from '../lib/useProfile'
import { refusedNotice, undoFailed, undoneNotice, type ReviewState } from '../review/notices'
import { useArrival } from '../review/useArrival'
import { paths } from '../routes'

/**
 * TEMPORARY, for UC27 (Daniel): the least of the task inbox that UC17 needs, the requests waiting
 * for this person, each with «Rever». After an approval it shows «Pedido aprovado» with «Desfazer».
 * UC27 builds the real inbox, with deadlines and the person's own requests.
 */
export function InboxPage() {
  const profile = useProfile()
  const navigate = useNavigate()
  const headingRef = useRef<HTMLHeadingElement>(null)
  useFocusOnArrival(headingRef)
  const arrived = useArrival()
  const [notice, setNotice] = useState<Notice | null>(arrived.notice ?? null)
  const [undo, setUndo] = useState(arrived.undo)
  const undoing = useRef(false)
  const [items, setItems] = useState<InboxItem[] | null | 'failed'>(null)

  useEffect(() => {
    let current = true
    listInbox().then(
      (list) => current && setItems(list),
      () => current && setItems('failed'),
    )
    return () => {
      current = false
    }
  }, [])

  async function undoIt() {
    // A second click while the first is on its way undoes nothing more
    if (!undo || undoing.current) return
    undoing.current = true
    let outcome
    try {
      outcome = await undoApproval(undo.task)
    } catch {
      undoing.current = false
      setNotice(undoFailed)
      return
    }
    if (outcome.done) {
      const state: ReviewState = { notice: undoneNotice }
      navigate(paths.request(undo.reference), { state })
    } else {
      setUndo(undefined)
      setNotice(refusedNotice(outcome.reason))
    }
  }

  const n = Array.isArray(items) ? items.length : 0

  return (
    <AppShell profile={profile} active="inbox" mainClassName="max-w-content gap-7">
      <Flash
        notice={notice}
        onClose={() => {
          setNotice(null)
          setUndo(undefined)
        }}
        onUndo={undo ? undoIt : undefined}
      />
      <h1 ref={headingRef} tabIndex={-1} className="m-0 text-headline-lg outline-none">
        Caixa de tarefas
      </h1>
      {items === null && (
        <p role="status" className="m-0 text-ink-2">
          A carregar…
        </p>
      )}
      {items === 'failed' && (
        <p role="alert" className="m-0 font-semibold text-error">
          Não foi possível ler as tarefas. Recarregue a página daqui a pouco.
        </p>
      )}
      {Array.isArray(items) && (
        <section aria-labelledby="titulo-tarefas" className="flex flex-col gap-4">
          <h2 id="titulo-tarefas" className="m-0 text-title-lg font-semibold">
            Tarefas à sua espera · {n}
          </h2>
          {n === 0 ? (
            <div className="edge flex flex-wrap items-center gap-x-5 gap-y-3 rounded-md bg-soft p-6">
              <Icon name="check" size={24} className="text-success" />
              <span className="flex-[1_1_280px] text-[15px] leading-[23px] text-ink-2">
                Não tem tarefas à sua espera. Quando um pedido precisar de si, aparece aqui.
              </span>
            </div>
          ) : (
            <ul className="m-0 list-none overflow-hidden rounded-md border border-line bg-panel p-0">
              {items.map((item, i) => (
                <li
                  key={item.task}
                  className={
                    i > 0
                      ? 'flex flex-wrap items-center gap-x-4.5 gap-y-3 border-t border-line px-5 py-4.5'
                      : 'flex flex-wrap items-center gap-x-4.5 gap-y-3 px-5 py-4.5'
                  }
                >
                  <span aria-hidden="true" className="grid size-12 shrink-0 place-items-center rounded-md bg-soft text-accent-ink">
                    <Icon name="fileText" size={22} />
                  </span>
                  <div className="flex min-w-0 flex-[1_1_300px] flex-col gap-1">
                    <span className="text-[16px] leading-6 font-semibold">{item.requester}</span>
                    <span className="text-[14px] leading-[21px] text-ink-3 tabular-nums">
                      {item.reference} · {item.step}
                    </span>
                  </div>
                  <Button to={paths.request(item.reference)} variant={i === 0 ? 'primary' : 'secondary'} size="s" iconRight="arrowRight" iconSize={18}>
                    Rever
                    <span className="sr-only">
                      {' '}
                      o pedido {item.reference} de {item.requester}
                    </span>
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </AppShell>
  )
}
