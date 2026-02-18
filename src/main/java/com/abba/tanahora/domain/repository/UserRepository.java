package com.abba.tanahora.domain.repository;

import com.abba.tanahora.domain.model.Plan;
import com.abba.tanahora.domain.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByWhatsappId(String whatsappId);

    List<User> findByPlanAndProUntilBefore(Plan plan, OffsetDateTime dateTime);

}
