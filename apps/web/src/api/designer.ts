import type { Draft, Problem } from '../designer/draft'
import { csrfHeader, send, unexpected } from './http'

/** A draft as the Designer keeps it, with its last publication if it has one */
export type StoredDraft = Draft & {
  id: string
  revision: number
  publishedVersion: number | null
  /** The name of who published it last */
  publishedBy: string | null
  publishedAt: string | null
}

/** A type of step in the palette: a plugin of the Registry's catalogue */
export type StepType = { name: string; version: string; outcomes: string[] }

/** Who can make a request or handle a step, with a note on where it comes from (UC05) */
export type GroupOption = { name: string; note: string }

/** The cards of «Quem pode pedir» and of «Quem trata este passo?», from the organization's directory */
export type GroupChoices = { requesters: GroupOption[]; handlers: GroupOption[] }

export type Publication = { published: true; version: number } | { published: false; problems: Problem[] }

export type Loading = { loaded: true; draft: StoredDraft } | { loaded: false; why: string }

const DRAFTS = '/api/designer/drafts'

async function json<T>(method: 'GET' | 'POST' | 'PUT', url: string, body?: unknown): Promise<T> {
  const response = await send(method, url, body)
  if (!response.ok) throw unexpected(method, url, response)
  return (await response.json()) as T
}

export const listDrafts = () => json<StoredDraft[]>('GET', DRAFTS)

export const listStepTypes = () => json<StepType[]>('GET', '/api/designer/step-types')

export const listGroups = () => json<GroupChoices>('GET', '/api/designer/groups')

export const startDraft = (name: string) => json<StoredDraft>('POST', DRAFTS, { name })

export const saveDraft = (id: string, draft: Draft) => json<StoredDraft>('PUT', `${DRAFTS}/${id}`, draft)

export async function loadDraft(id: string): Promise<StoredDraft | null> {
  const url = `${DRAFTS}/${id}`
  const response = await send('GET', url)
  if (response.status === 404) return null
  if (!response.ok) throw unexpected('GET', url, response)
  return (await response.json()) as StoredDraft
}

/** Publishes the draft as the workflow's next version, or gives what the Designer says is missing */
export async function publishDraft(id: string): Promise<Publication> {
  const url = `${DRAFTS}/${id}/publish`
  const response = await send('POST', url)
  if (response.status === 422) return { published: false, problems: ((await response.json()) as { errors: Problem[] }).errors }
  if (!response.ok) throw unexpected('POST', url, response)
  return { published: true, version: ((await response.json()) as { version: number }).version }
}

/** The address of a created workflow's file: its published version (docs/workflow-specification.md) */
export const specificationUrl = (id: string) => `${DRAFTS}/${id}/specification`

/** Loads a workflow file, sent as it is, as a new draft to review; or says why the server refused it */
export async function importDraft(file: string): Promise<Loading> {
  const url = `${DRAFTS}/import`
  const response = await fetch(url, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json', ...csrfHeader() },
    body: file,
  })
  if (response.status === 400) return { loaded: false, why: ((await response.json()) as { detail: string }).detail }
  if (!response.ok) throw unexpected('POST', url, response)
  return { loaded: true, draft: (await response.json()) as StoredDraft }
}
