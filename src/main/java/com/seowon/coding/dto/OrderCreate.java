package com.seowon.coding.dto;

import com.seowon.coding.service.OrderProduct;
import java.util.List;

public record OrderCreate(
        String customerName,
        String customerEmail,
        List<OrderProduct> products
) {
}
