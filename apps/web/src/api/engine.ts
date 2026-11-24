import { send, unexpected } from './http'

/**
 * A task waiting for this person in the inbox (the least of UC27 that UC17 needs). If a candidate
 * took it, reviewer is who («Rui Matos, Chefe de Divisão») and mine whether it is this person.
 */
export type InboxItem = { task: string; reference: string; requester: string; step: string; reviewer: string | null; mine: boolean }

/** A step of the path, with the reasons its reviewer may return or reject the request for (UC18) */
export type RequestStep = { id: string; name: string; handledBy: string; returnReasons: string[]; rejectReasons: string[] }

/** An answer of the current version; on a resubmitted request, a changed one keeps the one before */
export type Answer = { field: string; label: string; value: string; changed: boolean; previous: string | null }

/** A file of the request, with the version it was attached in */
export type RequestFile = { id: string; filename: string; version: number }

/** An entry, with the reviewer's message when it returned or rejected the request */
export type HistoryLine = { at: string; text: string; message: string | null }

/** Who returned or rejected the request, as «Rui Matos, Chefe de Divisão», why, and what they wrote */
export type Decision = { by: string; reasons: string[]; message: string | null }

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
  /** The task this person took and may decide now, or null */
  task: string | null
  /** Who took the current task, as «Rui Matos, Chefe de Divisão», or null */
  reviewer: string | null
  /** This person's notes on the task they took, still theirs alone */
  notes: Note[]
  /** The notes the previous reviewer sent with their approval or, for the requester, with the return */
  notesFrom: NoteFrom[]
  /** The decision, while the request is returned or rejected */
  decision: Decision | null
  /** Whether this person made the request */
  mine: boolean
}

export type Taken = { task: string; reference: string }

/** A reviewer's note on a field of the request: an answer's field, or «file:» and a file's id (UC19) */
export type Note = { field: string; label: string; text: string }

/** A note sent with a decision, with who wrote it: «Rui Matos, Chefe de Divisão» */
export type NoteFrom = Note & { author: string }

/** Where the approved request went; no next step when it was the last */
export type Approved = { task: string; reference: string; nextStep: string | null; handledBy: string | null; nextTask: string | null }

/** The task that was returned or rejected */
export type Ended = { task: string; reference: string }

/** The step open again, and the decision that was undone: approved, returned or rejected */
export type Undone = { task: string; reference: string; step: string; decision: string }

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

async function decide<T>(task: string, decision: 'take' | 'release' | 'approve' | 'return' | 'reject' | 'undo', body?: unknown): Promise<Outcome<T>> {
  const url = `/api/engine/tasks/${task}/${decision}`
  const response = await send('POST', url, body)
  if (response.status === 409) return { done: false, reason: ((await response.json()) as { detail: string }).detail }
  if (!response.ok) throw unexpected('POST', url, response)
  return { done: true, result: (await response.json()) as T }
}

/** «Rever»: takes the task, or gives who took it first */
export const take = (task: string) => decide<Taken>(task, 'take')

/** «Libertar a tarefa»: gives the task back to every candidate */
export const release = (task: string) => decide<Taken>(task, 'release')

export const approve = (task: string) => decide<Approved>(task, 'approve')

/** «Devolver para correção», for reasons of the step (UC18) */
export const returnToRequester = (task: string, why: { reasons: string[]; message: string }) => decide<Ended>(task, 'return', why)

/** «Rejeitar o pedido», for reasons of the step (UC18) */
export const reject = (task: string, why: { reasons: string[]; message: string }) => decide<Ended>(task, 'reject', why)

export const undoDecision = (task: string) => decide<Undone>(task, 'undo')

const noteUrl = (task: string, field: string) => `/api/engine/tasks/${task}/notes/${encodeURIComponent(field)}`

/** Writes the note on the field, new or changed */
export async function saveNote(task: string, field: string, text: string): Promise<Note> {
  const url = noteUrl(task, field)
  const response = await send('PUT', url, { text })
  if (!response.ok) throw unexpected('PUT', url, response)
  return (await response.json()) as Note
}

export async function deleteNote(task: string, field: string): Promise<void> {
  const url = noteUrl(task, field)
  const response = await send('DELETE', url)
  if (!response.ok) throw unexpected('DELETE', url, response)
}

/** The key of a note on a file */
export const fileField = (file: string) => `file:${file}`
