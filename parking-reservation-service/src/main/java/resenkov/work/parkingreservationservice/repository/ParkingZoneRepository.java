package resenkov.work.parkingreservationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import resenkov.work.parkingreservationservice.entity.ParkingZone;

import java.util.Collection;
import java.util.List;

public interface ParkingZoneRepository extends JpaRepository<ParkingZone, Long> {
    List<ParkingZone> findAllByOrderByCodeAsc();

    List<ParkingZone> findByLotIdOrderByCodeAsc(Long lotId);

    List<ParkingZone> findByIdIn(Collection<Long> ids);

    boolean existsByLotId(Long lotId);

    boolean existsByLotIdAndCodeIgnoreCase(Long lotId, String code);
}
