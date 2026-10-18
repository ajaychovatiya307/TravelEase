import { describe, expect, it, vi } from 'vitest'

import type { PaginatedResult } from '../models/pagination'
import { buildCsv, fetchAllPages } from './csvExport'

describe('CSV export helpers', () => {
  it('quotes fields and escapes spreadsheet formulas', () => {
    const csv = buildCsv(
      [{ name: 'Doe, Jane', note: '="unsafe"', count: 2 }],
      [
        { header: 'Name', value: (row) => row.name },
        { header: 'Note', value: (row) => row.note },
        { header: 'Count', value: (row) => row.count },
      ],
    )

    expect(csv).toBe('"Name","Note","Count"\r\n"Doe, Jane","\'=""unsafe""","2"')
  })

  it('fetches each page of a paginated result', async () => {
    const fetchPage = vi.fn(async (page: number, size: number) => {
      const pages: PaginatedResult<string>[] = [
        { content: ['first'], page: 0, size: 20, totalElements: 3, totalPages: 2 },
        { content: ['second', 'third'], page: 1, size: 20, totalElements: 3, totalPages: 2 },
      ]
      expect(size).toBe(20)
      return pages[page]
    })

    await expect(fetchAllPages(fetchPage)).resolves.toEqual(['first', 'second', 'third'])
    expect(fetchPage).toHaveBeenCalledTimes(2)
    expect(fetchPage).toHaveBeenNthCalledWith(1, 0, 20)
    expect(fetchPage).toHaveBeenNthCalledWith(2, 1, 20)
  })
})
