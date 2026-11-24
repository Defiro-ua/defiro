/**
 * The session cookie goes with every request, so the server asks writes for the CSRF token it left
 * in the XSRF-TOKEN cookie. Empty when there is no such cookie.
 */
export function csrfToken(): string {
  return cookie('XSRF-TOKEN')
}

/** The CSRF token as a write with fetch sends it back */
export function csrfHeader(): Record<string, string> {
  return { 'X-XSRF-TOKEN': csrfToken() }
}

function cookie(name: string): string {
  const prefix = `${name}=`
  const found = document.cookie.split('; ').find((c) => c.startsWith(prefix))
  return found ? decodeURIComponent(found.slice(prefix.length)) : ''
}

/** A request to the server with the session, and JSON both ways */
export function send(method: 'GET' | 'POST' | 'PUT', url: string, body?: unknown): Promise<Response> {
  const write = method !== 'GET'
  return fetch(url, {
    method,
    credentials: 'same-origin',
    headers: { Accept: 'application/json', ...(write ? { 'Content-Type': 'application/json', ...csrfHeader() } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export function unexpected(method: string, url: string, response: Response): Error {
  return new Error(`${method} ${url} answered ${response.status}`)
}
