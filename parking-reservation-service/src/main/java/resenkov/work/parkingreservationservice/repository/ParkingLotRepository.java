package resenkov.work.parkingreservationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import resenkov.work.parkingreservationservice.entity.ParkingLot;

import java.util.List;

public interface ParkingLotRepository extends JpaRepository<ParkingLot, Long> {
    List<ParkingLot> findAllByOrderByCodeAsc();

    boolean existsByCodeIgnoreCase(String code);
}
