// Unsaved-changes navigation guard (G-02, frontend-tasks.md — "G. Guards").
// Protects in-progress form edits (Travel Request / Expense Claim create-
// edit-resubmit forms, frontend-requirement.md §4) from being lost to an
// accidental navigation while dirty.
//
// The app's router (routes/Router.tsx, D-03) is a plain `BrowserRouter` /
// `<Routes>` tree, not a data router (`createBrowserRouter`) — react-router's
// built-in `useBlocker`/`unstable_usePrompt` require a data router and are
// not usable here. This hook instead exposes:
// - `guardedNavigate(action)`: the wrapper a form page calls instead of
//   `navigate(...)` directly for every in-app navigation it triggers (Cancel
//   buttons, post-submit redirects away from a dirty form, sidebar links the
//   form itself renders, etc.) — confirms via `window.confirm` first when
//   dirty, then runs `action` only if the user proceeds (or the form is
//   clean).
// - a `beforeunload` listener (tab close/refresh/external navigation) that
//   prompts the browser's native "leave site" dialog while dirty.

import { useCallback, useEffect, useRef } from 'react'

export const DEFAULT_UNSAVED_CHANGES_MESSAGE =
  'You have unsaved changes. Are you sure you want to leave this page?'

export interface UseUnsavedChangesGuardOptions {
  /** The form's current dirty state; read fresh on every guarded action. */
  isDirty: boolean
  /** Confirmation prompt text (`window.confirm`); also set as `beforeunload`'s `returnValue`. */
  message?: string
}

export interface UnsavedChangesGuard {
  /**
   * Runs `action` immediately when clean; when dirty, prompts for
   * confirmation first and only runs `action` if the user confirms.
   */
  guardedNavigate: (action: () => void) => void
}

/**
 * Blocks accidental navigation away from a dirty form (frontend-requirement.md
 * §4, frontend-plan.md §12). `isDirty` is read through a ref so the returned
 * `guardedNavigate` identity stays stable across re-renders while always
 * checking the latest dirty state.
 */
export function useUnsavedChangesGuard({
  isDirty,
  message = DEFAULT_UNSAVED_CHANGES_MESSAGE,
}: UseUnsavedChangesGuardOptions): UnsavedChangesGuard {
  const isDirtyRef = useRef(isDirty)
  useEffect(() => {
    isDirtyRef.current = isDirty
  }, [isDirty])

  useEffect(() => {
    function handleBeforeUnload(event: BeforeUnloadEvent): void {
      if (!isDirtyRef.current) return
      event.preventDefault()
      // Legacy requirement for triggering the browser's native prompt (Chrome/Firefox).
      event.returnValue = message
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [message])

  const guardedNavigate = useCallback(
    (action: () => void) => {
      if (!isDirtyRef.current || window.confirm(message)) {
        action()
      }
    },
    [message],
  )

  return { guardedNavigate }
}
