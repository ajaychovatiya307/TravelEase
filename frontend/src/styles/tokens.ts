// JS-side mirror of src/styles/tokens.css breakpoint scale, for runtime viewport checks
// (e.g., DataTable's card-layout fallback, frontend-clarification.md Q24).
export const breakpoints = {
  mobile: 0,
  tablet: 600,
  desktop: 1024,
} as const

// Below this width, tables render as stacked cards instead of columns.
export const TABLE_CARD_BREAKPOINT = 768

export type BreakpointKey = keyof typeof breakpoints
