package com.nium.virtualcardplatform.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id // Primary key
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private UUID cardId; // Associated card ID (FK)

    @Enumerated(EnumType.STRING) // Stores the ENUM as String in the DB 
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false) // It won't be updated after creation
    private LocalDateTime createdAt;

    // No-argument constructor required by JPA
    public Transaction() {
        this.id = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
    }

    // Constructor
    public Transaction(UUID cardId, TransactionType type, BigDecimal amount) {
        this(); // Initialize id and createdAt
        this.cardId = cardId;
        this.type = type;
        this.amount = amount;
    }

    // Enum for transaction types 
    public enum TransactionType {
        SPEND,
        TOPUP
    }

    // --- Getters and Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCardId() {
        return cardId;
    }

    public void setCardId(UUID cardId) {
        this.cardId = cardId;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Transaction{" +
               "id=" + id +
               ", cardId=" + cardId +
               ", type=" + type +
               ", amount=" + amount +
               ", createdAt=" + createdAt +
               '}';
    }
}