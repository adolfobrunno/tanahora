package com.abba.tanahora.application.service;

import com.abba.tanahora.application.dto.DowngradeResult;
import com.abba.tanahora.application.dto.PlanInfoResult;
import com.abba.tanahora.application.dto.UpgradeCheckoutResult;
import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.domain.model.*;
import com.abba.tanahora.domain.repository.PaymentEventRepository;
import com.abba.tanahora.domain.repository.SubscriptionRepository;
import com.abba.tanahora.domain.repository.UserRepository;
import com.abba.tanahora.domain.service.BillingGateway;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.SubscriptionService;
import com.abba.tanahora.domain.service.UserService;
import com.abba.tanahora.infrastructure.config.BillingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final List<SubscriptionStatus> REUSABLE_PENDING_STATUSES = List.of(SubscriptionStatus.PENDING);
    private static final List<SubscriptionStatus> CANCELLABLE_STATUSES = List.of(
            SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE
    );
    private static final List<SubscriptionStatus> TERMINAL_STATUSES = List.of(
            SubscriptionStatus.CANCELED, SubscriptionStatus.EXPIRED, SubscriptionStatus.FAILED
    );

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final BillingGateway billingGateway;
    private final BillingProperties billingProperties;
    private final NotificationService notificationService;

    public SubscriptionServiceImpl(SubscriptionRepository subscriptionRepository,
                                   PaymentEventRepository paymentEventRepository,
                                   UserRepository userRepository,
                                   UserService userService,
                                   BillingGateway billingGateway,
                                   BillingProperties billingProperties,
                                   NotificationService notificationService) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.billingGateway = billingGateway;
        this.billingProperties = billingProperties;
        this.notificationService = notificationService;
    }

    @Override
    public UpgradeCheckoutResult createOrReuseUpgradeLink(String whatsappId, String contactName) {
        User user = userService.findByWhatsappId(whatsappId);
        if (user == null) {
            user = userService.register(whatsappId, contactName);
        }

        if (user.isPremium()) {
            return new UpgradeCheckoutResult(null, false, true, user.getProUntil());
        }

        Optional<Subscription> reusable = subscriptionRepository
                .findTopByWhatsappIdAndStatusInAndCheckoutExpiresAtAfterOrderByCreatedAtDesc(
                        whatsappId, REUSABLE_PENDING_STATUSES, OffsetDateTime.now()
                );

        if (reusable.isPresent() && hasCheckoutLink(reusable.get())) {
            Subscription existing = reusable.get();
            return new UpgradeCheckoutResult(existing.getCheckoutUrl(), true, false, existing.getCheckoutExpiresAt());
        }

        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(Math.max(1, billingProperties.getCheckoutExpiresHours()));
        BillingGateway.SubscriptionData created = billingGateway.createRecurringSubscription(
                user,
                billingProperties.getPremiumAmount(),
                billingProperties.getPremiumCurrency(),
                billingProperties.getPremiumIntervalMonths(),
                expiresAt
        );

        Subscription subscription = new Subscription();
        subscription.setUserId(user.getId());
        subscription.setWhatsappId(user.getWhatsappId());
        subscription.setAmount(billingProperties.getPremiumAmount());
        subscription.setCurrency(billingProperties.getPremiumCurrency());
        subscription.setIntervalMonths(billingProperties.getPremiumIntervalMonths());
        subscription.setStatus(mapSubscriptionStatus(created.status()));
        subscription.setGatewaySubscriptionId(created.id());
        subscription.setCheckoutUrl(created.checkoutUrl());
        subscription.setCheckoutExpiresAt(expiresAt);
        subscription.setNextBillingAt(created.nextPaymentDate());
        subscription.setUpdatedAt(OffsetDateTime.now());
        subscriptionRepository.save(subscription);

        return new UpgradeCheckoutResult(subscription.getCheckoutUrl(), false, false, subscription.getCheckoutExpiresAt());
    }

    @Override
    public DowngradeResult requestDowngrade(String whatsappId, String contactName) {
        User user = userService.findByWhatsappId(whatsappId);
        if (user == null) {
            user = userService.register(whatsappId, contactName);
        }

        if (!user.isPremium()) {
            return new DowngradeResult(true, false, false, user.getProUntil());
        }

        Subscription subscription = subscriptionRepository.findTopByWhatsappIdAndStatusInOrderByCreatedAtDesc(
                whatsappId, CANCELLABLE_STATUSES
        ).orElse(null);

        if (subscription == null) {
            return new DowngradeResult(false, false, false, user.getProUntil());
        }

        if (!subscription.isCancellationRequested()) {
            subscription.setCancellationRequested(true);
            subscription.setCancellationRequestedAt(OffsetDateTime.now());
        }

        boolean confirmed = cancelAtGateway(subscription);
        subscription.setUpdatedAt(OffsetDateTime.now());
        subscriptionRepository.save(subscription);

        return new DowngradeResult(false, true, confirmed, user.getProUntil());
    }

    @Override
    public PlanInfoResult getPlanInfo(String whatsappId, String contactName) {
        User user = userService.findByWhatsappId(whatsappId);
        if (user == null) {
            user = userService.register(whatsappId, contactName);
        }
        Subscription latest = subscriptionRepository.findTopByWhatsappIdOrderByCreatedAtDesc(whatsappId).orElse(null);
        String subscriptionStatus = resolveSubscriptionStatus(latest);
        return new PlanInfoResult(user.getPlan(), user.getProUntil(), subscriptionStatus);
    }

    @Override
    public void handleMercadoPagoWebhook(String eventKey, String eventType, String resourceId, String payload) {
        if (eventKey == null || eventKey.isBlank()) {
            log.debug("Ignoring webhook with missing eventKey");
            return;
        }
        if (paymentEventRepository.existsByEventKey(eventKey)) {
            log.debug("Ignoring duplicate webhook eventKey={}", eventKey);
            return;
        }

        PaymentEvent event = new PaymentEvent();
        event.setEventKey(eventKey);
        event.setGateway("MERCADO_PAGO");
        event.setEventType(eventType);
        event.setResourceId(resourceId);
        event.setPayload(payload);
        event.setProcessed(false);
        paymentEventRepository.save(event);

        try {
            processEvent(eventType, resourceId);
            event.setProcessed(true);
            event.setProcessedAt(OffsetDateTime.now());
            paymentEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to process Mercado Pago webhook eventKey={} type={} resourceId={} reason={}",
                    eventKey, eventType, resourceId, e.getMessage(), e);
        }
    }

    @Override
    public void runDailyReconciliation() {
        reconcilePendingDowngrades();
        reconcileNonRenewedPremiumUsers();
    }

    private void processEvent(String eventType, String resourceId) {
        if (resourceId == null || resourceId.isBlank() || eventType == null) {
            return;
        }
        String normalized = eventType.toLowerCase();
        if (normalized.contains("payment")) {
            syncPayment(resourceId);
            return;
        }
        if (normalized.contains("preapproval") || normalized.contains("subscription")) {
            syncSubscription(resourceId);
        }
    }

    private void syncSubscription(String gatewaySubscriptionId) {
        BillingGateway.SubscriptionData data = billingGateway.getSubscription(gatewaySubscriptionId);
        Subscription subscription = subscriptionRepository.findByGatewaySubscriptionId(gatewaySubscriptionId)
                .orElseGet(() -> createFromGatewaySubscription(data));

        SubscriptionStatus previousStatus = subscription.getStatus();
        subscription.setStatus(mapSubscriptionStatus(data.status()));
        subscription.setGatewaySubscriptionId(data.id());
        if (data.checkoutUrl() != null && !data.checkoutUrl().isBlank()) {
            subscription.setCheckoutUrl(data.checkoutUrl());
        }
        if (data.nextPaymentDate() != null) {
            subscription.setNextBillingAt(data.nextPaymentDate());
        }
        subscription.setUpdatedAt(OffsetDateTime.now());
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE && subscription.getActivatedAt() == null) {
            subscription.setActivatedAt(OffsetDateTime.now());
        }
        if (subscription.isCancellationRequested() && subscription.getStatus() == SubscriptionStatus.CANCELED
                && subscription.getCancellationConfirmedAt() == null) {
            subscription.setCancellationConfirmedAt(OffsetDateTime.now());
        }
        subscriptionRepository.save(subscription);

        if (previousStatus != subscription.getStatus() && subscription.getStatus() == SubscriptionStatus.CANCELED) {
            notifyDowngradeConfirmed(subscription.getWhatsappId());
        }
    }

    private void syncPayment(String paymentId) {
        BillingGateway.PaymentData payment = billingGateway.getPayment(paymentId);
        if (!"approved".equalsIgnoreCase(payment.status())) {
            return;
        }

        Subscription subscription = findSubscriptionForPayment(payment).orElse(null);
        if (subscription == null || subscription.getWhatsappId() == null || subscription.getWhatsappId().isBlank()) {
            return;
        }
        if (payment.id() != null && payment.id().equals(subscription.getLastPaymentId())) {
            return;
        }

        subscription.setLastPaymentId(payment.id());
        subscription.setLastChargeApprovedAt(payment.approvedAt() != null ? payment.approvedAt() : OffsetDateTime.now());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        if (subscription.getActivatedAt() == null) {
            subscription.setActivatedAt(OffsetDateTime.now());
        }
        OffsetDateTime base = subscription.getLastChargeApprovedAt();
        int interval = subscription.getIntervalMonths() == null ? billingProperties.getPremiumIntervalMonths() : subscription.getIntervalMonths();
        subscription.setNextBillingAt(base.plusMonths(Math.max(1, interval)));
        subscription.setUpdatedAt(OffsetDateTime.now());
        subscriptionRepository.save(subscription);

        userService.applyPremiumCycle(subscription.getWhatsappId(), interval);
        notifyPaymentApproved(subscription.getWhatsappId());
    }

    private void reconcilePendingDowngrades() {
        List<Subscription> pendingDowngrades = subscriptionRepository.findByCancellationRequestedTrueAndStatusIn(CANCELLABLE_STATUSES);
        for (Subscription subscription : pendingDowngrades) {
            SubscriptionStatus previous = subscription.getStatus();
            boolean confirmed = cancelAtGateway(subscription);
            subscription.setUpdatedAt(OffsetDateTime.now());
            subscriptionRepository.save(subscription);
            if (confirmed && previous != SubscriptionStatus.CANCELED) {
                notifyDowngradeConfirmed(subscription.getWhatsappId());
            }
        }
    }

    private void reconcileNonRenewedPremiumUsers() {
        int toleranceDays = Math.max(1, billingProperties.getNonRenewalToleranceDays());
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(toleranceDays);
        List<User> expiredPremiumUsers = userRepository.findByPlanAndProUntilBefore(Plan.PREMIUM, cutoff);

        for (User user : expiredPremiumUsers) {
            Optional<Subscription> latestOpt = subscriptionRepository.findTopByWhatsappIdOrderByCreatedAtDesc(user.getWhatsappId());
            Subscription latest = latestOpt.orElse(null);

            if (latest != null && latest.getStatus() == SubscriptionStatus.ACTIVE && latest.getGatewaySubscriptionId() != null) {
                try {
                    syncSubscription(latest.getGatewaySubscriptionId());
                    latest = subscriptionRepository.findTopByWhatsappIdOrderByCreatedAtDesc(user.getWhatsappId()).orElse(latest);
                } catch (Exception e) {
                    log.warn("Unable to sync subscription before non-renewal validation whatsappId={} reason={}",
                            user.getWhatsappId(), e.getMessage());
                }
            }

            if (!shouldDowngradeByNonRenewal(latest, toleranceDays)) {
                continue;
            }

            userService.downgradeToFree(user.getWhatsappId());
            if (latest != null && latest.getStatus() == SubscriptionStatus.ACTIVE) {
                latest.setStatus(SubscriptionStatus.EXPIRED);
                latest.setUpdatedAt(OffsetDateTime.now());
                subscriptionRepository.save(latest);
            }
            notifyDowngradedAfterNonRenewal(user.getWhatsappId());
        }
    }

    private boolean shouldDowngradeByNonRenewal(Subscription latest, int toleranceDays) {
        if (latest == null) {
            return true;
        }
        if (TERMINAL_STATUSES.contains(latest.getStatus())) {
            return true;
        }
        if (latest.getStatus() == SubscriptionStatus.ACTIVE) {
            return false;
        }
        if (latest.getStatus() == SubscriptionStatus.PENDING) {
            OffsetDateTime nextPayment = latest.getNextBillingAt();
            if (nextPayment == null) {
                return true;
            }
            return nextPayment.plusDays(toleranceDays).isBefore(OffsetDateTime.now());
        }
        if (latest.getStatus() == SubscriptionStatus.PAST_DUE) {
            return true;
        }
        return true;
    }

    private boolean cancelAtGateway(Subscription subscription) {
        String gatewaySubscriptionId = subscription.getGatewaySubscriptionId();
        if (gatewaySubscriptionId == null || gatewaySubscriptionId.isBlank()) {
            subscription.setStatus(SubscriptionStatus.CANCELED);
            if (subscription.getCancellationConfirmedAt() == null) {
                subscription.setCancellationConfirmedAt(OffsetDateTime.now());
            }
            return true;
        }
        try {
            BillingGateway.SubscriptionData cancelled = billingGateway.cancelSubscription(gatewaySubscriptionId);
            subscription.setStatus(mapSubscriptionStatus(cancelled.status()));
            if (subscription.getStatus() == SubscriptionStatus.CANCELED || subscription.getStatus() == SubscriptionStatus.EXPIRED) {
                if (subscription.getCancellationConfirmedAt() == null) {
                    subscription.setCancellationConfirmedAt(OffsetDateTime.now());
                }
                return true;
            }
        } catch (Exception e) {
            log.warn("Failed to cancel recurring subscription gatewayId={} reason={}", gatewaySubscriptionId, e.getMessage());
        }
        return false;
    }

    private Optional<Subscription> findSubscriptionForPayment(BillingGateway.PaymentData payment) {
        if (payment.subscriptionId() != null && !payment.subscriptionId().isBlank()) {
            Optional<Subscription> bySubscriptionId = subscriptionRepository.findByGatewaySubscriptionId(payment.subscriptionId());
            if (bySubscriptionId.isPresent()) {
                return bySubscriptionId;
            }
        }
        if (payment.externalReference() != null && !payment.externalReference().isBlank()) {
            return subscriptionRepository.findTopByWhatsappIdOrderByCreatedAtDesc(payment.externalReference());
        }
        return Optional.empty();
    }

    private Subscription createFromGatewaySubscription(BillingGateway.SubscriptionData data) {
        Subscription subscription = new Subscription();
        String whatsappId = data.externalReference();
        subscription.setWhatsappId(whatsappId);
        User user = userService.findByWhatsappId(whatsappId);
        if (user != null) {
            subscription.setUserId(user.getId());
        }
        subscription.setAmount(billingProperties.getPremiumAmount());
        subscription.setCurrency(billingProperties.getPremiumCurrency());
        subscription.setIntervalMonths(billingProperties.getPremiumIntervalMonths());
        subscription.setGatewaySubscriptionId(data.id());
        subscription.setCheckoutUrl(data.checkoutUrl());
        subscription.setStatus(mapSubscriptionStatus(data.status()));
        subscription.setNextBillingAt(data.nextPaymentDate());
        subscription.setUpdatedAt(OffsetDateTime.now());
        return subscription;
    }

    private SubscriptionStatus mapSubscriptionStatus(String status) {
        if (status == null) {
            return SubscriptionStatus.PENDING;
        }
        return switch (status.toLowerCase()) {
            case "authorized", "active" -> SubscriptionStatus.ACTIVE;
            case "paused", "pending" -> SubscriptionStatus.PENDING;
            case "cancelled", "canceled" -> SubscriptionStatus.CANCELED;
            case "expired" -> SubscriptionStatus.EXPIRED;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            default -> SubscriptionStatus.FAILED;
        };
    }

    private boolean hasCheckoutLink(Subscription subscription) {
        return subscription.getCheckoutUrl() != null && !subscription.getCheckoutUrl().isBlank();
    }

    private String resolveSubscriptionStatus(Subscription latest) {
        if (latest == null) {
            return "SEM_ASSINATURA";
        }
        if (latest.isCancellationRequested() || latest.getStatus() == SubscriptionStatus.CANCELED
                || latest.getStatus() == SubscriptionStatus.EXPIRED || latest.getStatus() == SubscriptionStatus.FAILED) {
            return "CANCELADA";
        }
        if (latest.getStatus() == SubscriptionStatus.ACTIVE
                || latest.getStatus() == SubscriptionStatus.PENDING
                || latest.getStatus() == SubscriptionStatus.PAST_DUE) {
            return "ATIVA";
        }
        return "CANCELADA";
    }

    private void notifyPaymentApproved(String whatsappId) {
        User user = userService.findByWhatsappId(whatsappId);
        if (user == null) {
            return;
        }
        notificationService.sendNotification(user,
                BasicWhatsAppMessage.builder()
                        .to(user.getWhatsappId())
                        .message("Deu tudo certo com seu pagamento 🤩. Aproveite todas as vantagens do plano Premium!")
                        .build());
    }

    private void notifyDowngradeConfirmed(String whatsappId) {
        User user = userService.findByWhatsappId(whatsappId);
        if (user == null) {
            return;
        }
        notificationService.sendNotification(user,
                BasicWhatsAppMessage.builder()
                        .to(user.getWhatsappId())
                        .message("""
                                Que pena 😢.
                                
                                Seu plano Premium foi cancelado, mas fique tranquilo que você ainda tem acesso a todas as funcionalidades
                                 até o final do período já pago.
                                
                                """)
                        .build());
    }

    private void notifyDowngradedAfterNonRenewal(String whatsappId) {
        User user = userService.findByWhatsappId(whatsappId);
        if (user == null) {
            return;
        }
        notificationService.sendNotification(user,
                BasicWhatsAppMessage.builder()
                        .to(user.getWhatsappId())
                        .message("""
                                Ops, parece que seu plano Premium expirou e não foi renovado automaticamente 😢
                                Se precisar, é só pedir que gero um novo link para reativar seu plano.
                                """)
                        .build());
    }
}
