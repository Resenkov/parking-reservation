import type { Dispatch, FormEvent, SetStateAction } from 'react'
import type { ReservationPolicySettings } from '../../types/models'

interface PolicySettingsPanelProps {
  policy: ReservationPolicySettings
  savingSettings: boolean
  setPolicy: Dispatch<SetStateAction<ReservationPolicySettings>>
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
}

export function PolicySettingsPanel({
  policy,
  savingSettings,
  setPolicy,
  onSubmit,
}: PolicySettingsPanelProps) {
  return (
    <section className="panel panel--elevated">
      <div className="panel__header">
        <div>
          <h3 className="panel__title">Политика бронирования</h3>
        </div>
      </div>

      <form className="admin-form" onSubmit={onSubmit}>
        <div className="field-grid field-grid--four">
          <label className="field">
            <span>Шаг брони, мин</span>
            <input
              type="number"
              min={1}
              value={policy.bookingStepMinutes}
              onChange={(event) =>
                setPolicy((current) => ({
                  ...current,
                  bookingStepMinutes: Number(event.target.value),
                }))
              }
            />
          </label>
          <label className="field">
            <span>HOLD, мин</span>
            <input
              type="number"
              min={1}
              value={policy.holdDurationMinutes}
              onChange={(event) =>
                setPolicy((current) => ({
                  ...current,
                  holdDurationMinutes: Number(event.target.value),
                }))
              }
            />
          </label>
          <label className="field">
            <span>Крайний заезд до конца, мин</span>
            <input
              type="number"
              min={0}
              value={policy.arrivalDeadlineMinutesBeforeEnd}
              onChange={(event) =>
                setPolicy((current) => ({
                  ...current,
                  arrivalDeadlineMinutesBeforeEnd: Number(event.target.value),
                }))
              }
            />
          </label>
          <label className="field">
            <span>Обычный возврат, %</span>
            <input
              type="number"
              min={0}
              max={100}
              value={policy.standardCancellationRefundPercent}
              onChange={(event) =>
                setPolicy((current) => ({
                  ...current,
                  standardCancellationRefundPercent: Number(event.target.value),
                }))
              }
            />
          </label>
          <label className="field">
            <span>NO_SHOW возврат, %</span>
            <input
              type="number"
              min={0}
              max={100}
              value={policy.noShowRefundPercent}
              onChange={(event) =>
                setPolicy((current) => ({
                  ...current,
                  noShowRefundPercent: Number(event.target.value),
                }))
              }
            />
          </label>
          <label className="field">
            <span>Макс. длительность, мин</span>
            <input
              type="number"
              min={1}
              value={policy.maxBookingDurationMinutes}
              onChange={(event) =>
                setPolicy((current) => ({
                  ...current,
                  maxBookingDurationMinutes: Number(event.target.value),
                }))
              }
            />
          </label>
          <label className="field">
            <span>Бронирование вперёд, дней</span>
            <input
              type="number"
              min={1}
              value={policy.maxBookingAheadDays}
              onChange={(event) =>
                setPolicy((current) => ({
                  ...current,
                  maxBookingAheadDays: Number(event.target.value),
                }))
              }
            />
          </label>
        </div>

        <div className="admin-form__actions">
          <button type="submit" className="primary-button" disabled={savingSettings}>
            {savingSettings ? 'Сохранение...' : 'Сохранить настройки'}
          </button>
        </div>
      </form>
    </section>
  )
}
