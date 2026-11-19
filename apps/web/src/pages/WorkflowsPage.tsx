import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { listDrafts, type StoredDraft } from '../api/designer'
import { Chip } from '../components/Chip'
import { Flash, type Notice } from '../components/Flash'
import { AppShell } from '../components/layout/AppShell'
import { useFocusOnArrival } from '../lib/useFocusOnArrival'
import { useProfile } from '../lib/useProfile'
import { paths } from '../routes'

const day = (iso: string) => new Date(iso).toLocaleDateString('pt-PT', { timeZone: 'Europe/Lisbon' })

/**
 * «Workflows» (UC03, criterion 10): what the Workflow Manager designed. A published workflow shows
 * its last version, who created it and when; a draft not yet created can be continued.
 */
export function WorkflowsPage() {
  const profile = useProfile()
  const location = useLocation()
  const headingRef = useRef<HTMLHeadingElement>(null)
  useFocusOnArrival(headingRef)
  const [drafts, setDrafts] = useState<StoredDraft[] | null | 'failed'>(null)
  const [notice, setNotice] = useState<Notice | null>((location.state as { notice?: Notice } | null)?.notice ?? null)
  const allowed = profile.menu.includes('workflows')

  useEffect(() => {
    if (!allowed) return
    let current = true
    listDrafts().then(
      (list) => current && setDrafts(list),
      () => current && setDrafts('failed'),
    )
    return () => {
      current = false
    }
  }, [allowed])

  if (!allowed) return <Navigate to={paths.inbox} replace />

  return (
    <AppShell profile={profile} active="workflows">
      <Flash notice={notice} onClose={() => setNotice(null)} />
      <div className="flex max-w-reading flex-col gap-6">
        <h1 ref={headingRef} tabIndex={-1} className="m-0 text-[32px] leading-10 font-extrabold tracking-headline outline-none">
          Workflows
        </h1>
        {drafts === null && (
          <p role="status" className="m-0 text-ink-2">
            A carregar…
          </p>
        )}
        {drafts === 'failed' && (
          <p role="alert" className="m-0 font-semibold text-error">
            Não foi possível ler os workflows. Recarregue a página daqui a pouco.
          </p>
        )}
        {Array.isArray(drafts) && drafts.length === 0 && (
          <p className="m-0 text-ink-2">Ainda não há workflows. Crie o primeiro com «Criar workflow», no menu.</p>
        )}
        {Array.isArray(drafts) && drafts.length > 0 && (
          <ul aria-label="Workflows" className="m-0 flex list-none flex-col gap-3 p-0">
            {drafts.map((d) => {
              const name = d.name || 'Workflow sem nome'
              return (
                <li key={d.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-item border border-line-strong bg-panel px-5 py-4">
                  <div className="flex min-w-0 flex-[1_1_280px] flex-col gap-1">
                    <span className="text-[17px] leading-6 font-semibold">{name}</span>
                    {d.publishedVersion !== null ? (
                      <span className="text-[14px] leading-5 text-ink-2">
                        Versão {d.publishedVersion} · criado por {d.publishedBy || '—'}
                        {d.publishedAt ? ` a ${day(d.publishedAt)}` : ''}
                      </span>
                    ) : (
                      <Chip tone="warn" icon="pen">
                        Rascunho por criar
                      </Chip>
                    )}
                  </div>
                  <Link to={`${paths.designer}/${d.id}`} className="font-semibold text-accent-ink">
                    {d.publishedVersion !== null ? 'Alterar ' : 'Continuar '}
                    <span className="sr-only">{name}</span>
                  </Link>
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </AppShell>
  )
}
