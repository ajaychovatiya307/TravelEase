/** VAL-11: accepted file MIME types for document uploads. */
export const DEFAULT_ACCEPTED_TYPES = ['application/pdf', 'image/jpeg', 'image/png'] as const

/** VAL-11: maximum upload size, inclusive, in bytes. */
export const DEFAULT_MAX_SIZE_BYTES = 5 * 1024 * 1024

/** VAL-11: maximum number of files attached to one record. */
export const DEFAULT_MAX_COUNT = 5

export const FILE_UPLOAD_MESSAGES = {
  unsupportedType: 'Only PDF, JPG, and PNG files are supported.',
  oversized: 'File exceeds the 5 MB size limit.',
  tooManyFiles: (maxCount: number) => `A maximum of ${maxCount} files is allowed.`,
} as const

export interface FileUploadCandidate {
  type: string
  size: number
}

/**
 * Validates one file before upload.
 *
 * `existingCount` is the number of files already accepted, so a count of four
 * permits the fifth file while a count of five rejects the next file.
 */
export function validateFile(
  file: FileUploadCandidate,
  existingCount: number,
  acceptedTypes: readonly string[] = DEFAULT_ACCEPTED_TYPES,
  maxSizeBytes = DEFAULT_MAX_SIZE_BYTES,
  maxCount = DEFAULT_MAX_COUNT,
): string | undefined {
  if (!acceptedTypes.includes(file.type)) {
    return FILE_UPLOAD_MESSAGES.unsupportedType
  }
  if (file.size > maxSizeBytes) {
    return FILE_UPLOAD_MESSAGES.oversized
  }
  if (existingCount >= maxCount) {
    return FILE_UPLOAD_MESSAGES.tooManyFiles(maxCount)
  }
  return undefined
}
