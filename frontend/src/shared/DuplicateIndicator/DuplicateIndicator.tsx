interface DuplicateIndicatorProps {
  duplicateFlag: boolean
}

/**
 * Flags a potential-duplicate expense claim as text + icon, not color alone
 * (frontend-requirement.md §11). Renders nothing when the flag is false.
 */
export function DuplicateIndicator({ duplicateFlag }: DuplicateIndicatorProps) {
  if (!duplicateFlag) {
    return null
  }
  return (
    <span className="status status-duplicate duplicate-indicator">
      <i className="icon icon-alert" />
      Possible duplicate
    </span>
  )
}
