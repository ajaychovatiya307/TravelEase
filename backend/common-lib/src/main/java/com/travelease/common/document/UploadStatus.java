package com.travelease.common.document;

/**
 * Upload lifecycle status for supporting documents stored in external object storage
 * (data-model.md §3.2/§4.2, FR-DOC-02). FAILED/PENDING support the draft-on-failure
 * handling required by FR-DOC-03.
 */
public enum UploadStatus {
    PENDING,
    CONFIRMED,
    FAILED
}
