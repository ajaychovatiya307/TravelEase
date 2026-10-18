import { useState } from 'react'
import { isClassifiedErrorInstance } from '../../services/httpClient'

interface DocumentDownloadButtonProps {
  fileName: string
  /** Fetches the document through the authenticated API client. */
  download: () => Promise<Blob>
}

function downloadErrorMessage(error: unknown): string {
  if (isClassifiedErrorInstance(error)) {
    if (error.category === 'FORBIDDEN_ACTION') return 'You do not have access to this document.'
    if (error.category === 'RESOURCE_NOT_FOUND') return 'This document is no longer available.'
  }
  return 'The document could not be downloaded. Please try again.'
}

/**
 * Downloads a stored document. The request goes through the shared HTTP client (bearer token,
 * session-expiry handling), and the file is saved via a temporary object URL; the server sends it
 * as an attachment with `nosniff`, so it is never rendered inside the app.
 */
export function DocumentDownloadButton({ fileName, download }: DocumentDownloadButtonProps) {
  const [isDownloading, setIsDownloading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleClick = async () => {
    setIsDownloading(true)
    setError(null)
    try {
      const blob = await download()
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = fileName
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    } catch (caught) {
      setError(downloadErrorMessage(caught))
    } finally {
      setIsDownloading(false)
    }
  }

  return (
    <span className="document-download">
      <button
        className="button button-secondary button-small"
        type="button"
        onClick={handleClick}
        disabled={isDownloading}
        aria-label={`Download ${fileName}`}
      >
        {isDownloading ? 'Downloading…' : 'Download'}
      </button>
      {error ? (
        <span className="required" role="alert" style={{ marginLeft: 8, fontSize: 13 }}>
          {error}
        </span>
      ) : null}
    </span>
  )
}
