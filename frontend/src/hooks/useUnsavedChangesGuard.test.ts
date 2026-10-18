// useUnsavedChangesGuard unit tests (G-02, frontend-tasks.md — "G. Guards").
// Simulates a dirty and a clean form state and asserts prompt behavior
// (window.confirm) accordingly, plus the beforeunload wiring.

import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  DEFAULT_UNSAVED_CHANGES_MESSAGE,
  useUnsavedChangesGuard,
} from './useUnsavedChangesGuard'

describe('useUnsavedChangesGuard (G-02)', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('guardedNavigate', () => {
    it('runs the action immediately without prompting when clean', () => {
      const confirmSpy = vi.spyOn(window, 'confirm')
      const { result } = renderHook(() => useUnsavedChangesGuard({ isDirty: false }))
      const action = vi.fn()

      act(() => result.current.guardedNavigate(action))

      expect(confirmSpy).not.toHaveBeenCalled()
      expect(action).toHaveBeenCalledTimes(1)
    })

    it('prompts for confirmation when dirty, and runs the action if confirmed', () => {
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
      const { result } = renderHook(() => useUnsavedChangesGuard({ isDirty: true }))
      const action = vi.fn()

      act(() => result.current.guardedNavigate(action))

      expect(confirmSpy).toHaveBeenCalledWith(DEFAULT_UNSAVED_CHANGES_MESSAGE)
      expect(action).toHaveBeenCalledTimes(1)
    })

    it('prompts for confirmation when dirty and does NOT run the action if dismissed', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false)
      const { result } = renderHook(() => useUnsavedChangesGuard({ isDirty: true }))
      const action = vi.fn()

      act(() => result.current.guardedNavigate(action))

      expect(action).not.toHaveBeenCalled()
    })

    it('always reads the latest isDirty value, not the value at mount', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      const { result, rerender } = renderHook(
        ({ isDirty }) => useUnsavedChangesGuard({ isDirty }),
        { initialProps: { isDirty: false } },
      )

      rerender({ isDirty: true })
      const action = vi.fn()
      act(() => result.current.guardedNavigate(action))

      expect(window.confirm).toHaveBeenCalled()
      expect(action).toHaveBeenCalledTimes(1)
    })

    it('uses a custom message when provided', () => {
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
      const { result } = renderHook(() =>
        useUnsavedChangesGuard({ isDirty: true, message: 'Discard your edits?' }),
      )

      act(() => result.current.guardedNavigate(vi.fn()))

      expect(confirmSpy).toHaveBeenCalledWith('Discard your edits?')
    })
  })

  describe('beforeunload', () => {
    let addSpy: ReturnType<typeof vi.spyOn>
    let removeSpy: ReturnType<typeof vi.spyOn>

    beforeEach(() => {
      addSpy = vi.spyOn(window, 'addEventListener')
      removeSpy = vi.spyOn(window, 'removeEventListener')
    })

    /**
     * Captures and invokes the registered `beforeunload` handler directly with
     * a minimal fake event, rather than `window.dispatchEvent` — jsdom's
     * generic `Event` does not implement the legacy `returnValue` IDL
     * attribute real browsers expose on `BeforeUnloadEvent`, so a real
     * dispatch can't observe the hook's assignment.
     */
    type FakeBeforeUnloadEvent = {
      preventDefault: ReturnType<typeof vi.fn>
      returnValue: unknown
    }

    function invokeBeforeUnloadHandler(): FakeBeforeUnloadEvent {
      renderHook(() => useUnsavedChangesGuard({ isDirty: true }))
      const [, handler] = addSpy.mock.calls.find(
        ([type]: [string, unknown]) => type === 'beforeunload',
      ) as [string, EventListener]
      const event: FakeBeforeUnloadEvent = { preventDefault: vi.fn(), returnValue: false }
      handler(event as unknown as BeforeUnloadEvent)
      return event
    }

    it('registers a beforeunload listener on mount and removes it on unmount', () => {
      const { unmount } = renderHook(() => useUnsavedChangesGuard({ isDirty: true }))
      expect(addSpy).toHaveBeenCalledWith('beforeunload', expect.any(Function))

      unmount()
      expect(removeSpy).toHaveBeenCalledWith('beforeunload', expect.any(Function))
    })

    it('prevents default and sets returnValue when dirty', () => {
      const event = invokeBeforeUnloadHandler()
      expect(event.preventDefault).toHaveBeenCalled()
      expect(event.returnValue).toBe(DEFAULT_UNSAVED_CHANGES_MESSAGE)
    })

    it('does nothing when clean', () => {
      renderHook(() => useUnsavedChangesGuard({ isDirty: false }))
      const [, handler] = addSpy.mock.calls.find(
        ([type]: [string, unknown]) => type === 'beforeunload',
      ) as [string, EventListener]
      const event: FakeBeforeUnloadEvent = { preventDefault: vi.fn(), returnValue: false }
      handler(event as unknown as BeforeUnloadEvent)
      expect(event.preventDefault).not.toHaveBeenCalled()
    })
  })
})
