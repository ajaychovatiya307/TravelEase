import { useState } from 'react'
import type { ChangeEvent } from 'react'
import {
  DEFAULT_ACCEPTED_TYPES,
  DEFAULT_MAX_COUNT,
  DEFAULT_MAX_SIZE_BYTES,
  validateFile,
} from '../../validation/fileUploadRules'
import type { UploadedDocument, UploadStatus } from './FileUploadControl.types'
import './FileUploadControl.css'

interface UploadEntry {
  tempId: string
  file: File
  status: UploadStatus
  documentId?: string
}

interface FileUploadControlProps {
  id: string
  label: string
  acceptedTypes?: readonly string[]
  maxSizeBytes?: number
  maxCount?: number
  /** Uploads the file immediately on valid selection; resolves with the confirmed document. */
  onUpload: (file: File) => Promise<UploadedDocument>
  /** Notified whenever the overall list of uploaded/pending documents changes. */
  onChange?: (documents: UploadedDocument[]) => void
}

function toUploadedDocuments(entries: UploadEntry[]): UploadedDocument[] {
  return entries.map((entry) => ({
    documentId: entry.documentId ?? entry.tempId,
    fileName: entry.file.name,
    uploadStatus: entry.status,
  }))
}

/**
 * Shared file upload control (frontend-constitution.md §11): validates type/size/count before
 * upload, uploads immediately on valid selection, and shows per-file status with retry on failure
 * (frontend-clarification.md Q10/Q25).
 */
export function FileUploadControl({
  id,
  label,
  acceptedTypes = DEFAULT_ACCEPTED_TYPES,
  maxSizeBytes = DEFAULT_MAX_SIZE_BYTES,
  maxCount = DEFAULT_MAX_COUNT,
  onUpload,
  onChange,
}: FileUploadControlProps) {
  const [entries, setEntries] = useState<UploadEntry[]>([])
  const [validationError, setValidationError] = useState<string | undefined>()

  function updateEntries(next: UploadEntry[]) {
    setEntries(next)
    onChange?.(toUploadedDocuments(next))
  }

  function upload(entry: UploadEntry, current: UploadEntry[]) {
    onUpload(entry.file).then(
      (document) => {
        updateEntries(
          current.map((item) =>
            item.tempId === entry.tempId
              ? { ...item, status: 'CONFIRMED', documentId: document.documentId }
              : item,
          ),
        )
      },
      () => {
        updateEntries(
          current.map((item) =>
            item.tempId === entry.tempId ? { ...item, status: 'FAILED' } : item,
          ),
        )
      },
    )
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? [])
    event.target.value = ''
    setValidationError(undefined)

    let currentEntries = entries
    for (const file of files) {
      const error = validateFile(file, currentEntries.length, acceptedTypes, maxSizeBytes, maxCount)
      if (error) {
        setValidationError(error)
        continue
      }
      const entry: UploadEntry = { tempId: crypto.randomUUID(), file, status: 'PENDING' }
      const next = [...currentEntries, entry]
      currentEntries = next
      updateEntries(next)
      upload(entry, next)
    }
  }

  function handleRetry(tempId: string) {
    const entry = entries.find((item) => item.tempId === tempId)
    if (!entry) return
    const next = entries.map((item) =>
      item.tempId === tempId ? { ...item, status: 'PENDING' as const } : item,
    )
    updateEntries(next)
    upload({ ...entry, status: 'PENDING' }, next)
  }

  return (
    <div className="file-upload-control">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type="file"
        className="file-input"
        accept={acceptedTypes.join(',')}
        multiple
        onChange={handleFileChange}
        aria-describedby={validationError ? `${id}-error` : undefined}
      />
      {validationError ? (
        <p id={`${id}-error`} role="alert" className="required" style={{ fontSize: 13 }}>
          {validationError}
        </p>
      ) : null}

      {entries.length === 0 ? (
        <p className="empty-state">No files uploaded yet</p>
      ) : (
        <dl className="detail-list">
          {entries.map((entry) => (
            <div key={entry.tempId} className="detail-row">
              <dt>{entry.file.name}</dt>
              <dd>{entry.status}</dd>
              {entry.status === 'FAILED' ? (
                <button
                  className="button button-secondary button-small"
                  type="button"
                  onClick={() => handleRetry(entry.tempId)}
                >
                  Retry
                </button>
              ) : null}
            </div>
          ))}
        </dl>
      )}
    </div>
  )
}
