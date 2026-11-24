package com.jornal.requests.dto;

import com.jornal.requests.RequestLine;
import com.jornal.requests.RequestStatus;
import com.jornal.requests.ServiceRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * {@code scheduledAt} es el instante UTC; {@code scheduledLocal} es la hora en la zona del lugar del trabajo.
 * El frontend la muestra en la zona de quien la ve.
 */
public record RequestResponse(UUID id, RequestStatus status, UUID clientId, UUID workerId,
                              UUID categoryId, String categorySlug, String categoryName, String unit, int units,
                              List<RequestLine> lines, long subtotalMinor, long travelFeeMinor, long taxMinor,
                              boolean taxIncluded, long tipMinor, long totalMinor, String currency, int moneyScale,
                              String paymentMethod, int paymentAttempt, boolean paymentCaptured, String receiptNumber,
                              Instant scheduledAt, LocalDateTime scheduledLocal, String timezone,
                              String addressText, double latitude, double longitude, String notes,
                              String cancelReason, Instant createdAt, Instant updatedAt) {

    public static RequestResponse of(ServiceRequest r) {
        return new RequestResponse(r.getId(), r.getStatus(), r.getClientId(), r.getWorkerId(),
                r.getCategoryId(), r.getCategorySlug(), r.getCategoryName(), r.getUnit(), r.getUnits(),
                List.copyOf(r.getLines()), r.getSubtotalMinor(), r.getTravelFeeMinor(), r.getTaxMinor(),
                r.isTaxIncluded(), r.getTipMinor(), r.getTotalMinor(), r.getCurrency(), r.getMoneyScale(),
                r.getPaymentMethod(), r.getPaymentAttempt(), r.isPaymentCaptured(), r.getReceiptNumber(),
                r.getScheduledAt(), LocalDateTime.ofInstant(r.getScheduledAt(), ZoneId.of(r.getTimezone())),
                r.getTimezone(), r.getAddressText(), r.getLatitude(), r.getLongitude(), r.getNotes(),
                r.getCancelReason(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
