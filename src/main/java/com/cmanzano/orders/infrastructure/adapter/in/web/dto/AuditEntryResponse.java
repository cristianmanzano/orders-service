package com.cmanzano.orders.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(UUID eventId, String eventType, String detail, Instant occurredAt) {
}
