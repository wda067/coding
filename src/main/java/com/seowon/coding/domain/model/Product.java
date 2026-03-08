package com.seowon.coding.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.RoundingMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Product name is required")
    private String name;
    
    private String description;
    
    @Positive(message = "Price must be positive")
    private BigDecimal price;
    
    private int stockQuantity;
    
    private String category;
    
    // Business logic
    public boolean isInStock() {
        return stockQuantity > 0;
    }
    
    public void decreaseStock(int quantity) {
        if (quantity > stockQuantity) {
            throw new IllegalArgumentException("Not enough stock available");
        }
        stockQuantity -= quantity;
    }
    
    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        stockQuantity += quantity;
    }

    private static final String TAX = "1.1";
    private static final String HUNDRED = "100";

    public void applyPriceChange(double percentage, boolean includeTax) {
        BigDecimal base = getPrice() == null ? BigDecimal.ZERO : getPrice();
        BigDecimal parsedPercentage = BigDecimal.valueOf(percentage);

        BigDecimal base2 = getPrice() == null ? BigDecimal.ZERO : getPrice();
        base2 = base2.multiply(
                parsedPercentage.divide(new BigDecimal(HUNDRED), 10, RoundingMode.HALF_UP));
        base = base.add(base2);

        if (includeTax) {
            base = base.multiply(new BigDecimal(TAX));
        }

        BigDecimal newPrice = base.setScale(2, RoundingMode.HALF_UP);
        setPrice(newPrice);
    }
}
