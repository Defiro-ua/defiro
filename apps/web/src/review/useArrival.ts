import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import type { ReviewState } from './notices'

/**
 * What the previous page passed on (the notice, and the approval «Desfazer» reverses), read once.
 * It is taken out of the history at once, so reloading the page, or coming back to it, does not
 * show the notice again, nor offer to undo an approval hours later.
 */
export function useArrival(): ReviewState {
  const location = useLocation()
  const navigate = useNavigate()
  const [arrived] = useState<ReviewState>(() => (location.state as ReviewState | null) ?? {})

  useEffect(() => {
    if (location.state == null) return
    navigate({ pathname: location.pathname, search: location.search }, { replace: true, state: null })
  }, [location, navigate])

  return arrived
}
