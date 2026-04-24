package com.abba.tanahora.infrastructure.backoffice;

import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.application.notification.InteractiveWhatsAppMessage;
import com.abba.tanahora.application.notification.TemplateWhatsAppMessage;
import com.abba.tanahora.application.notification.WhatsAppTemplates;
import com.abba.tanahora.domain.model.Reminder;
import com.abba.tanahora.domain.model.ReminderEvent;
import com.abba.tanahora.domain.model.ReminderStatus;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.repository.ReminderEventRepository;
import com.abba.tanahora.domain.repository.ReminderRepository;
import com.abba.tanahora.domain.repository.UserRepository;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.ReminderEventService;
import com.abba.tanahora.domain.service.ReminderService;
import com.abba.tanahora.domain.service.UserService;
import com.whatsapp.api.domain.messages.Button;
import com.whatsapp.api.domain.messages.Reply;
import com.whatsapp.api.domain.messages.type.ButtonType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/backoffice")
@Tag(name = "Backoffice", description = "Operacoes administrativas para suporte e operacao manual")
public class BackofficeController {

    private final UserService userService;
    private final ReminderService reminderService;
    private final NotificationService notificationService;
    private final ReminderEventService reminderEventService;
    private final UserRepository userRepository;
    private final ReminderRepository reminderRepository;
    private final ReminderEventRepository reminderEventRepository;

    public BackofficeController(UserService userService,
                                ReminderService reminderService,
                                NotificationService notificationService,
                                ReminderEventService reminderEventService,
                                UserRepository userRepository,
                                ReminderRepository reminderRepository,
                                ReminderEventRepository reminderEventRepository) {
        this.userService = userService;
        this.reminderService = reminderService;
        this.notificationService = notificationService;
        this.reminderEventService = reminderEventService;
        this.userRepository = userRepository;
        this.reminderRepository = reminderRepository;
        this.reminderEventRepository = reminderEventRepository;
    }

    @PostMapping("/users/{whatsappId}/messages")
    @Operation(
            summary = "Enviar mensagem manual para usuario",
            description = "Envia uma mensagem de texto para um usuario existente pelo whatsappId."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mensagem enviada"),
            @ApiResponse(responseCode = "400", description = "Payload invalido"),
            @ApiResponse(responseCode = "404", description = "Usuario nao encontrado")
    })
    public ResponseEntity<?> sendMessageToUser(@PathVariable String whatsappId,
                                               @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                                       description = "Payload com texto da mensagem",
                                                       required = true,
                                                       content = @Content(
                                                               schema = @Schema(implementation = BackofficeSendMessageRequest.class),
                                                               examples = {
                                                                       @ExampleObject(name = "Texto", value = """
                                                                               {"message":"Mensagem do backoffice"}
                                                                               """),
                                                                       @ExampleObject(name = "Template", value = """
                                                                               {"template":"RECALL_TO_ACTION","templateParameters":["Joao","Paracetamol"]}
                                                                               """)
                                                               }
                                                       )
                                               )
                                               @RequestBody BackofficeSendMessageRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "request body is required"));
        }

        User user = userService.findByWhatsappId(whatsappId);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "user not found"));
        }

        String messageId = sendBackofficeMessage(user, request);

        if (messageId == null || messageId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message or template is required"));
        }

        return ResponseEntity.ok(new BackofficeSendMessageResponse(messageId));
    }

    @PostMapping("/users/{whatsappId}/plan")
    @Operation(
            summary = "Atualizar plano do usuario",
            description = "Define o plano como PREMIUM ou FREE. Para PREMIUM, months e opcional e default 1."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plano atualizado"),
            @ApiResponse(responseCode = "400", description = "Plano invalido"),
            @ApiResponse(responseCode = "404", description = "Usuario nao encontrado")
    })
    public ResponseEntity<?> updateUserPlan(@PathVariable String whatsappId,
                                            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                                    description = "Payload de atualizacao de plano",
                                                    required = true,
                                                    content = @Content(
                                                            schema = @Schema(implementation = BackofficePlanUpdateRequest.class),
                                                            examples = {
                                                                    @ExampleObject(name = "Premium", value = """
                                                                            {"plan":"PREMIUM","months":1}
                                                                            """),
                                                                    @ExampleObject(name = "Free", value = """
                                                                            {"plan":"FREE"}
                                                                            """)
                                                            }
                                                    )
                                            )
                                            @RequestBody BackofficePlanUpdateRequest request) {
        if (request == null || request.plan() == null || request.plan().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "plan is required"));
        }

        User user = userService.findByWhatsappId(whatsappId);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "user not found"));
        }

        String plan = request.plan().trim().toUpperCase(Locale.ROOT);
        if ("PREMIUM".equals(plan)) {
            int months = request.months() == null ? 1 : Math.max(1, request.months());
            userService.applyPremiumCycle(whatsappId, months);
        } else if ("FREE".equals(plan)) {
            userService.downgradeToFree(whatsappId);
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "plan must be PREMIUM or FREE"));
        }

        User updated = userService.findByWhatsappId(whatsappId);
        return ResponseEntity.ok(new BackofficePlanUpdateResponse(
                updated.getWhatsappId(),
                updated.getPlan().name(),
                updated.getProUntil()
        ));
    }

    @GetMapping("/users/{whatsappId}/next-reminder")
    @Operation(
            summary = "Consultar proximo lembrete de um usuario",
            description = "Retorna o lembrete ativo com menor nextDispatch para o whatsappId informado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Consulta executada"),
            @ApiResponse(responseCode = "404", description = "Usuario nao encontrado")
    })
    public ResponseEntity<?> getNextReminderByUser(@PathVariable String whatsappId) {
        User user = userService.findByWhatsappId(whatsappId);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "user not found"));
        }

        Optional<Reminder> nextReminder = reminderService.getByUser(user).stream()
                .filter(reminder -> reminder.getNextDispatch() != null)
                .min(Comparator.comparing(Reminder::getNextDispatch));

        if (nextReminder.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "whatsappId", user.getWhatsappId(),
                    "hasReminder", false
            ));
        }

        Reminder reminder = nextReminder.get();
        return ResponseEntity.ok(new BackofficeNextReminderResponse(
                user.getWhatsappId(),
                true,
                reminder.getId().toString(),
                reminder.getPatientId(),
                reminder.getPatientName(),
                reminder.getMedication() != null ? reminder.getMedication().getName() : null,
                reminder.getNextDispatch()
        ));
    }

    @PostMapping("/users/{whatsappId}/reminders/last/resend")
    @Operation(
            summary = "Reenviar ultimo lembrete de um usuario",
            description = "Reenvia a mensagem do lembrete mais recente registrada em reminder_events para o whatsappId informado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lembrete reenviado"),
            @ApiResponse(responseCode = "404", description = "Usuario ou ultimo lembrete nao encontrado"),
            @ApiResponse(responseCode = "422", description = "Ultimo evento sem lembrete valido")
    })
    public ResponseEntity<?> resendLastReminderByUser(@PathVariable String whatsappId) {
        User user = userService.findByWhatsappId(whatsappId);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "user not found"));
        }

        Optional<ReminderEvent> lastReminderEvent = reminderEventRepository.findFirstByUserWhatsappIdOrderBySentAtDesc(whatsappId);
        if (lastReminderEvent.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "last reminder not found"));
        }

        ReminderEvent event = lastReminderEvent.get();
        Reminder reminder = event.getReminder();
        if (reminder == null || reminder.getMedication() == null) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", "last reminder event has no valid reminder"));
        }

        String messageId = notificationService.sendNotification(user, InteractiveWhatsAppMessage
                .builder()
                .to(user.getWhatsappId())
                .text(reminder.createSendReminderMessage())
                .button(new Button().setType(ButtonType.REPLY).setReply(new Reply().setTitle("Tomei").setId("tomei_btn")))
                .button(new Button().setType(ButtonType.REPLY).setReply(new Reply().setTitle("Adiar por uma hora").setId("adiar_btn")))
                .button(new Button().setType(ButtonType.REPLY).setReply(new Reply().setTitle("Pular").setId("pular_btn")))
                .build());

        reminderEventService.updateDispatch(event, messageId);

        return ResponseEntity.ok(new BackofficeSendMessageResponse(messageId));
    }

    @GetMapping("/stats/active")
    @Operation(
            summary = "Obter metricas ativas",
            description = "Retorna total de usuarios, usuarios com lembrete ativo e total de lembretes ativos."
    )
    @ApiResponse(responseCode = "200", description = "Metricas retornadas")
    public ResponseEntity<BackofficeActiveStatsResponse> getActiveStats() {
        long totalUsers = userRepository.count();
        long activeReminders = reminderRepository.countByStatus(ReminderStatus.ACTIVE);
        Set<String> activeUserIds = reminderRepository.findByStatus(ReminderStatus.ACTIVE).stream()
                .map(Reminder::getUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return ResponseEntity.ok(new BackofficeActiveStatsResponse(
                totalUsers,
                activeUserIds.size(),
                activeReminders
        ));
    }

    @Schema(name = "BackofficeSendMessageRequest")
    public record BackofficeSendMessageRequest(
            @Schema(description = "Texto da mensagem a ser enviada (quando nao usar template)", example = "Mensagem do backoffice")
            String message,
            @Schema(description = "Template a ser usado (quando enviar template)", implementation = WhatsAppTemplates.class)
            WhatsAppTemplates template,
            @Schema(description = "Parametros do template em ordem", example = "[\"Joao\", \"Paracetamol\"]")
            List<String> templateParameters) {
    }

    @Schema(name = "BackofficeSendMessageResponse")
    public record BackofficeSendMessageResponse(
            @Schema(description = "ID retornado pela camada de notificacao", example = "msg-1")
            String messageId) {
    }

    @Schema(name = "BackofficePlanUpdateRequest")
    public record BackofficePlanUpdateRequest(
            @Schema(description = "Plano desejado", allowableValues = {"PREMIUM", "FREE"}, example = "PREMIUM")
            String plan,
            @Schema(description = "Quantidade de meses para ciclo premium (opcional)", example = "1")
            Integer months) {
    }

    @Schema(name = "BackofficePlanUpdateResponse")
    public record BackofficePlanUpdateResponse(
            @Schema(example = "5511999999999")
            String whatsappId,
            @Schema(example = "PREMIUM")
            String plan,
            @Schema(description = "Data limite premium, nulo para FREE")
            OffsetDateTime proUntil) {
    }

    @Schema(name = "BackofficeNextReminderResponse")
    public record BackofficeNextReminderResponse(
            @Schema(example = "5511999999999")
            String whatsappId,
            @Schema(example = "true")
            boolean hasReminder,
            @Schema(nullable = true)
            String reminderId,
            @Schema(nullable = true)
            String patientId,
            @Schema(nullable = true)
            String patientName,
            @Schema(nullable = true)
            String medicationName,
            @Schema(nullable = true)
            OffsetDateTime nextDispatch) {
    }

    @Schema(name = "BackofficeActiveStatsResponse")
    public record BackofficeActiveStatsResponse(
            @Schema(example = "120")
            long totalUsers,
            @Schema(example = "46")
            long activeUsers,
            @Schema(example = "89")
            long activeReminders) {
    }

    private String sendBackofficeMessage(User user, BackofficeSendMessageRequest request) {
        if (request.template() != null) {
            TemplateWhatsAppMessage.Builder builder = TemplateWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .template(request.template());

            if (request.templateParameters() != null) {
                request.templateParameters().forEach(builder::bodyParameter);
            }

            return notificationService.sendNotification(user, builder.build());
        }

        if (request.message() == null || request.message().isBlank()) {
            return "";
        }

        return notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                .to(user.getWhatsappId())
                .message(request.message())
                .build());
    }
}
