package com.abba.tanahora.domain.repository;

import com.abba.tanahora.domain.model.Subscription;
import com.abba.tanahora.domain.model.SubscriptionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends MongoRepository<Subscription, String> {

    Optional<Subscription> findTopByWhatsappIdAndStatusInAndCheckoutExpiresAtAfterOrderByCreatedAtDesc(
            String whatsappId, Collection<SubscriptionStatus> statuses, OffsetDateTime now
    );

    Optional<Subscription> findByGatewaySubscriptionId(String gatewaySubscriptionId);

    Optional<Subscription> findTopByWhatsappIdOrderByCreatedAtDesc(String whatsappId);

    Optional<Subscription> findTopByWhatsappIdAndStatusInOrderByCreatedAtDesc(
            String whatsappId, Collection<SubscriptionStatus> statuses
    );

    List<Subscription> findByCancellationRequestedTrueAndStatusIn(Collection<SubscriptionStatus> statuses);
}
