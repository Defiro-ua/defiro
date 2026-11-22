import { send, unexpected } from './http'

/** A task waiting for this person in the inbox (the least of UC27 that UC17 needs) */
export type InboxItem = { task: string; reference: string; requester: string; step: string }

export type RequestStep = { id: string; name: string; handledBy: string }

/** An answer of the current version; on a resubmitted request, a changed one keeps the one before */
export type Answer = { field: string; label: string; value: string; changed: boolean; previous: string | null }

/** A file of the request, with the version it was attached in */
export type RequestFile = { id: string; filename: string; version: number }

export type HistoryLine = { at: string; text: string }

/** A request as the person signed in sees it (GET /api/engine/procedures/{reference}) */
export type ReviewRequest = {
  reference: string
  workflow: string
  status: string
  currentStep: string | null
  path: RequestStep[]
  version: number
  requester: { name: string; jobTitle: string | null; unit: string | null; manager: string | null }
  answers: Answer[]
  files: RequestFile[]
  history: HistoryLine[]
  /** The task this person may decide now, or null */
  task: string | null
}

/** Where the approved request went; no next step when it was the last */
export type Approved = { task: string; reference: string; nextStep: string | null; handledBy: string | null; nextTask: string | null }

export type Undone = { task: string; reference: string; step: string }

/**
 * A decision the server took, or the reason it refused it: someone acted first, or the next step
 * was already handled. The reason is the text the person is shown.
 */
export type Outcome<T> = { done: true; result: T } | { done: false; reason: string }

/** Who takes no part in the request may not see it */
export type Opened = { found: true; request: ReviewRequest } | { found: false; status: 403 | 404 }

const PROCEDURES = '/api/engine/procedures'

export async function listInbox(): Promise<InboxItem[]> {
  const url = '/api/engine/inbox'
  const response = await send('GET', url)
  if (!response.ok) throw unexpected('GET', url, response)
  return (await response.json()) as InboxItem[]
}

export async function openRequest(reference: string): Promise<Opened> {
  const url = `${PROCEDURES}/${encodeURIComponent(reference)}`
  const response = await send('GET', url)
  if (response.status === 403 || response.status === 404) return { found: false, status: response.status }
  if (!response.ok) throw unexpected('GET', url, response)
  return { found: true, request: (await response.json()) as ReviewRequest }
}

/** The address of a file of the request, to open in the browser */
export const fileUrl = (reference: string, file: string) => `${PROCEDURES}/${encodeURIComponent(reference)}/files/${file}`

async function decide<T>(task: string, decision: 'approve' | 'undo'): Promise<Outcome<T>> {
  const url = `/api/engine/tasks/${task}/${decision}`
  const response = await send('POST', url)
  if (response.status === 409) return { done: false, reason: ((await response.json()) as { detail: string }).detail }
  if (!response.ok) throw unexpected('POST', url, response)
  return { done: true, result: (await response.json()) as T }
}

export const approve = (task: string) => decide<Approved>(task, 'approve')

export const undoApproval = (task: string) => decide<Undone>(task, 'undo')
