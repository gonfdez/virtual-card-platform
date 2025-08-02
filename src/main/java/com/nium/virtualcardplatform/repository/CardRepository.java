package com.nium.virtualcardplatform.repository;

import com.nium.virtualcardplatform.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository // Marks this interface as a Spring Data JPA repository
public interface CardRepository extends JpaRepository<Card, UUID> {
    // Spring Data JPA automatically provides CRUD methods (save, findById, findAll, delete, etc.)
    // I can add custom query methods here if needed, following Spring Data JPA naming conventions.
    // For example:
    // List<Card> findByCardholderName(String cardholderName);
}