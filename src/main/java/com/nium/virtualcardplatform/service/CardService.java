package com.nium.virtualcardplatform.service;

import com.nium.virtualcardplatform.model.Card;
import com.nium.virtualcardplatform.model.Transaction;
import com.nium.virtualcardplatform.repository.CardRepository;
import com.nium.virtualcardplatform.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CardService {

    private final CardRepository cardRepository;
    private final TransactionRepository transactionRepository;

    // Dependency Injection
    @Autowired
    public CardService(CardRepository cardRepository, TransactionRepository transactionRepository) {
        this.cardRepository = cardRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Creates a new virtual card with an initial balance.
     * @param cardholderName The name of the cardholder.
     * @param initialBalance The initial balance for the card.
     * @return The created Card object.
     */
    @Transactional // Ensures this method runs within a database transaction
    public Card createCard(String cardholderName, BigDecimal initialBalance) {
        // Basic validation for initial balance
        if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative.");
        }
        Card card = new Card(cardholderName, initialBalance);
        return cardRepository.save(card); // Save the new card to the database
    }

    /**
     * Retrieves a card by its ID.
     * @param cardId The ID of the card.
     * @return An Optional containing the Card if found, or empty if not.
     */
    public Optional<Card> getCardById(UUID cardId) {
        return cardRepository.findById(cardId);
    }

    /**
     * Retrieves all cards.
     * @return A list of all Card objects.
     */
    public List<Card> getAllCards() {
        return cardRepository.findAll();
    }

    /**
     * Processes a spend transaction for a card.
     * @param cardId The ID of the card.
     * @param amount The amount to spend.
     * @return The updated Card object after the transaction.
     * @throws IllegalArgumentException If the amount is invalid or card not found.
     * @throws IllegalStateException If the card has insufficient balance.
     */
    @Transactional // Ensures the balance update and transaction recording are atomic
    public Card spend(UUID cardId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Spend amount must be a positive number greater than zero.");
        }

        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found with ID: " + cardId));

        if (card.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance for card ID: " + cardId);
        }

        // Update card balance
        card.setBalance(card.getBalance().subtract(amount));
        Card updatedCard = cardRepository.save(card); // Save updated card

        // Record the transaction
        Transaction transaction = new Transaction(cardId, Transaction.TransactionType.SPEND, amount);
        transactionRepository.save(transaction);

        return updatedCard;
    }

    /**
     * Processes a top-up transaction for a card.
     * @param cardId The ID of the card.
     * @param amount The amount to top-up.
     * @return The updated Card object after the transaction.
     * @throws IllegalArgumentException If the amount is invalid or card not found.
     */
    @Transactional // Ensures the balance update and transaction recording are atomic
    public Card topUp(UUID cardId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Top-up amount must be a positive number greater than zero.");
        }

        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found with ID: " + cardId));

        // Update card balance
        card.setBalance(card.getBalance().add(amount));
        Card updatedCard = cardRepository.save(card); // Save updated card

        // Record the transaction
        Transaction transaction = new Transaction(cardId, Transaction.TransactionType.TOPUP, amount);
        transactionRepository.save(transaction);

        return updatedCard;
    }

    /**
     * Retrieves all transactions for a specific card.
     * @param cardId The ID of the card.
     * @return A list of Transaction objects for the given card.
     */
    public List<Transaction> getCardTransactions(UUID cardId) {
        return transactionRepository.findByCardId(cardId);
    }
}