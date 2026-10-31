import type { ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { routeTable } from '../../routes/routeTable'
import { NotificationBell } from '../../components/notifications/NotificationBell'
import { SessionExpiryWarning } from '../../components/session/SessionExpiryWarning'

interface AuthenticatedLayoutProps {
  children: ReactNode
}

/**
 * Persistent shell for all authenticated pages: role-filtered route navigation,
 * a top bar, and the content outlet.
 */
export function AuthenticatedLayout({ children }: AuthenticatedLayoutProps) {
  const { authState, clearAuthState } = useAuth()
  const { pathname } = useLocation()
  const navigationRoutes = routeTable.filter(
    (entry) =>
      entry.navigationLabel !== undefined &&
      authState.roles.some((role) => entry.requiredRoles.includes(role)),
  )

  return (
    <div className="app authenticated-layout">
      <a href="#main-content" className="skip-link">
        Skip to content
      </a>
      <aside className="sidebar authenticated-layout__sidebar" aria-label="Primary navigation">
        <div className="brand">
          <span className="brand-mark">
            <i className="icon icon-plane" />
          </span>
          <span className="brand-name">TravelEase</span>
        </div>
        <nav className="nav authenticated-layout__navigation" aria-label="Dashboard sections">
          <ul>
            {navigationRoutes.map((entry) => {
              const icon =
                entry.pageId === 'P-02'
                  ? 'dashboard'
                  : entry.pageId === 'P-03' || entry.pageId === 'P-08'
                    ? 'plane'
                    : entry.pageId === 'P-09' || entry.pageId === 'P-14'
                      ? 'receipt'
                      : entry.pageId === 'P-20'
                        ? 'clock'
                        : 'user'
              return (
                <li key={entry.pageId}>
                  <Link
                    to={entry.path}
                    aria-current={isNavigationRouteActive(entry.pageId, pathname) ? 'page' : undefined}
                    className={`nav-link authenticated-layout__navigation-link${
                      isNavigationRouteActive(entry.pageId, pathname) ? ' active' : ''
                    }`}
                  >
                    <i className={`icon icon-${icon}`} />
                    {entry.navigationLabel}
                  </Link>
                </li>
              )
            })}
          </ul>
        </nav>
      </aside>
      <div className="workspace authenticated-layout__body">
        <header className="topbar authenticated-layout__topbar">
          <div className="user authenticated-layout__user">
            <span className="user-avatar">
              <i className="icon icon-user" />
            </span>
            <span className="user-id numeric">{authState.userId ?? 'Guest'}</span>
            {authState.roles.map((role) => (
              <span className="role-badge" key={role}>
                {role}
              </span>
            ))}
          </div>
          <div className="topbar-actions">
            <NotificationBell />
            <Link className="button button-secondary" to="/account/password">
              <i className="icon icon-lock" />
              <span className="button-label">Change password</span>
            </Link>
            <button className="button button-secondary" type="button" onClick={clearAuthState}>
              <i className="icon icon-logout" />
              <span className="button-label">Logout</span>
            </button>
          </div>
        </header>
        <main id="main-content" className="content authenticated-layout__content">
          <SessionExpiryWarning />
          {children}
        </main>
      </div>
    </div>
  )
}

function isNavigationRouteActive(pageId: string, pathname: string): boolean {
  if (pageId === 'P-02') return pathname === '/'
  if (pageId === 'P-03') return pathname.startsWith('/travel-requests')
  if (pageId === 'P-09') {
    return (
      pathname !== '/expenses/review-queue' &&
      (pathname === '/expenses' ||
        pathname === '/expenses/new' ||
        /^\/expenses\/[^/]+(?:\/edit)?$/.test(pathname))
    )
  }
  if (pageId === 'P-14') {
    return (
      pathname === '/expenses/review-queue' ||
      /^\/expenses\/[^/]+\/(?:review|reimburse)$/.test(pathname)
    )
  }
  return pathname === routeTable.find((entry) => entry.pageId === pageId)?.path
}
