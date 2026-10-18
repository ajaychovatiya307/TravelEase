import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { toClassifiedError } from '../../services/httpClient'
import { DocumentDownloadButton } from './DocumentDownloadButton'

describe('DocumentDownloadButton', () => {
  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:mock-url')
    URL.revokeObjectURL = vi.fn()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('fetches through the API client and saves the file via a temporary object URL', async () => {
    const download = vi.fn().mockResolvedValue(new Blob(['%PDF'], { type: 'application/pdf' }))
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    render(<DocumentDownloadButton fileName="receipt.pdf" download={download} />)

    await userEvent.click(screen.getByRole('button', { name: 'Download receipt.pdf' }))

    await waitFor(() => expect(click).toHaveBeenCalledTimes(1))
    expect(download).toHaveBeenCalledTimes(1)
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it.each([
    ['FORBIDDEN_ACTION', 'You do not have access to this document.'],
    ['RESOURCE_NOT_FOUND', 'This document is no longer available.'],
    ['INTERNAL_ERROR', 'The document could not be downloaded. Please try again.'],
  ] as const)('shows a clear message for %s', async (category, message) => {
    const download = vi.fn().mockRejectedValue(
      toClassifiedError({ category, status: 0, errorCode: category, message: 'x', timestamp: '', fieldErrors: [] }),
    )
    render(<DocumentDownloadButton fileName="receipt.pdf" download={download} />)

    await userEvent.click(screen.getByRole('button', { name: 'Download receipt.pdf' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
  })
})
