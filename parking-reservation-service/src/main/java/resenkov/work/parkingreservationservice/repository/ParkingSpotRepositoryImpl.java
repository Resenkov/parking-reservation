package resenkov.work.parkingreservationservice.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.stereotype.Repository;
import resenkov.work.parkingreservationservice.entity.ParkingSpot;
import resenkov.work.parkingreservationservice.entity.Reservation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class ParkingSpotRepositoryImpl implements ParkingSpotRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<ParkingSpot> search(String zone, String level, Boolean occupied) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ParkingSpot> query = cb.createQuery(ParkingSpot.class);
        Root<ParkingSpot> root = query.from(ParkingSpot.class);

        List<Predicate> predicates = new ArrayList<>();
        addCaseInsensitiveEquals(cb, root, predicates, "zone", zone);
        addCaseInsensitiveEquals(cb, root, predicates, "level", level);
        if (occupied != null) {
            predicates.add(cb.equal(root.get("occupied"), occupied));
        }

        query.select(root)
                .where(predicates.toArray(new Predicate[0]))
                .orderBy(cb.asc(root.get("zone")), cb.asc(root.get("code")));

        return entityManager.createQuery(query).getResultList();
    }

    @Override
    public List<ParkingSpot> findAvailableSpots(String zone,
                                                String level,
                                                LocalDateTime fromTime,
                                                LocalDateTime toTime,
                                                List<Reservation.ReservationStatus> statuses) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ParkingSpot> query = cb.createQuery(ParkingSpot.class);
        Root<ParkingSpot> root = query.from(ParkingSpot.class);

        List<Predicate> predicates = new ArrayList<>();
        addCaseInsensitiveEquals(cb, root, predicates, "zone", zone);
        addCaseInsensitiveEquals(cb, root, predicates, "level", level);

        Subquery<Long> subquery = query.subquery(Long.class);
        Root<Reservation> reservationRoot = subquery.from(Reservation.class);
        subquery.select(cb.literal(1L))
                .where(
                        cb.equal(reservationRoot.get("spotId"), root.get("id")),
                        reservationRoot.get("status").in(statuses),
                        cb.lessThan(reservationRoot.get("startTime"), toTime),
                        cb.greaterThan(reservationRoot.get("endTime"), fromTime)
                );

        predicates.add(cb.not(cb.exists(subquery)));

        query.select(root)
                .where(predicates.toArray(new Predicate[0]))
                .orderBy(cb.asc(root.get("zone")), cb.asc(root.get("code")));

        TypedQuery<ParkingSpot> typedQuery = entityManager.createQuery(query);
        return typedQuery.getResultList();
    }

    private void addCaseInsensitiveEquals(CriteriaBuilder cb,
                                          Root<ParkingSpot> root,
                                          List<Predicate> predicates,
                                          String fieldName,
                                          String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        predicates.add(cb.equal(
                cb.lower(root.get(fieldName)),
                value.trim().toLowerCase(Locale.ROOT)
        ));
    }
}
