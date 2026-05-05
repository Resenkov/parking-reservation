import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAppData } from '../context/AppDataContext'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'

export function LoginPage() {
  const navigate = useNavigate()
  const { login } = useAppData()
  const { signIn } = useAuth()
  const { showToast } = useToast()
  const [pending, setPending] = useState(false)

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const formData = new FormData(event.currentTarget)

    setPending(true)
    try {
      const response = await login({
        email: String(formData.get('email') ?? '').trim(),
        password: String(formData.get('password') ?? ''),
      })
      signIn(response.token)
      navigate('/app/book', { replace: true })
      showToast('Вход выполнен', 'success')
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Не удалось выполнить вход', 'error')
    } finally {
      setPending(false)
    }
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit}>
      <label className="field">
        <span>Email</span>
        <input name="email" type="email" autoComplete="email" required />
      </label>

      <label className="field">
        <span>Пароль</span>
        <input
          name="password"
          type="password"
          autoComplete="current-password"
          minLength={8}
          required
        />
      </label>

      <button type="submit" className="primary-button primary-button--wide" disabled={pending}>
        {pending ? 'Вход...' : 'Войти'}
      </button>
    </form>
  )
}
