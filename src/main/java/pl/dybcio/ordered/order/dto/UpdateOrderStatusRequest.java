package pl.dybcio.ordered.order.dto;

import jakarta.validation.constraints.NotNull;
import pl.dybcio.ordered.order.entity.OrderStatus;

public record UpdateOrderStatusRequest(
    @NotNull(message = "Status is required") OrderStatus status) {}
