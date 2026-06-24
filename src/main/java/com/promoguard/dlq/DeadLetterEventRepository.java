package com.promoguard.dlq;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, Long> {

    List<DeadLetterEvent> findByStatusOrderByIdAsc(DeadLetterStatus status);

    Optional<DeadLetterEvent> findFirstByStatusOrderByIdAsc(DeadLetterStatus status);
}
