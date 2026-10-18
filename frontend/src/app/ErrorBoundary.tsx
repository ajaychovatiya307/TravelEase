import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'
import { GenericErrorPage } from '../shared/GenericErrorPage'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
}

/**
 * Top-level error boundary (frontend-plan.md §11): catches unexpected rendering exceptions
 * and renders GenericErrorPage instead of a blank screen.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Unhandled rendering error caught by ErrorBoundary', error, errorInfo)
  }

  render() {
    if (this.state.hasError) {
      return <GenericErrorPage />
    }
    return this.props.children
  }
}
