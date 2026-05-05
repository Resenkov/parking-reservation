import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAppData } from '../context/AppDataContext'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'

export function RegisterPage() {
  const navigate = useNavigate()
  const { register } = useAppData()
  const { signIn } = useAuth()
  const { showToast } = useToast()
  const [pending, setPending] = useState(false)

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const formData = new FormData(event.currentTarget)

    setPending(true)
    try {
      const response = await register({
        firstName: String(formData.get('firstName') ?? '').trim(),
        lastName: String(formData.get('lastName') ?? '').trim(),
        email: String(formData.get('email') ?? '').trim(),
        password: String(formData.get('password') ?? ''),
      })
      signIn(response.token)
      navigate('/app/book', { replace: true })
      showToast('Аккаунт создан', 'success')
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Не удалось создать аккаунт', 'error')
    } finally {
      setPending(false)
    }
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit}>
      <div className="field-grid field-grid--two">
        <label className="field">
          <span>Имя</span>
          <input name="firstName" type="text" minLength={2} maxLength={64} required />
        </label>

        <label className="field">
          <span>Фамилия</span>
          <input name="lastName" type="text" minLength={2} maxLength={64} required />
        </label>
      </div>

      <label className="field">
        <span>Email</span>
        <input name="email" type="email" autoComplete="email" required />
      </label>

      <label className="field">
        <span>Пароль</span>
        <input
          name="password"
          type="password"
          autoComplete="new-password"
          minLength={8}
          maxLength={100}
          required
        />
      </label>

      <button type="submit" className="primary-button primary-button--wide" disabled={pending}>
        {pending ? 'Создание...' : 'Создать аккаунт'}
      </button>
    </form>
  )
}
