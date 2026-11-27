import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  approve,
  deleteNote,
  fileField,
  fetchDocument,
  fileUrl,
  listInbox,
  listMyRequests,
  openRequest,
  reject,
  release,
  returnToRequester,
  saveNote,
  take,
  undoDecision,
} from './engine'

function answer(status: number, body?: unknown) {
  const fetch = vi.fn().mockResolvedValue(new Response(body === undefined ? null : JSON.stringify(body), { status }))
  vi.stubGlobal('fetch', fetch)
  return fetch
}

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

describe('the Engine API', () => {
  it('lists the tasks of the inbox', async () => {
    const items = [{ task: 't1', reference: 'AC-2026-0147', requester: 'Ana Ferreira', step: 'Validação da chefia' }]
    const fetch = answer(200, items)

    await expect(listInbox()).resolves.toEqual(items)
    expect(fetch.mock.calls[0][0]).toBe('/api/engine/inbox')
  })

  it('opens a request by its reference', async () => {
    const fetch = answer(200, { reference: 'AC-2026-0147' })

    await expect(openRequest('AC-2026-0147')).resolves.toEqual({ found: true, request: { reference: 'AC-2026-0147' } })
    expect(fetch.mock.calls[0][0]).toBe('/api/engine/procedures/AC-2026-0147')
  })

  it('has no request for someone who takes no part in it, nor for an unknown reference', async () => {
    answer(403)
    await expect(openRequest('AC-2026-0147')).resolves.toEqual({ found: false, status: 403 })

    answer(404)
    await expect(openRequest('AC-0000')).resolves.toEqual({ found: false, status: 404 })
  })

  it('fails on any other answer', async () => {
    answer(500)
    await expect(openRequest('AC-2026-0147')).rejects.toThrow('answered 500')
    await expect(listInbox()).rejects.toThrow('answered 500')
  })

  it('gives the address of a file', () => {
    expect(fileUrl('AC-2026-0147', 'f1')).toBe('/api/engine/procedures/AC-2026-0147/files/f1')
  })

  it('approves a task, sending the CSRF token as the server asks', async () => {
    document.cookie = 'XSRF-TOKEN=token-1'
    const approved = { task: 't1', reference: 'AC-2026-0147', nextStep: 'Verificação financeira', handledBy: 'Divisão Financeira', nextTask: 't2' }
    const fetch = answer(200, approved)

    await expect(approve('t1')).resolves.toEqual({ done: true, result: approved })
    const [url, init] = fetch.mock.calls[0]
    expect(url).toBe('/api/engine/tasks/t1/approve')
    expect(init).toMatchObject({ method: 'POST' })
    expect(init.headers).toMatchObject({ 'X-XSRF-TOKEN': 'token-1' })
  })

  it('gives the reason when someone else acted first', async () => {
    answer(409, { detail: 'Este pedido já foi tratado por Segunda Chefia, Chefe de Divisão.' })

    await expect(approve('t1')).resolves.toEqual({ done: false, reason: 'Este pedido já foi tratado por Segunda Chefia, Chefe de Divisão.' })
  })

  it('undoes a decision, or gives why it no longer can', async () => {
    const fetch = answer(200, { task: 't1', reference: 'AC-2026-0147', step: 'Validação da chefia' })
    await expect(undoDecision('t1')).resolves.toMatchObject({ done: true })
    expect(fetch.mock.calls[0][0]).toBe('/api/engine/tasks/t1/undo')

    answer(409, { detail: 'Já não é possível desfazer: o pedido já foi tratado no passo «Verificação financeira».' })
    await expect(undoDecision('t1')).resolves.toEqual({
      done: false,
      reason: 'Já não é possível desfazer: o pedido já foi tratado no passo «Verificação financeira».',
    })
  })

  it.each([
    ['return', returnToRequester],
    ['reject', reject],
  ])('sends the reasons and the message to %s', async (decision, send) => {
    const why = { reasons: ['Preenchido incorretamente'], message: 'Corrija a data de regresso e junte o certificado, por favor.' }
    const fetch = answer(200, { task: 't1', reference: 'AC-2026-0147' })

    await expect(send('t1', why)).resolves.toEqual({ done: true, result: { task: 't1', reference: 'AC-2026-0147' } })
    const [url, init] = fetch.mock.calls[0]
    expect(url).toBe(`/api/engine/tasks/t1/${decision}`)
    expect(init).toMatchObject({ method: 'POST', body: JSON.stringify(why) })

    answer(422)
    await expect(send('t1', why)).rejects.toThrow('answered 422')
  })

  it('takes a task, or gives who took it first', async () => {
    const fetch = answer(200, { task: 't1', reference: 'AC-2026-0147' })
    await expect(take('t1')).resolves.toEqual({ done: true, result: { task: 't1', reference: 'AC-2026-0147' } })
    expect(fetch.mock.calls[0]).toEqual(['/api/engine/tasks/t1/take', expect.objectContaining({ method: 'POST' })])

    answer(409, { detail: 'Em revisão por Segunda Chefia, Chefe de Divisão.' })
    await expect(take('t1')).resolves.toEqual({ done: false, reason: 'Em revisão por Segunda Chefia, Chefe de Divisão.' })
  })

  it('releases a task', async () => {
    const fetch = answer(200, { task: 't1', reference: 'AC-2026-0147' })
    await expect(release('t1')).resolves.toMatchObject({ done: true })
    expect(fetch.mock.calls[0][0]).toBe('/api/engine/tasks/t1/release')
  })

  it('writes a note on a field, and on a file by its key', async () => {
    const note = { field: 'file:f1', label: 'fatura_hotel.pdf', text: 'Falta o certificado de presença com o carimbo da entidade formadora.' }
    const fetch = answer(200, note)

    await expect(saveNote('t1', fileField('f1'), note.text)).resolves.toEqual(note)
    const [url, init] = fetch.mock.calls[0]
    expect(url).toBe('/api/engine/tasks/t1/notes/file%3Af1')
    expect(init).toMatchObject({ method: 'PUT', body: JSON.stringify({ text: note.text }) })
  })

  it('deletes a note', async () => {
    const fetch = answer(204)

    await expect(deleteNote('t1', 'regresso')).resolves.toBeUndefined()
    expect(fetch.mock.calls[0]).toEqual(['/api/engine/tasks/t1/notes/regresso', expect.objectContaining({ method: 'DELETE' })])
  })

  it('fails when a note is refused', async () => {
    answer(422)
    await expect(saveNote('t1', 'regresso', '')).rejects.toThrow('answered 422')
    answer(403)
    await expect(deleteNote('t1', 'regresso')).rejects.toThrow('answered 403')
  })

  it('fails when the server refuses the decision for another reason', async () => {
    answer(403)
    await expect(approve('t1')).rejects.toThrow('answered 403')
  })

  it('lists my requests', async () => {
    const requests = [
      { reference: 'AC-2026-0098', workflow: 'Ajudas de custo', status: 'closed', currentStep: null, closedAt: '2026-10-17T15:30:00+01:00', hasDocument: true },
    ]
    const fetch = answer(200, requests)

    await expect(listMyRequests()).resolves.toEqual(requests)
    expect(fetch.mock.calls[0][0]).toBe('/api/engine/my-requests')
  })

  it('fetches the document of a request with the name the server gives it', async () => {
    const fetch = vi.fn().mockResolvedValue(
      new Response('%PDF-1.4', {
        status: 200,
        headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'inline; filename="ordem_reembolso_AC-2026-0098.pdf"' },
      }),
    )
    vi.stubGlobal('fetch', fetch)

    const document = await fetchDocument('AC-2026-0098')

    expect(fetch.mock.calls[0][0]).toBe('/api/engine/procedures/AC-2026-0098/document')
    expect(document.filename).toBe('ordem_reembolso_AC-2026-0098.pdf')
    expect(document.blob.type).toBe('application/pdf')
  })

  it('names the document after the request when the server gives no name', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('%PDF-1.4', { status: 200 })))

    await expect(fetchDocument('AC-2026-0098')).resolves.toMatchObject({ filename: 'ordem_reembolso_AC-2026-0098.pdf' })
  })

  it('has no document for someone who takes no part in the request', async () => {
    answer(403)
    await expect(fetchDocument('AC-2026-0098')).rejects.toThrow('answered 403')
  })
})
