import { describe, expect, it } from 'vitest'

import {
  DEFAULT_ACCEPTED_TYPES,
  DEFAULT_MAX_COUNT,
  DEFAULT_MAX_SIZE_BYTES,
  FILE_UPLOAD_MESSAGES,
  validateFile,
} from './fileUploadRules'

const validPdf = { type: 'application/pdf', size: 1024 }

describe('file upload rules (L-05, VAL-11)', () => {
  it.each(DEFAULT_ACCEPTED_TYPES)('accepts supported MIME type %s', (type) => {
    expect(validateFile({ type, size: 1024 }, 0)).toBeUndefined()
  })

  it('rejects unsupported MIME types', () => {
    expect(validateFile({ type: 'application/octet-stream', size: 1024 }, 0)).toBe(
      FILE_UPLOAD_MESSAGES.unsupportedType,
    )
  })

  it('accepts a file exactly at the 5 MB limit', () => {
    expect(validateFile({ ...validPdf, size: DEFAULT_MAX_SIZE_BYTES }, 0)).toBeUndefined()
  })

  it('rejects a file one byte over the 5 MB limit', () => {
    expect(
      validateFile({ ...validPdf, size: DEFAULT_MAX_SIZE_BYTES + 1 }, 0),
    ).toBe(FILE_UPLOAD_MESSAGES.oversized)
  })

  it('accepts the fifth file', () => {
    expect(validateFile(validPdf, DEFAULT_MAX_COUNT - 1)).toBeUndefined()
  })

  it('rejects the sixth file', () => {
    expect(validateFile(validPdf, DEFAULT_MAX_COUNT)).toBe(
      FILE_UPLOAD_MESSAGES.tooManyFiles(DEFAULT_MAX_COUNT),
    )
  })

  it('allows caller-provided type, size, and count limits', () => {
    expect(validateFile({ type: 'text/plain', size: 5 }, 1, ['text/plain'], 5, 2)).toBeUndefined()
  })
})
