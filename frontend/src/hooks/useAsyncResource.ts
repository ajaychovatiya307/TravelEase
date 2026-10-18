// Shared data-fetching primitive underlying the F-06 resource hooks
// (frontend-tasks.md — "F. Services", useTravelRequestList/useTravelRequest/
// useExpenseList/useExpenseClaim/useAdminUsers/useAdminMappings/
// usePolicyConfigs/useExpenseCategories/useAuditList).
//
// Every resource hook wraps one service call (Section F) with the same
// `ServerDataState` shape (frontend-plan.md §7): `data`/`isLoading`/`error`
// plus a `refetch` the caller uses both to retry a failed fetch and to
// invalidate/reload after a mutation (frontend-tasks.md F-06 "refetch/
// invalidation support"). Implemented once here so every resource hook stays
// a thin service-to-state mapping.
//
// Race-safety: fetches started by a stale render (changed deps, or an
// in-flight `refetch`) are discarded — only the latest fetch commits state.
// Service functions always reject with a `ClassifiedErrorInstance`
// (httpClient.ts `request()`), so `error` never needs secondary translation.

import { useCallback, useEffect, useRef, useState } from 'react'

import type { ClassifiedErrorInstance } from '../services/httpClient'

/** Common shape of every F-06 data-fetching hook (frontend-plan.md §7). */
export interface ServerDataState<T> {
  /** Last successfully fetched value; `null` before the first success. */
  data: T | null
  /** True while a fetch (initial, dep-change, or `refetch`) is in flight. */
  isLoading: boolean
  /** The normalized error of the most recent failed fetch; `null` otherwise. */
  error: ClassifiedErrorInstance | null
  /** Re-runs the fetch — used to retry on error and to invalidate after a mutation. */
  refetch: () => void
}

/**
 * Runs `fetcher` whenever `deps` change (and whenever `refetch` is called),
 * exposing the result as a `ServerDataState`. `deps` follows the same
 * equality rules as `useEffect`'s dependency array.
 */
export function useAsyncResource<T>(
  fetcher: () => Promise<T>,
  deps: readonly unknown[],
): ServerDataState<T> {
  const [data, setData] = useState<T | null>(null)
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [error, setError] = useState<ClassifiedErrorInstance | null>(null)
  const [nonce, setNonce] = useState<number>(0)

  // Always-current fetcher reference so the effect below only re-runs on a
  // real dep change or `refetch`, never because the caller passed a new
  // function identity for the same logical request. Synced in its own
  // effect (never written during render) so it runs before the fetch effect
  // below on every commit.
  const fetcherRef = useRef(fetcher)
  useEffect(() => {
    fetcherRef.current = fetcher
  })

  const refetch = useCallback(() => setNonce((n) => n + 1), [])

  useEffect(() => {
    let cancelled = false
    // Intentional: resets loading/error at the start of every fetch (initial,
    // dep-change, or refetch) — not a derived-state anti-pattern.
    // eslint-disable-next-line react-hooks/set-state-in-effect -- resets fetch state at the start of every fetch
    setIsLoading(true)
    setError(null)
    fetcherRef
      .current()
      .then((result) => {
        if (cancelled) return
        setData(result)
        setIsLoading(false)
      })
      .catch((err: unknown) => {
        if (cancelled) return
        setError(err as ClassifiedErrorInstance)
        setIsLoading(false)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- deps is the caller-supplied dependency list
  }, [...deps, nonce])

  return { data, isLoading, error, refetch }
}
