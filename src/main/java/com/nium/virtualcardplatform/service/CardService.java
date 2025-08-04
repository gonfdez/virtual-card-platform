package com.nium.virtualcardplatform.service;

import com.nium.virtualcardplatform.model.Card;
import com.nium.virtualcardplatform.model.Transaction;
import com.nium.virtualcardplatform.repository.CardRepository;
import com.nium.virtualcardplatform.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CardService {

    private final CardRepository cardRepository;
    private final TransactionRepository transactionRepository;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long BASE_DELAY_MS = 10;

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
    @Transactional
    public Card createCard(String cardholderName, BigDecimal initialBalance) {
        if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative.");
        }
        Card card = new Card(cardholderName, initialBalance);
        return cardRepository.save(card);
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
     * Processes a spend transaction for a card with optimistic locking retry mechanism.
     * Uses the @Version field in Card entity for optimistic concurrency control.
     * @param cardId The ID of the card.
     * @param amount The amount to spend.
     * @return The updated Card object after the transaction.
     * @throws IllegalArgumentException If the amount is invalid or card not found.
     * @throws IllegalStateException If the card has insufficient balance.
     */
    public Card spend(UUID cardId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Spend amount must be a positive number greater than zero.");
        }

        RuntimeException lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return performSpendTransactionWithOptimisticLocking(cardId, amount);
            } catch (OptimisticLockingFailureException e) {
                lastException = new RuntimeException("Optimistic locking failure on attempt " + attempt, e);
                
                if (attempt == MAX_RETRY_ATTEMPTS) {
                    break; // Exit loop to throw exception below
                }
                
                // Short delay with some randomization to reduce collision probability
                try {
                    long delay = BASE_DELAY_MS + (long) (Math.random() * BASE_DELAY_MS * attempt);
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread was interrupted during retry", ie);
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                // These are business logic errors that shouldn't be retried
                throw e;
            }
        }
        
        throw new RuntimeException("Unable to complete spend transaction after " + MAX_RETRY_ATTEMPTS 
            + " attempts due to concurrent modifications. The @Version field detected concurrent updates.", lastException);
    }

    /**
     * Performs the actual spend transaction leveraging JPA's optimistic locking.
     * The @Version field in Card entity automatically handles concurrency control.
     * @param cardId The ID of the card.
     * @param amount The amount to spend.
     * @return The updated Card object after the transaction.
     * @throws IllegalArgumentException If the card is not found.
     * @throws IllegalStateException If the card has insufficient balance.
     * @throws OptimisticLockingFailureException If concurrent modification is detected.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private Card performSpendTransactionWithOptimisticLocking(UUID cardId, BigDecimal amount) {
        // Fresh read from database - JPA will load current version
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found with ID: " + cardId));

        // Business logic validation
        if (card.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance for card ID: " + cardId);
        }

        // Update card balance - the version will be automatically incremented by JPA
        card.setBalance(card.getBalance().subtract(amount));
        
        // This save() will check the version field and throw OptimisticLockingFailureException
        // if another transaction modified the entity since we loaded it
        Card updatedCard = cardRepository.save(card);

        // Record the transaction only after successful card update
        Transaction transaction = new Transaction(cardId, Transaction.TransactionType.SPEND, amount);
        transactionRepository.save(transaction);

        return updatedCard;
    }

    /**
     * Processes a top-up transaction for a card with optimistic locking retry mechanism.
     * Uses the @Version field in Card entity for optimistic concurrency control.
     * @param cardId The ID of the card.
     * @param amount The amount to top-up.
     * @return The updated Card object after the transaction.
     * @throws IllegalArgumentException If the amount is invalid or card not found.
     */
    public Card topUp(UUID cardId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Top-up amount must be a positive number greater than zero.");
        }

        RuntimeException lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return performTopUpTransactionWithOptimisticLocking(cardId, amount);
            } catch (OptimisticLockingFailureException e) {
                lastException = new RuntimeException("Optimistic locking failure on attempt " + attempt, e);
                
                if (attempt == MAX_RETRY_ATTEMPTS) {
                    break; // Exit loop to throw exception below
                }
                
                // Short delay with randomization
                try {
                    long delay = BASE_DELAY_MS + (long) (Math.random() * BASE_DELAY_MS * attempt);
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread was interrupted during retry", ie);
                }
            } catch (IllegalArgumentException e) {
                // Business logic errors that shouldn't be retried
                throw e;
            }
        }
        
        throw new RuntimeException("Unable to complete top-up transaction after " + MAX_RETRY_ATTEMPTS 
            + " attempts due to concurrent modifications. The @Version field detected concurrent updates.", lastException);
    }

    /**
     * Performs the actual top-up transaction leveraging JPA's optimistic locking.
     * The @Version field in Card entity automatically handles concurrency control.
     * @param cardId The ID of the card.
     * @param amount The amount to top-up.
     * @return The updated Card object after the transaction.
     * @throws IllegalArgumentException If the card is not found.
     * @throws OptimisticLockingFailureException If concurrent modification is detected.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private Card performTopUpTransactionWithOptimisticLocking(UUID cardId, BigDecimal amount) {
        // Fresh read from database - JPA will load current version
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found with ID: " + cardId));

        // Update card balance - the version will be automatically incremented by JPA
        card.setBalance(card.getBalance().add(amount));
        
        // This save() will check the version field and throw OptimisticLockingFailureException
        // if another transaction modified the entity since we loaded it
        Card updatedCard = cardRepository.save(card);

        // Record the transaction only after successful card update
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