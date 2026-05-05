import { useEffect, useState } from 'react'
import { useAppData } from '../context/AppDataContext'
import { useToast } from '../context/ToastContext'
import { roleLabel } from '../lib/format'

export function ProfilePage() {
  const { profile, updateProfile } = useAppData()
  const { showToast } = useToast()
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    setFirstName(profile?.firstName ?? '')
    setLastName(profile?.lastName ?? '')
  }, [profile])
  /* eslint-enable react-hooks/set-state-in-effect */

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmedFirstName = firstName.trim()
    const trimmedLastName = lastName.trim()

    if (!trimmedFirstName || !trimmedLastName) {
      showToast('Имя и фамилия обязательны', 'error')
      return
    }

    try {
      setPending(true)
      await updateProfile({
        firstName: trimmedFirstName,
        lastName: trimmedLastName,
        password: password.trim() || undefined,
      })
      setPassword('')
      showToast('Профиль обновлён', 'success')
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Не удалось обновить профиль', 'error')
    } finally {
      setPending(false)
    }
  }

  return (
    <section className="panel panel--elevated">
      <div className="panel__header">
        <div>
          <h3 className="panel__title">Профиль</h3>
          <p className="panel__meta">{profile?.email ?? '-'}</p>
        </div>

        <div className="role-list">
          {(profile?.roles ?? []).map((role) => (
            <span key={role} className="role-pill">
              {roleLabel(role)}
            </span>
          ))}
        </div>
      </div>

      <form className="field-grid field-grid--two" onSubmit={handleSubmit}>
        <label className="field">
          <span>Имя</span>
          <input
            type="text"
            value={firstName}
            onChange={(event) => setFirstName(event.target.value)}
            minLength={2}
            maxLength={64}
            required
          />
        </label>

        <label className="field">
          <span>Фамилия</span>
          <input
            type="text"
            value={lastName}
            onChange={(event) => setLastName(event.target.value)}
            minLength={2}
            maxLength={64}
            required
          />
        </label>

        <label className="field field--full">
          <span>Новый пароль</span>
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            minLength={8}
            maxLength={100}
            placeholder="Оставьте поле пустым, если пароль менять не нужно"
          />
        </label>

        <div className="form-actions field--full">
          <button type="submit" className="primary-button" disabled={pending}>
            {pending ? 'Сохранение...' : 'Сохранить'}
          </button>
        </div>
      </form>
    </section>
  )
}
