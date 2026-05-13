package resenkov.work.parkingreservationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import resenkov.work.parkingreservationservice.entity.ReservationPolicySettings;

public interface ReservationPolicySettingsRepository extends JpaRepository<ReservationPolicySettings, Long> {
}
