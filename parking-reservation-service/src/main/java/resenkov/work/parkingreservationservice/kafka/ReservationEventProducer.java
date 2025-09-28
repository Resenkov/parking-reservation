package resenkov.work.parkingreservationservice.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import resenkov.work.parkingreservationservice.dto.ReservationCreatedEvent;

@Component
public class ReservationEventProducer {
    private final KafkaTemplate<String, ReservationCreatedEvent> kafkaTemplate;

    public ReservationEventProducer(KafkaTemplate<String, ReservationCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishReservationCreated(ReservationCreatedEvent event) {
        kafkaTemplate.send("reservation-events", String.valueOf(event.getReservationId()), event);
    }
}
