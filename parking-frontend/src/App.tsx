import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppDataProvider } from './context/AppDataContext'
import { AuthProvider } from './context/AuthContext'
import { ToastProvider } from './context/ToastContext'
import { ProtectedRoute, PublicOnlyRoute } from './components/RouteGuards'
import { AppShell } from './layouts/AppShell'
import { AuthLayout } from './layouts/AuthLayout'
import { BookingPage } from './pages/BookingPage'
import { BookingsPage } from './pages/BookingsPage'
import { LoginPage } from './pages/LoginPage'
import { ProfilePage } from './pages/ProfilePage'
import { RegisterPage } from './pages/RegisterPage'
import { WalletPage } from './pages/WalletPage'

function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <AppDataProvider>
            <Routes>
              <Route
                path="/login"
                element={
                  <PublicOnlyRoute>
                    <AuthLayout>
                      <LoginPage />
                    </AuthLayout>
                  </PublicOnlyRoute>
                }
              />

              <Route
                path="/register"
                element={
                  <PublicOnlyRoute>
                    <AuthLayout>
                      <RegisterPage />
                    </AuthLayout>
                  </PublicOnlyRoute>
                }
              />

              <Route
                path="/app"
                element={
                  <ProtectedRoute>
                    <AppShell />
                  </ProtectedRoute>
                }
              >
                <Route index element={<Navigate to="/app/book" replace />} />
                <Route path="book" element={<BookingPage />} />
                <Route path="bookings" element={<BookingsPage />} />
                <Route path="wallet" element={<WalletPage />} />
                <Route path="profile" element={<ProfilePage />} />
              </Route>

              <Route path="*" element={<Navigate to="/login" replace />} />
            </Routes>
          </AppDataProvider>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}

export default App
