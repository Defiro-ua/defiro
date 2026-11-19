import type { Draft, Problem } from '../designer/draft'
import { send, unexpected } from './http'

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

export type Publication = { published: true; version: number } | { published: false; problems: Problem[] }

const DRAFTS = '/api/designer/drafts'

async function json<T>(method: 'GET' | 'POST' | 'PUT', url: string, body?: unknown): Promise<T> {
  const response = await send(method, url, body)
  if (!response.ok) throw unexpected(method, url, response)
  return (await response.json()) as T
}

export const listDrafts = () => json<StoredDraft[]>('GET', DRAFTS)

export const listStepTypes = () => json<StepType[]>('GET', '/api/designer/step-types')

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
