package resenkov.work.parkingreservationservice.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import resenkov.work.parkingreservationservice.dto.ReservationBillingEvent;

@Component
public class ReservationEventProducer {
    private static final String TOPIC = "reservation-events";

    private final KafkaTemplate<String, ReservationBillingEvent> kafkaTemplate;

    public ReservationEventProducer(KafkaTemplate<String, ReservationBillingEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishReservationEvent(ReservationBillingEvent event) {
        kafkaTemplate.send(TOPIC, String.valueOf(event.getReservationId()), event);
    }
}
