package resenkov.work.parkingreservationservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import resenkov.work.parkingreservationservice.entity.ParkingSpot;

import java.util.Optional;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, Long> {
    Optional<ParkingSpot> findByCode(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ParkingSpot p where p.id = :id")
    ParkingSpot lockByIdForUpdate(@Param("id") Long id);
}