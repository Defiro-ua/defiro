import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router'
import { approve, fileUrl, openRequest, type Opened, type ReviewRequest } from '../api/engine'
import { Button } from '../components/Button'
import { Chip } from '../components/Chip'
import { Flash, type Notice } from '../components/Flash'
import { Icon } from '../components/Icon'
import { AppShell } from '../components/layout/AppShell'
import { ProcedureProgress } from '../components/ProcedureProgress'
import { cx } from '../lib/cx'
import { useFocusOnArrival } from '../lib/useFocusOnArrival'
import { useProfile } from '../lib/useProfile'
import { approvedNotice, approveFailed, dateTime, refusedNotice, type ReviewState } from '../review/notices'
import { paths } from '../routes'

const card = 'rounded-md border border-line bg-panel'
const secondarySmall =
  'box-border inline-flex min-h-11 shrink-0 items-center justify-center gap-2 rounded-pill border-emph border-field bg-bg px-4 text-[14px] font-semibold text-ink no-underline hover:bg-soft'

/**
 * E08 · Rever pedido (UC17): the request, its files, its path and its history, and, for a
 * candidate of the current step, the decision. The expenses and their total are UC15 (Dinis);
 * the notes are UC19, and returning and rejecting UC18, so those two buttons wait for it.
 */
export function RequestPage() {
  const { reference = '' } = useParams()
  const profile = useProfile()
  const location = useLocation()
  const [opened, setOpened] = useState<Opened | null | 'failed'>(null)
  const [notice, setNotice] = useState<Notice | null>((location.state as ReviewState | null)?.notice ?? null)

  useEffect(() => {
    let current = true
    openRequest(reference).then(
      (answer) => current && setOpened(answer),
      () => current && setOpened('failed'),
    )
    return () => {
      current = false
    }
  }, [reference])

  return (
    <AppShell profile={profile} active="inbox" mainClassName="max-w-detail gap-6">
      <Flash notice={notice} onClose={() => setNotice(null)} />
      {opened === null && (
        <p role="status" className="m-0 text-ink-2">
          A carregar…
        </p>
      )}
      {opened === 'failed' && (
        <p role="alert" className="m-0 font-semibold text-error">
          Não foi possível abrir o pedido. Recarregue a página daqui a pouco.
        </p>
      )}
      {opened !== null && opened !== 'failed' && !opened.found && (
        <p role="alert" className="m-0 font-semibold text-error">
          {opened.status === 403 ? 'Não participa neste pedido, por isso não o pode abrir.' : `Não há nenhum pedido ${reference}.`}
        </p>
      )}
      {opened !== null && opened !== 'failed' && opened.found && <Review request={opened.request} onRefused={setNotice} />}
    </AppShell>
  )
}

function Review({ request: r, onRefused }: { request: ReviewRequest; onRefused: (notice: Notice) => void }) {
  const navigate = useNavigate()
  const headingRef = useRef<HTMLHeadingElement>(null)
  useFocusOnArrival(headingRef)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [approving, setApproving] = useState(false)

  const current = r.path.findIndex((s) => s.id === r.currentStep)
  const next = current >= 0 ? r.path[current + 1] : undefined
  const closed = r.status === 'closed'

  async function approveIt(task: string) {
    setApproving(true)
    try {
      const outcome = await approve(task)
      const state: ReviewState = outcome.done
        ? { notice: approvedNotice(r.reference, outcome.result.nextStep, outcome.result.handledBy), undo: { task, reference: r.reference } }
        : // Someone acted first: back to the inbox, with who, and no «Desfazer»
          { notice: refusedNotice(outcome.reason) }
      navigate(paths.inbox, { state })
    } catch {
      setApproving(false)
      onRefused(approveFailed)
    }
  }

  return (
    <>
      <header className="flex flex-col gap-3.5">
        <Button variant="secondary" size="l" icon="arrowLeft" className="self-start" to={paths.inbox}>
          Anterior<span className="sr-only">: Caixa de tarefas</span>
        </Button>
        <h1 ref={headingRef} tabIndex={-1} className="m-0 text-headline-lg outline-none">
          {r.workflow} · <span className="text-orange">{r.requester.name}</span>
        </h1>
        <div className="flex flex-wrap items-center gap-2">
          {r.version > 1 && (
            <Chip tone="info" icon="pen">
              Versão {r.version}
            </Chip>
          )}
          <span className="text-[14px] text-ink-3 tabular-nums">
            {[r.requester.jobTitle, r.requester.unit].filter(Boolean).join(', ')} · {r.reference}
          </span>
        </div>
      </header>

      <section aria-label="Passos do procedimento" className={cx(card, 'px-6 pt-5 pb-4')}>
        <ProcedureProgress steps={r.path.map((s) => ({ label: s.name, meta: s.handledBy }))} current={Math.max(0, current)} estado={closed ? 'fechado' : ''} />
      </section>

      <div className="flex flex-wrap items-start gap-7">
        <div className="flex min-w-0 flex-[999_1_560px] flex-col gap-6">
          <section aria-labelledby="titulo-requerente" className={cx(card, 'flex flex-col gap-2.5 px-6.5 py-5.5')}>
            <h2 id="titulo-requerente" className="m-0 text-title-sm font-semibold">
              Requerente
            </h2>
            <dl className="m-0 flex flex-wrap gap-x-6 gap-y-2.5">
              {[
                { rot: 'Nome', v: r.requester.name },
                { rot: 'Cargo', v: r.requester.jobTitle },
                { rot: 'Unidade orgânica', v: r.requester.unit },
                { rot: 'Chefia direta', v: r.requester.manager },
              ].map((row) => (
                <div key={row.rot} className="flex flex-[1_1_200px] flex-col gap-0.5">
                  <dt className="text-label-sm leading-[18px] font-semibold text-ink-3">{row.rot}</dt>
                  <dd className="m-0 text-[15px] leading-[22px]">{row.v || '—'}</dd>
                </div>
              ))}
            </dl>
          </section>

          <section aria-labelledby="titulo-dados" className={cx(card, 'flex flex-col gap-1.5 px-6.5 pt-5.5 pb-3')}>
            <h2 id="titulo-dados" className="m-0 text-title-sm font-semibold">
              Dados do pedido
            </h2>
            <dl className="m-0 flex flex-col">
              {r.answers.map((a) => (
                <div key={a.field} className="flex flex-wrap gap-x-5 gap-y-1 border-t border-line py-3">
                  <dt className="flex-[0_0_190px] pt-2 text-[14px] leading-5 font-semibold text-ink-2">{a.label}</dt>
                  <dd className="m-0 flex min-w-0 flex-[1_1_320px] flex-col gap-1">
                    <div className="flex min-h-10 flex-wrap items-center gap-x-3 gap-y-2">
                      <span className="flex-[1_1_200px] text-[16px] leading-6 tabular-nums [overflow-wrap:anywhere]">{a.value || '—'}</span>
                      {a.changed && (
                        <Chip tone="info" icon="pen">
                          Alterado
                        </Chip>
                      )}
                    </div>
                    {a.changed && <span className="text-[14px] leading-5 text-ink-3 tabular-nums">Antes: {a.previous || '—'}</span>}
                  </dd>
                </div>
              ))}
            </dl>
          </section>

          <section aria-labelledby="titulo-ficheiros" className={cx(card, 'flex flex-col gap-3.5 px-6.5 py-5.5')}>
            <h2 id="titulo-ficheiros" className="m-0 text-title-sm font-semibold">
              Ficheiros · {r.files.length}
            </h2>
            <ul className="m-0 flex list-none flex-col p-0">
              {r.files.map((f, i) => (
                <li key={f.id} className={cx('flex flex-wrap items-center gap-x-3.5 gap-y-2.5 py-3', i > 0 && 'border-t border-line')}>
                  <span aria-hidden="true" className="grid size-10 shrink-0 place-items-center rounded-tile bg-soft text-accent-ink">
                    <Icon name="fileText" size={20} />
                  </span>
                  <span className="min-w-0 flex-[1_1_200px] text-[15px] font-semibold [overflow-wrap:anywhere]">{f.filename}</span>
                  {f.version > 1 && f.version === r.version && (
                    <Chip tone="info" icon="plus">
                      Novo na versão {f.version}
                    </Chip>
                  )}
                  <a href={fileUrl(r.reference, f.id)} target="_blank" rel="noreferrer" className={secondarySmall}>
                    <Icon name="eye" size={16} />
                    Ver<span className="sr-only"> {f.filename}</span>
                  </a>
                </li>
              ))}
            </ul>
          </section>

          <section aria-labelledby="titulo-historico" className={cx(card, 'flex flex-col py-4 pr-5 pl-6.5')}>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
              <h2 id="titulo-historico" className="m-0 flex flex-[1_1_220px] items-center gap-2 text-[16px] leading-6 font-semibold">
                <Icon name="history" size={18} className="text-accent-ink" />
                Histórico do pedido · {r.history.length} registos
              </h2>
              <button
                type="button"
                className={secondarySmall}
                aria-expanded={historyOpen}
                aria-controls="lista-historico"
                onClick={() => setHistoryOpen((v) => !v)}
              >
                <Icon name={historyOpen ? 'chevronUp' : 'eye'} size={16} />
                {historyOpen ? 'Esconder histórico' : 'Ver histórico'}
              </button>
            </div>
            {historyOpen && (
              <ol id="lista-historico" className="m-0 mt-3.5 flex animate-surge list-none flex-col p-0">
                {r.history
                  .slice()
                  .reverse()
                  .map((h, i) => (
                    <li key={i} className="flex flex-wrap gap-x-4 gap-y-0.5 border-t border-line py-2.5 text-[14px] leading-[21px]">
                      <span className="flex-[0_0_140px] text-ink-3 tabular-nums">{dateTime(h.at)}</span>
                      <span className="flex-[1_1_260px]">{h.text}</span>
                    </li>
                  ))}
              </ol>
            )}
          </section>
        </div>

        <div className="sticky top-4 flex max-w-full flex-[1_1_340px] flex-col gap-5">
          {r.task ? (
            <section aria-labelledby="titulo-decisao" className="edge flex flex-col gap-4 rounded-md bg-soft p-6">
              <h2 id="titulo-decisao" className="m-0 text-title-lg font-semibold">
                A sua decisão
              </h2>
              {/* UC18: returning and rejecting arrive with it */}
              <div className="flex flex-col gap-2.5" role="group" aria-label="Decisão" aria-describedby="decisao-em-breve">
                <Button variant="secondary" icon="reset" className="w-full" disabled>
                  Devolver para correção
                </Button>
                <Button variant="secondary" icon="x" className="w-full" disabled>
                  Rejeitar o pedido
                </Button>
                <span id="decisao-em-breve" className="text-center text-label-sm leading-[19px] text-ink-3">
                  Devolver e rejeitar: disponível em breve.
                </span>
                <Button id="btn-aprovar" size="l" icon="check" iconSize={20} disabled={approving} onClick={() => approveIt(r.task as string)}>
                  Aprovar
                </Button>
              </div>
              <p className="-mt-1.5 mb-0 text-center text-label-sm leading-[19px] text-ink-2">
                {next ? `Segue para o passo «${next.name}», com ${next.handledBy}.` : 'Fecha o procedimento.'}
              </p>
            </section>
          ) : (
            <section aria-labelledby="titulo-estado" className="edge flex flex-col gap-3 rounded-md bg-soft p-6">
              <h2 id="titulo-estado" className="m-0 text-title-lg font-semibold">
                Estado
              </h2>
              <p className="m-0 text-[15px] leading-[23px]">
                {closed ? 'Concluído.' : current >= 0 ? `No passo «${r.path[current].name}», com ${r.path[current].handledBy}.` : '—'}
              </p>
            </section>
          )}
        </div>
      </div>
    </>
  )
}
