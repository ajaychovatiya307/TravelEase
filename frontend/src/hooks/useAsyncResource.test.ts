// useAsyncResource unit tests (F-06, frontend-tasks.md — "F. Services").
// Covers the shared primitive directly: initial loading, success, error,
// dep-change re-fetch, `refetch()`, and discarding a stale in-flight fetch
// (frontend-plan.md §14 test matrix "Hooks" row).

import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { useAsyncResource } from './useAsyncResource'

describe('useAsyncResource (F-06)', () => {
  it('starts in a loading state with no data/error', () => {
    const { result } = renderHook(() => useAsyncResource(() => new Promise<string>(() => {}), []))
    expect(result.current.isLoading).toBe(true)
    expect(result.current.data).toBeNull()
    expect(result.current.error).toBeNull()
  })

  it('resolves to data and clears loading on success', async () => {
    const fetcher = vi.fn().mockResolvedValue('value-1')
    const { result } = renderHook(() => useAsyncResource(fetcher, []))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.data).toBe('value-1')
    expect(result.current.error).toBeNull()
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('exposes a ClassifiedErrorInstance-shaped error on rejection', async () => {
    const classifiedError = Object.assign(new Error('Not found'), {
      category: 'RESOURCE_NOT_FOUND',
    })
    const fetcher = vi.fn().mockRejectedValue(classifiedError)
    const { result } = renderHook(() => useAsyncResource(fetcher, []))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.data).toBeNull()
    expect(result.current.error).toBe(classifiedError)
  })

  it('re-fetches when a dependency changes', async () => {
    const fetcher = vi.fn().mockResolvedValue('value')
    const { result, rerender } = renderHook(({ dep }) => useAsyncResource(fetcher, [dep]), {
      initialProps: { dep: 'a' },
    })
    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(fetcher).toHaveBeenCalledTimes(1)

    rerender({ dep: 'b' })
    await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(2))
  })

  it('re-fetches on refetch() without a dependency change', async () => {
    const fetcher = vi.fn().mockResolvedValue('value')
    const { result } = renderHook(() => useAsyncResource(fetcher, []))
    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(fetcher).toHaveBeenCalledTimes(1)

    act(() => result.current.refetch())
    await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(2))
  })

  it('discards a stale in-flight fetch superseded by a dep change', async () => {
    let resolveFirst: (value: string) => void = () => {}
    const fetcher = vi
      .fn()
      .mockImplementationOnce(
        () => new Promise<string>((resolve) => (resolveFirst = resolve)),
      )
      .mockImplementationOnce(() => Promise.resolve('second'))

    const { result, rerender } = renderHook(({ dep }) => useAsyncResource(fetcher, [dep]), {
      initialProps: { dep: 'a' },
    })

    rerender({ dep: 'b' })
    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.data).toBe('second')

    // The first (now-stale) fetch resolving afterward must not overwrite state.
    act(() => resolveFirst('first'))
    expect(result.current.data).toBe('second')
  })
})
