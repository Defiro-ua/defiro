import { afterEach, describe, expect, it, vi } from 'vitest'
import { approve, deleteNote, fileField, fileUrl, listInbox, openRequest, release, saveNote, take, undoApproval } from './engine'

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

  it('undoes an approval, or gives why it no longer can', async () => {
    const fetch = answer(200, { task: 't1', reference: 'AC-2026-0147', step: 'Validação da chefia' })
    await expect(undoApproval('t1')).resolves.toMatchObject({ done: true })
    expect(fetch.mock.calls[0][0]).toBe('/api/engine/tasks/t1/undo')

    answer(409, { detail: 'Já não é possível desfazer: o pedido já foi tratado no passo «Verificação financeira».' })
    await expect(undoApproval('t1')).resolves.toEqual({
      done: false,
      reason: 'Já não é possível desfazer: o pedido já foi tratado no passo «Verificação financeira».',
    })
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
})
