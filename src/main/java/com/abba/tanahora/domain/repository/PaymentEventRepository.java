package com.abba.tanahora.domain.repository;

import com.abba.tanahora.domain.model.PaymentEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentEventRepository extends MongoRepository<PaymentEvent, String> {

    boolean existsByEventKey(String eventKey);
}
