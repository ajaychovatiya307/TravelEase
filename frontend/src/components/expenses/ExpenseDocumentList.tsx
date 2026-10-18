import type { ExpenseClaimResponse } from '../../models/expenseClaim'
import * as expenseService from '../../services/expenseService'
import { DocumentDownloadButton } from '../../shared/DocumentDownload'

// Contract `long` fileSizeBytes rendered in a human-readable size (B/KB/MB).
function displayFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const kilobytes = bytes / 1024
  if (kilobytes < 1024) {
    return `${kilobytes.toFixed(1)} KB`
  }
  return `${(kilobytes / 1024).toFixed(1)} MB`
}

interface ExpenseDocumentListProps {
  claim: ExpenseClaimResponse
}

/**
 * The claim's documents (api-contract.md §5.9 → §5.6 `DocumentSummary`) with a download action
 * for each successfully stored file. Used on the claim detail (P-10) and Finance review (P-13)
 * pages; access is enforced by the download endpoint (owner, FINANCE, ADMIN).
 */
export function ExpenseDocumentList({ claim }: ExpenseDocumentListProps) {
  return (
    <div className="card card-medium">
      <div className="card-header">
        <h2>
          <i className="icon icon-file" />
          Documents
        </h2>
      </div>
      <div className="card-body">
        {claim.documents.length === 0 ? (
          <p className="empty-state">No documents uploaded.</p>
        ) : (
          <dl className="detail-list">
            {claim.documents.map((doc) => (
              <div key={doc.id} className="detail-row">
                <dt>{doc.fileName}</dt>
                <dd>
                  {doc.fileType} · {displayFileSize(doc.fileSizeBytes)}
                  {doc.uploadStatus === 'CONFIRMED' ? (
                    <>
                      {' '}
                      <DocumentDownloadButton
                        fileName={doc.fileName}
                        download={() => expenseService.downloadDocument(claim.id, doc.id)}
                      />
                    </>
                  ) : (
                    <span className="required"> · upload failed</span>
                  )}
                </dd>
              </div>
            ))}
          </dl>
        )}
      </div>
    </div>
  )
}
