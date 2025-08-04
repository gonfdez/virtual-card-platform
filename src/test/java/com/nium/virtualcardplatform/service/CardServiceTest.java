package com.nium.virtualcardplatform.service;

import com.nium.virtualcardplatform.model.Card;
import com.nium.virtualcardplatform.model.Transaction;
import com.nium.virtualcardplatform.repository.CardRepository;
import com.nium.virtualcardplatform.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private CardService cardService;

    private Card testCard;
    private UUID testCardId;

    @BeforeEach
    void setUp() {
        testCardId = UUID.randomUUID();
        testCard = new Card("John Doe", BigDecimal.valueOf(100.00));
        testCard.setId(testCardId);
        testCard.setCreatedAt(LocalDateTime.now());
        testCard.setVersion(1L);
    }

    @Test
    void createCard_withValidData_shouldReturnCreatedCard() {
        // Given
        String cardholderName = "Alice Smith";
        BigDecimal initialBalance = BigDecimal.valueOf(50.00);
        Card expectedCard = new Card(cardholderName, initialBalance);
        expectedCard.setId(UUID.randomUUID());

        when(cardRepository.save(any(Card.class))).thenReturn(expectedCard);

        // When
        Card result = cardService.createCard(cardholderName, initialBalance);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCardholderName()).isEqualTo(cardholderName);
        assertThat(result.getBalance()).isEqualByComparingTo(initialBalance);
        verify(cardRepository, times(1)).save(any(Card.class));
    }

    @Test
    void createCard_withNegativeBalance_shouldThrowIllegalArgumentException() {
        // Given
        String cardholderName = "John Doe";
        BigDecimal negativeBalance = BigDecimal.valueOf(-10.00);

        // When & Then
        assertThatThrownBy(() -> cardService.createCard(cardholderName, negativeBalance))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Initial balance cannot be negative.");

        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void getCardById_withExistingCard_shouldReturnCard() {
        // Given
        when(cardRepository.findById(testCardId)).thenReturn(Optional.of(testCard));

        // When
        Optional<Card> result = cardService.getCardById(testCardId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testCard);
        verify(cardRepository, times(1)).findById(testCardId);
    }

    @Test
    void getCardById_withNonExistentCard_shouldReturnEmpty() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        when(cardRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // When
        Optional<Card> result = cardService.getCardById(nonExistentId);

        // Then
        assertThat(result).isNotPresent();
        verify(cardRepository, times(1)).findById(nonExistentId);
    }

    @Test
    void getAllCards_shouldReturnAllCards() {
        // Given
        Card card1 = new Card("Alice", BigDecimal.valueOf(100.00));
        Card card2 = new Card("Bob", BigDecimal.valueOf(200.00));
        List<Card> expectedCards = Arrays.asList(card1, card2);

        when(cardRepository.findAll()).thenReturn(expectedCards);

        // When
        List<Card> result = cardService.getAllCards();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyElementsOf(expectedCards);
        verify(cardRepository, times(1)).findAll();
    }

    @Test
    void spend_withValidAmount_shouldUpdateBalanceAndCreateTransaction() {
        // Given
        BigDecimal spendAmount = BigDecimal.valueOf(30.00);
        BigDecimal expectedBalance = BigDecimal.valueOf(70.00);
        
        Card updatedCard = new Card(testCard.getCardholderName(), expectedBalance);
        updatedCard.setId(testCardId);
        updatedCard.setVersion(2L);

        when(cardRepository.findById(testCardId)).thenReturn(Optional.of(testCard));
        when(cardRepository.save(any(Card.class))).thenReturn(updatedCard);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(new Transaction());

        // When
        Card result = cardService.spend(testCardId, spendAmount);

        // Then
        assertThat(result.getBalance()).isEqualByComparingTo(expectedBalance);
        verify(cardRepository, times(1)).findById(testCardId);
        verify(cardRepository, times(1)).save(any(Card.class));
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void spend_withInsufficientBalance_shouldThrowIllegalStateException() {
        // Given
        BigDecimal spendAmount = BigDecimal.valueOf(150.00); // More than available balance

        when(cardRepository.findById(testCardId)).thenReturn(Optional.of(testCard));

        // When & Then
        assertThatThrownBy(() -> cardService.spend(testCardId, spendAmount))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Insufficient balance for card ID: " + testCardId);

        verify(cardRepository, times(1)).findById(testCardId);
        verify(cardRepository, never()).save(any(Card.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void spend_withNegativeAmount_shouldThrowIllegalArgumentException() {
        // Given
        BigDecimal negativeAmount = BigDecimal.valueOf(-10.00);

        // When & Then
        assertThatThrownBy(() -> cardService.spend(testCardId, negativeAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Spend amount must be a positive number greater than zero.");

        verify(cardRepository, never()).findById(any(UUID.class));
    }

    @Test
    void spend_withZeroAmount_shouldThrowIllegalArgumentException() {
        // Given
        BigDecimal zeroAmount = BigDecimal.ZERO;

        // When & Then
        assertThatThrownBy(() -> cardService.spend(testCardId, zeroAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Spend amount must be a positive number greater than zero.");

        verify(cardRepository, never()).findById(any(UUID.class));
    }

    @Test
    void spend_withNonExistentCard_shouldThrowIllegalArgumentException() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        BigDecimal spendAmount = BigDecimal.valueOf(10.00);

        when(cardRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> cardService.spend(nonExistentId, spendAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Card not found with ID: " + nonExistentId);

        verify(cardRepository, times(1)).findById(nonExistentId);
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void spend_withOptimisticLockingFailure_shouldRetryAndEventuallyFail() {
        // Given
        BigDecimal spendAmount = BigDecimal.valueOf(30.00);

        when(cardRepository.findById(testCardId)).thenReturn(Optional.of(testCard));
        when(cardRepository.save(any(Card.class))).thenThrow(new OptimisticLockingFailureException("Version conflict"));

        // When & Then
        assertThatThrownBy(() -> cardService.spend(testCardId, spendAmount))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unable to complete spend transaction after 3 attempts due to concurrent modifications");

        // Verify that it tried 3 times (3 findById calls, 3 save attempts)
        verify(cardRepository, times(3)).findById(testCardId);
        verify(cardRepository, times(3)).save(any(Card.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void topUp_withValidAmount_shouldUpdateBalanceAndCreateTransaction() {
        // Given
        BigDecimal topUpAmount = BigDecimal.valueOf(50.00);
        BigDecimal expectedBalance = BigDecimal.valueOf(150.00);
        
        Card updatedCard = new Card(testCard.getCardholderName(), expectedBalance);
        updatedCard.setId(testCardId);
        updatedCard.setVersion(2L);

        when(cardRepository.findById(testCardId)).thenReturn(Optional.of(testCard));
        when(cardRepository.save(any(Card.class))).thenReturn(updatedCard);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(new Transaction());

        // When
        Card result = cardService.topUp(testCardId, topUpAmount);

        // Then
        assertThat(result.getBalance()).isEqualByComparingTo(expectedBalance);
        verify(cardRepository, times(1)).findById(testCardId);
        verify(cardRepository, times(1)).save(any(Card.class));
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void topUp_withNegativeAmount_shouldThrowIllegalArgumentException() {
        // Given
        BigDecimal negativeAmount = BigDecimal.valueOf(-10.00);

        // When & Then
        assertThatThrownBy(() -> cardService.topUp(testCardId, negativeAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Top-up amount must be a positive number greater than zero.");

        verify(cardRepository, never()).findById(any(UUID.class));
    }

    @Test
    void topUp_withZeroAmount_shouldThrowIllegalArgumentException() {
        // Given
        BigDecimal zeroAmount = BigDecimal.ZERO;

        // When & Then
        assertThatThrownBy(() -> cardService.topUp(testCardId, zeroAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Top-up amount must be a positive number greater than zero.");

        verify(cardRepository, never()).findById(any(UUID.class));
    }

    @Test
    void topUp_withNonExistentCard_shouldThrowIllegalArgumentException() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        BigDecimal topUpAmount = BigDecimal.valueOf(50.00);

        when(cardRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> cardService.topUp(nonExistentId, topUpAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Card not found with ID: " + nonExistentId);

        verify(cardRepository, times(1)).findById(nonExistentId);
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void topUp_withOptimisticLockingFailure_shouldRetryAndEventuallyFail() {
        // Given
        BigDecimal topUpAmount = BigDecimal.valueOf(50.00);

        when(cardRepository.findById(testCardId)).thenReturn(Optional.of(testCard));
        when(cardRepository.save(any(Card.class))).thenThrow(new OptimisticLockingFailureException("Version conflict"));

        // When & Then
        assertThatThrownBy(() -> cardService.topUp(testCardId, topUpAmount))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unable to complete top-up transaction after 3 attempts due to concurrent modifications");

        // Verify that it tried 3 times
        verify(cardRepository, times(3)).findById(testCardId);
        verify(cardRepository, times(3)).save(any(Card.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void getCardTransactions_shouldReturnTransactionsList() {
        // Given
        Transaction transaction1 = new Transaction(testCardId, Transaction.TransactionType.SPEND, BigDecimal.valueOf(10.00));
        Transaction transaction2 = new Transaction(testCardId, Transaction.TransactionType.TOPUP, BigDecimal.valueOf(20.00));
        List<Transaction> expectedTransactions = Arrays.asList(transaction1, transaction2);

        when(transactionRepository.findByCardId(testCardId)).thenReturn(expectedTransactions);

        // When
        List<Transaction> result = cardService.getCardTransactions(testCardId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyElementsOf(expectedTransactions);
        verify(transactionRepository, times(1)).findByCardId(testCardId);
    }

    @Test
    void getCardTransactions_withNoTransactions_shouldReturnEmptyList() {
        // Given
        when(transactionRepository.findByCardId(testCardId)).thenReturn(Arrays.asList());

        // When
        List<Transaction> result = cardService.getCardTransactions(testCardId);

        // Then
        assertThat(result).isEmpty();
        verify(transactionRepository, times(1)).findByCardId(testCardId);
    }
}