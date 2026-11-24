import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchProfile, signOut, type Profile } from './session'

const ana: Profile = {
  name: 'Ana Ferreira',
  unit: 'Divisão de Obras Municipais',
  jobTitle: 'Técnica Superior',
  manager: 'Rui Matos',
  menu: ['new-request', 'inbox'],
}

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status })
}

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

describe('fetchProfile', () => {
  it('returns who is signed in, as defiro knows them', async () => {
    fetchMock.mockResolvedValue(respond(200, ana))

    await expect(fetchProfile()).resolves.toEqual(ana)
    expect(fetchMock).toHaveBeenCalledWith('/api/me', expect.objectContaining({ credentials: 'same-origin' }))
  })

  it('returns null when nobody is signed in', async () => {
    fetchMock.mockResolvedValue(respond(401))

    await expect(fetchProfile()).resolves.toBeNull()
  })

  it('fails on any other answer, so an outage is not mistaken for being signed out', async () => {
    fetchMock.mockResolvedValue(respond(503))

    await expect(fetchProfile()).rejects.toThrow('503')
  })
})

describe('signOut', () => {
  // jsdom does not submit forms
  const submit = vi.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(() => {})

  afterEach(() => {
    submit.mockClear()
    document.body.replaceChildren()
  })

  it('signs out by posting a form to /logout with the CSRF token', () => {
    document.cookie = 'XSRF-TOKEN=token-from-the-server; path=/'

    expect(signOut()).toBe(true)

    const form = document.querySelector<HTMLFormElement>('form[action="/logout"]')!
    expect(form.method).toBe('post')
    expect(new FormData(form).get('_csrf')).toBe('token-from-the-server')
    expect(submit).toHaveBeenCalledTimes(1)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('does not submit without the CSRF cookie', () => {
    expect(signOut()).toBe(false)
    expect(submit).not.toHaveBeenCalled()
  })
})
