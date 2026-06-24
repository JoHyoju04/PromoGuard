package com.promoguard.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = """
            select *
              from outbox_events
             where status = 'READY'
               and next_retry_at <= :now
             order by id
             limit :limit
             for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> findReadyForUpdate(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying
    @Query(value = """
            update outbox_events
               set status = 'READY',
                   next_retry_at = :now,
                   updated_at = :now
             where status = 'PROCESSING'
               and updated_at < :deadline
            """, nativeQuery = true)
    int releaseStaleProcessingEvents(@Param("now") Instant now, @Param("deadline") Instant deadline);
}
