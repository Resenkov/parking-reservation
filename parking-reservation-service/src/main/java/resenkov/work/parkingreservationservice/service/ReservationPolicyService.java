package resenkov.work.parkingreservationservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import resenkov.work.parkingreservationservice.dto.ReservationPolicySettingsResponse;
import resenkov.work.parkingreservationservice.dto.admin.ReservationPolicySettingsUpdateRequest;
import resenkov.work.parkingreservationservice.entity.ReservationPolicySettings;
import resenkov.work.parkingreservationservice.repository.ReservationPolicySettingsRepository;

@Service
public class ReservationPolicyService {

    private static final int DEFAULT_BOOKING_STEP_MINUTES = 15;
    private static final int DEFAULT_HOLD_DURATION_MINUTES = 5;
    private static final int DEFAULT_ARRIVAL_DEADLINE_MINUTES_BEFORE_END = 15;
    private static final int DEFAULT_STANDARD_CANCELLATION_REFUND_PERCENT = 60;
    private static final int DEFAULT_NO_SHOW_REFUND_PERCENT = 0;
    private static final int DEFAULT_MAX_BOOKING_DURATION_MINUTES = 12 * 60;
    private static final int DEFAULT_MAX_BOOKING_AHEAD_DAYS = 7;

    private final ReservationPolicySettingsRepository repository;

    public ReservationPolicyService(ReservationPolicySettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ReservationPolicySettings getSettings() {
        return repository.findById(ReservationPolicySettings.SINGLETON_ID)
                .orElseGet(this::createDefaultSettings);
    }

    @Transactional(readOnly = true)
    public ReservationPolicySettingsResponse getSettingsResponse() {
        return toResponse(repository.findById(ReservationPolicySettings.SINGLETON_ID)
                .orElseGet(this::defaultSettings));
    }

    @Transactional
    public ReservationPolicySettingsResponse updateSettings(ReservationPolicySettingsUpdateRequest request) {
        validateSettings(request);

        ReservationPolicySettings settings = repository.findById(ReservationPolicySettings.SINGLETON_ID)
                .orElseGet(this::defaultSettings);

        settings.setBookingStepMinutes(request.getBookingStepMinutes());
        settings.setHoldDurationMinutes(request.getHoldDurationMinutes());
        settings.setArrivalDeadlineMinutesBeforeEnd(request.getArrivalDeadlineMinutesBeforeEnd());
        settings.setStandardCancellationRefundPercent(request.getStandardCancellationRefundPercent());
        settings.setNoShowRefundPercent(request.getNoShowRefundPercent());
        settings.setMaxBookingDurationMinutes(request.getMaxBookingDurationMinutes());
        settings.setMaxBookingAheadDays(request.getMaxBookingAheadDays());

        return toResponse(repository.save(settings));
    }

    private void validateSettings(ReservationPolicySettingsUpdateRequest request) {
        if (request.getBookingStepMinutes() > request.getMaxBookingDurationMinutes()) {
            throw new IllegalArgumentException("bookingStepMinutes cannot exceed maxBookingDurationMinutes");
        }
        if (request.getArrivalDeadlineMinutesBeforeEnd() > request.getMaxBookingDurationMinutes()) {
            throw new IllegalArgumentException("arrivalDeadlineMinutesBeforeEnd cannot exceed maxBookingDurationMinutes");
        }
        if (request.getMaxBookingDurationMinutes() % request.getBookingStepMinutes() != 0) {
            throw new IllegalArgumentException("maxBookingDurationMinutes must be divisible by bookingStepMinutes");
        }
    }

    private ReservationPolicySettings createDefaultSettings() {
        return repository.save(defaultSettings());
    }

    private ReservationPolicySettings defaultSettings() {
        ReservationPolicySettings settings = new ReservationPolicySettings();
        settings.setId(ReservationPolicySettings.SINGLETON_ID);
        settings.setBookingStepMinutes(DEFAULT_BOOKING_STEP_MINUTES);
        settings.setHoldDurationMinutes(DEFAULT_HOLD_DURATION_MINUTES);
        settings.setArrivalDeadlineMinutesBeforeEnd(DEFAULT_ARRIVAL_DEADLINE_MINUTES_BEFORE_END);
        settings.setStandardCancellationRefundPercent(DEFAULT_STANDARD_CANCELLATION_REFUND_PERCENT);
        settings.setNoShowRefundPercent(DEFAULT_NO_SHOW_REFUND_PERCENT);
        settings.setMaxBookingDurationMinutes(DEFAULT_MAX_BOOKING_DURATION_MINUTES);
        settings.setMaxBookingAheadDays(DEFAULT_MAX_BOOKING_AHEAD_DAYS);
        return settings;
    }

    private ReservationPolicySettingsResponse toResponse(ReservationPolicySettings settings) {
        return ReservationPolicySettingsResponse.builder()
                .bookingStepMinutes(settings.getBookingStepMinutes())
                .holdDurationMinutes(settings.getHoldDurationMinutes())
                .arrivalDeadlineMinutesBeforeEnd(settings.getArrivalDeadlineMinutesBeforeEnd())
                .standardCancellationRefundPercent(settings.getStandardCancellationRefundPercent())
                .noShowRefundPercent(settings.getNoShowRefundPercent())
                .maxBookingDurationMinutes(settings.getMaxBookingDurationMinutes())
                .maxBookingAheadDays(settings.getMaxBookingAheadDays())
                .build();
    }
}
