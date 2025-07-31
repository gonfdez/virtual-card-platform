package com.nium.virtualcardplatform.repository;

import com.nium.virtualcardplatform.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository // Marks this interface as a Spring Data JPA repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    // Custom method to find transactions by cardId (SELECT * FROM transactions WHERE card_id = ?)
    List<Transaction> findByCardId(UUID cardId);
}