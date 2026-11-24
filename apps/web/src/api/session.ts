import { csrfToken } from './http'

/** Items of the side menu the server allows this person to see. */
export type MenuItem = 'create-workflow' | 'new-request' | 'inbox' | 'workflows'

/**
 * Who is signed in, as defiro knows them (GET /api/me). Name, unit, job title and manager are null
 * when defiro does not have them.
 */
export type Profile = {
  name: string | null
  unit: string | null
  jobTitle: string | null
  manager: string | null
  menu: MenuItem[]
}

export async function fetchProfile(): Promise<Profile | null> {
  const response = await fetch('/api/me', { credentials: 'same-origin' })
  if (response.status === 401) return null
  if (!response.ok) throw new Error(`GET /api/me answered ${response.status}`)
  return (await response.json()) as Profile
}

/**
 * Ends the session: in defiro and then at the identity provider. It submits a form instead of using
 * fetch, because the browser itself has to go through the provider; it comes back to the home page,
 * with the signed-out notice. The server asks for the CSRF token, which goes in the form; without
 * its cookie, nothing is submitted and this returns false.
 */
export function signOut(): boolean {
  const token = csrfToken()
  if (!token) return false
  const form = document.createElement('form')
  form.method = 'post'
  form.action = '/logout'
  form.hidden = true
  const field = document.createElement('input')
  field.type = 'hidden'
  field.name = '_csrf'
  field.value = token
  form.append(field)
  document.body.append(form)
  form.submit()
  return true
}
