import type { PropsWithChildren } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export function ProtectedRoute({ children }: PropsWithChildren) {
  const { isAuthenticated } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return children
}

export function PublicOnlyRoute({ children }: PropsWithChildren) {
  const { isAuthenticated } = useAuth()

  if (isAuthenticated) {
    return <Navigate to="/app/book" replace />
  }

  return children
}
