package com.abba.tanahora.domain.repository;

import com.abba.tanahora.domain.model.Reminder;
import com.abba.tanahora.domain.model.ReminderStatus;
import com.abba.tanahora.domain.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ReminderRepository extends MongoRepository<Reminder, UUID> {

    List<Reminder> findByNextDispatchLessThanEqualAndStatus(OffsetDateTime now, ReminderStatus status);

    List<Reminder> findByUserAndStatus(User user, ReminderStatus status);

    List<Reminder> findByStatus(ReminderStatus status);

    long countByStatus(ReminderStatus status);

    default List<Reminder> findPendingNextDispatch(OffsetDateTime now) {
        return findByNextDispatchLessThanEqualAndStatus(now.plusMinutes(5), ReminderStatus.ACTIVE);
    }

}
