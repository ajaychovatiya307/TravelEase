import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { FileUploadControl } from './FileUploadControl'

function makeFile(name: string, type: string, sizeBytes: number): File {
  const file = new File(['x'.repeat(Math.min(sizeBytes, 10))], name, { type })
  Object.defineProperty(file, 'size', { value: sizeBytes })
  return file
}

describe('FileUploadControl', () => {
  it('rejects an unsupported file type with an inline error before upload', async () => {
    const onUpload = vi.fn()
    render(<FileUploadControl id="docs" label="Documents" onUpload={onUpload} />)

    const file = makeFile('malware.exe', 'application/octet-stream', 1024)
    const input = screen.getByLabelText('Documents')
    fireEvent.change(input, { target: { files: [file] } })

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Only PDF, JPG, and PNG files are supported.',
    )
    expect(onUpload).not.toHaveBeenCalled()
  })

  it('rejects an oversized file with an inline error before upload', async () => {
    const user = userEvent.setup()
    const onUpload = vi.fn()
    render(<FileUploadControl id="docs" label="Documents" onUpload={onUpload} />)

    const file = makeFile('receipt.pdf', 'application/pdf', 6 * 1024 * 1024)
    await user.upload(screen.getByLabelText('Documents'), file)

    expect(screen.getByRole('alert')).toHaveTextContent('File exceeds the 5 MB size limit.')
    expect(onUpload).not.toHaveBeenCalled()
  })

  it('accepts a file exactly at the 5 MB limit', async () => {
    const user = userEvent.setup()
    const onUpload = vi.fn().mockResolvedValue({
      documentId: 'DOC1',
      fileName: 'receipt.pdf',
      uploadStatus: 'CONFIRMED',
    })
    render(<FileUploadControl id="docs" label="Documents" onUpload={onUpload} />)

    const file = makeFile('receipt.pdf', 'application/pdf', 5 * 1024 * 1024)
    await user.upload(screen.getByLabelText('Documents'), file)

    expect(onUpload).toHaveBeenCalledWith(file)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('rejects files beyond five when selected together', async () => {
    const user = userEvent.setup()
    const onUpload = vi.fn().mockResolvedValue({
      documentId: 'DOC1',
      fileName: 'receipt.pdf',
      uploadStatus: 'CONFIRMED',
    })
    render(<FileUploadControl id="docs" label="Documents" onUpload={onUpload} />)

    const files = Array.from({ length: 6 }, (_, index) =>
      makeFile(`receipt-${index}.pdf`, 'application/pdf', 1024),
    )
    await user.upload(screen.getByLabelText('Documents'), files)

    expect(onUpload).toHaveBeenCalledTimes(5)
    expect(screen.getByRole('alert')).toHaveTextContent('A maximum of 5 files is allowed.')
  })

  it('triggers the upload callback immediately on a valid selection', async () => {
    const user = userEvent.setup()
    const onUpload = vi.fn().mockResolvedValue({
      documentId: 'DOC1',
      fileName: 'receipt.pdf',
      uploadStatus: 'CONFIRMED',
    })
    render(<FileUploadControl id="docs" label="Documents" onUpload={onUpload} />)

    const file = makeFile('receipt.pdf', 'application/pdf', 1024)
    await user.upload(screen.getByLabelText('Documents'), file)

    expect(onUpload).toHaveBeenCalledWith(file)
    await waitFor(() => expect(screen.getByText('CONFIRMED')).toBeInTheDocument())
  })

  it('shows FAILED status and a retry action when upload fails, then succeeds on retry', async () => {
    const user = userEvent.setup()
    const onUpload = vi
      .fn()
      .mockRejectedValueOnce(new Error('network error'))
      .mockResolvedValueOnce({
        documentId: 'DOC1',
        fileName: 'receipt.pdf',
        uploadStatus: 'CONFIRMED',
      })
    render(<FileUploadControl id="docs" label="Documents" onUpload={onUpload} />)

    const file = makeFile('receipt.pdf', 'application/pdf', 1024)
    await user.upload(screen.getByLabelText('Documents'), file)

    await waitFor(() => expect(screen.getByText('FAILED')).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: 'Retry' }))

    expect(onUpload).toHaveBeenCalledTimes(2)
    await waitFor(() => expect(screen.getByText('CONFIRMED')).toBeInTheDocument())
  })
})
