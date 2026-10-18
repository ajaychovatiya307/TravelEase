import type { PaginatedResult } from '../models/pagination'

export interface CsvColumn<T> {
  header: string
  value: (row: T) => string | number | boolean | null
}

function escapeCsvValue(value: string | number | boolean | null): string {
  const text = value === null ? '' : String(value)
  const safeText = /^[\t\r\n ]*[=+\-@]/.test(text) ? `'${text}` : text
  return `"${safeText.replaceAll('"', '""')}"`
}

export function buildCsv<T>(rows: T[], columns: CsvColumn<T>[]): string {
  const headers = columns.map((column) => escapeCsvValue(column.header))
  const records = rows.map((row) => columns.map((column) => escapeCsvValue(column.value(row))))

  return [headers, ...records].map((record) => record.join(',')).join('\r\n')
}

export async function fetchAllPages<T>(
  fetchPage: (page: number, size: number) => Promise<PaginatedResult<T>>,
): Promise<T[]> {
  const pageSize = 20
  const firstPage = await fetchPage(0, pageSize)
  const rows = [...firstPage.content]

  for (let page = 1; page < firstPage.totalPages; page += 1) {
    const result = await fetchPage(page, pageSize)
    rows.push(...result.content)
  }

  return rows
}

export function downloadCsv<T>(filename: string, rows: T[], columns: CsvColumn<T>[]): void {
  const blob = new Blob([`\uFEFF${buildCsv(rows, columns)}`], {
    type: 'text/csv;charset=utf-8',
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
