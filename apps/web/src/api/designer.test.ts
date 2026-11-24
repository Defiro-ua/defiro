import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  importDraft,
  listDrafts,
  listGroups,
  listStepTypes,
  loadDraft,
  publishDraft,
  saveDraft,
  specificationUrl,
  startDraft,
  type StoredDraft,
} from './designer'

const stored: StoredDraft = {
  id: 'd1',
  revision: 1,
  name: 'Ajudas de custo',
  steps: [],
  publishedVersion: null,
  publishedBy: null,
  publishedAt: null,
}

function answer(status: number, body?: unknown) {
  const fetch = vi.fn().mockResolvedValue(new Response(body === undefined ? null : JSON.stringify(body), { status }))
  vi.stubGlobal('fetch', fetch)
  return fetch
}

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

describe('the Designer API', () => {
  it('starts a draft with its name, sending the CSRF token as the server asks', async () => {
    document.cookie = 'XSRF-TOKEN=token-1'
    const fetch = answer(201, stored)

    await expect(startDraft('Ajudas de custo')).resolves.toEqual(stored)
    const [url, init] = fetch.mock.calls[0]
    expect(url).toBe('/api/designer/drafts')
    expect(init).toMatchObject({ method: 'POST', credentials: 'same-origin', body: JSON.stringify({ name: 'Ajudas de custo' }) })
    expect(init.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'token-1' })
  })

  it('loads a draft, and has none for an id the server does not know', async () => {
    answer(200, stored)
    await expect(loadDraft('d1')).resolves.toEqual(stored)

    answer(404)
    await expect(loadDraft('nope')).resolves.toBeNull()
  })

  it('saves the whole draft', async () => {
    const fetch = answer(200, { ...stored, revision: 2 })

    await expect(saveDraft('d1', { name: 'Ajudas de custo', steps: [] })).resolves.toMatchObject({ revision: 2 })
    expect(fetch.mock.calls[0][0]).toBe('/api/designer/drafts/d1')
    expect(fetch.mock.calls[0][1]).toMatchObject({ method: 'PUT', body: JSON.stringify({ name: 'Ajudas de custo', steps: [] }) })
  })

  it('publishes a draft and gives the version it became', async () => {
    const fetch = answer(201, { version: 3 })

    await expect(publishDraft('d1')).resolves.toEqual({ published: true, version: 3 })
    expect(fetch.mock.calls[0]).toEqual(['/api/designer/drafts/d1/publish', expect.objectContaining({ method: 'POST' })])
  })

  it('gives the problems the server found when it refuses to publish', async () => {
    const problems = [{ field: 'name', message: 'Dê um nome ao workflow.' }]
    answer(422, { title: 'O workflow tem respostas em falta ou inválidas.', errors: problems })

    await expect(publishDraft('d1')).resolves.toEqual({ published: false, problems })
  })

  it('lists the drafts and the step types', async () => {
    answer(200, [stored])
    await expect(listDrafts()).resolves.toEqual([stored])

    const types = [{ name: 'review', version: '0.1.0', outcomes: ['approved', 'returned', 'rejected'] }]
    answer(200, types)
    await expect(listStepTypes()).resolves.toEqual(types)
  })

  it('asks the Designer who can request and who can handle a step', async () => {
    const groups = { requesters: [{ name: 'Todos os trabalhadores', note: 'Todas as pessoas da organização' }], handlers: [] }
    const fetch = answer(200, groups)

    await expect(listGroups()).resolves.toEqual(groups)
    expect(fetch.mock.calls[0][0]).toBe('/api/designer/groups')
  })

  it('loads a workflow file as it is, as a new draft', async () => {
    document.cookie = 'XSRF-TOKEN=token-2'
    const fetch = answer(201, stored)

    await expect(importDraft('{"schemaVersion":1}')).resolves.toEqual({ loaded: true, draft: stored })
    const [url, init] = fetch.mock.calls[0]
    expect(url).toBe('/api/designer/drafts/import')
    expect(init).toMatchObject({ method: 'POST', credentials: 'same-origin', body: '{"schemaVersion":1}' })
    expect(init.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'token-2' })
  })

  it('says why the server refused a file', async () => {
    answer(400, { title: 'O ficheiro não foi carregado.', detail: 'O ficheiro não é um workflow da defiro.' })

    await expect(importDraft('olá')).resolves.toEqual({ loaded: false, why: 'O ficheiro não é um workflow da defiro.' })
  })

  it('gives the address of a workflow’s file', () => {
    expect(specificationUrl('d1')).toBe('/api/designer/drafts/d1/specification')
  })

  it('fails on any other answer', async () => {
    answer(500)
    await expect(listDrafts()).rejects.toThrow('GET /api/designer/drafts answered 500')
    answer(403)
    await expect(publishDraft('d1')).rejects.toThrow('POST /api/designer/drafts/d1/publish answered 403')
    answer(500)
    await expect(importDraft('{}')).rejects.toThrow('POST /api/designer/drafts/import answered 500')
  })
})
