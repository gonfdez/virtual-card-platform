package com.nium.virtualcardplatform;

import com.nium.virtualcardplatform.model.Card;
import com.nium.virtualcardplatform.model.Transaction;
import com.nium.virtualcardplatform.repository.CardRepository;
import com.nium.virtualcardplatform.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CardIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        // We clean the database before each test to ensure a clean state
        transactionRepository.deleteAll();
        cardRepository.deleteAll();
    }

    @Test
    void testCreateCard_and_GetCardById_shouldReturnCreatedCard() {
        // Given: Request to create a new card
        String cardholderName = "John Doe";
        BigDecimal initialBalance = BigDecimal.valueOf(100.00);
        Map<String, Object> requestBody = Map.of(
                "cardholderName", cardholderName,
                "initialBalance", initialBalance
        );

        // When: We make the POST request to the creation endpoint
        ResponseEntity<Card> response = restTemplate.postForEntity("/cards", requestBody, Card.class);

        // Then: Verify that the card was created correctly
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Card createdCard = response.getBody();
        assertThat(createdCard).isNotNull();
        assertThat(createdCard.getId()).isNotNull();
        assertThat(createdCard.getCardholderName()).isEqualTo(cardholderName);
        assertThat(createdCard.getBalance()).isEqualByComparingTo(initialBalance);

        // When: We make the GET request to get the card by ID
        ResponseEntity<Card> getResponse = restTemplate.getForEntity("/cards/" + createdCard.getId(), Card.class);

        // Then: Verify that the correct card is returned
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Card retrievedCard = getResponse.getBody();
        assertThat(retrievedCard).isNotNull();
        assertThat(retrievedCard.getId()).isEqualTo(createdCard.getId());
        assertThat(retrievedCard.getBalance()).isEqualByComparingTo(initialBalance);
    }
    
    @Test
    void testCreateCard_withInvalidInitialBalance_shouldReturnBadRequest() {
        // Given: a request with a negative initial balance
        String cardholderName = "John Doe";
        BigDecimal initialBalance = BigDecimal.valueOf(-10.00);
        Map<String, Object> requestBody = Map.of(
            "cardholderName", cardholderName,
            "initialBalance", initialBalance
        );

        // When: we make the POST request to the creation endpoint
        ResponseEntity<String> response = restTemplate.postForEntity("/cards", requestBody, String.class);

        // Then: we should get a Bad Request status and no card should be created
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(cardRepository.count()).isEqualTo(0);
    }

    @Test
    void testSpendAndTopUpTransactions_shouldUpdateBalanceAndRecordTransactions() {
        // Given: Create a card with an initial balance of 100.00
        Card initialCard = new Card("Jane Doe", BigDecimal.valueOf(100.00));
        Card savedCard = cardRepository.save(initialCard);
        UUID cardId = savedCard.getId();

        // When: Spend 25.00
        Map<String, Object> spendRequestBody = Map.of("amount", BigDecimal.valueOf(25.00));
        ResponseEntity<Card> spendResponse = restTemplate.exchange(
                "/cards/" + cardId + "/spend",
                HttpMethod.POST,
                new HttpEntity<>(spendRequestBody),
                Card.class
        );
        assertThat(spendResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Then: Verify balance
        Card cardAfterSpend = spendResponse.getBody();
        assertThat(cardAfterSpend.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(75.00));

        // When: Top up with 50.00
        Map<String, Object> topUpRequestBody = Map.of("amount", BigDecimal.valueOf(50.00));
        ResponseEntity<Card> topUpResponse = restTemplate.exchange(
                "/cards/" + cardId + "/topup",
                HttpMethod.POST,
                new HttpEntity<>(topUpRequestBody),
                Card.class
        );
        assertThat(topUpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Then: Verify balance
        Card cardAfterTopUp = topUpResponse.getBody();
        assertThat(cardAfterTopUp.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(125.00));

        // And: Verify transaction history
        ResponseEntity<List<Transaction>> transactionsResponse = restTemplate.exchange(
                "/cards/" + cardId + "/transactions",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Transaction>>() {}
        );
        
        assertThat(transactionsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Transaction> transactions = transactionsResponse.getBody();
        assertThat(transactions).hasSize(2);

        // Verify first transaction (spend)
        assertThat(transactions.get(0).getType()).isEqualTo(Transaction.TransactionType.SPEND);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(25.00));

        // Verify second transaction (top-up)
        assertThat(transactions.get(1).getType()).isEqualTo(Transaction.TransactionType.TOPUP);
        assertThat(transactions.get(1).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
    }

    @Test
    void testSpend_withInsufficientBalance_shouldReturnBadRequest() {
        // Given: A card with a balance of 50.00
        Card initialCard = new Card("Test User", BigDecimal.valueOf(50.00));
        Card savedCard = cardRepository.save(initialCard);
        UUID cardId = savedCard.getId();

        // When: Attempt to spend 75.00
        Map<String, Object> spendRequestBody = Map.of("amount", BigDecimal.valueOf(75.00));
        ResponseEntity<String> response = restTemplate.exchange(
                "/cards/" + cardId + "/spend",
                HttpMethod.POST,
                new HttpEntity<>(spendRequestBody),
                String.class
        );

        // Then: The response should be a Bad Request (400) and the balance should not change
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Card card = cardRepository.findById(cardId).orElseThrow();
        assertThat(card.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
        assertThat(transactionRepository.findByCardId(cardId)).isEmpty();
    }
    
    @Test
    void testSpend_withInvalidAmount_shouldReturnBadRequest() {
        // Given: A card with a positive balance
        Card initialCard = new Card("Test User", BigDecimal.valueOf(100.00));
        Card savedCard = cardRepository.save(initialCard);
        UUID cardId = savedCard.getId();

        // When: Attempt to spend a negative amount
        Map<String, Object> spendRequestBody = Map.of("amount", BigDecimal.valueOf(-10.00));
        ResponseEntity<String> negativeAmountResponse = restTemplate.exchange(
                "/cards/" + cardId + "/spend",
                HttpMethod.POST,
                new HttpEntity<>(spendRequestBody),
                String.class
        );

        // Then: The response should be a Bad Request (400)
        assertThat(negativeAmountResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // When: Attempt to spend an amount of zero
        Map<String, Object> zeroAmountRequestBody = Map.of("amount", BigDecimal.valueOf(0.00));
        ResponseEntity<String> zeroAmountResponse = restTemplate.exchange(
                "/cards/" + cardId + "/spend",
                HttpMethod.POST,
                new HttpEntity<>(zeroAmountRequestBody),
                String.class
        );

        // Then: The response should also be a Bad Request (400)
        assertThat(zeroAmountResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // And: The balance should not have changed and no transactions should be recorded
        Card card = cardRepository.findById(cardId).orElseThrow();
        assertThat(card.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(transactionRepository.findByCardId(cardId)).isEmpty();
    }
    
    @Test
    void testTopUp_withInvalidAmount_shouldReturnBadRequest() {
        // Given: A card with a positive balance
        Card initialCard = new Card("Test User", BigDecimal.valueOf(100.00));
        Card savedCard = cardRepository.save(initialCard);
        UUID cardId = savedCard.getId();

        // When: Attempt to top up with a negative amount
        Map<String, Object> topUpRequestBody = Map.of("amount", BigDecimal.valueOf(-10.00));
        ResponseEntity<String> negativeAmountResponse = restTemplate.exchange(
                "/cards/" + cardId + "/topup",
                HttpMethod.POST,
                new HttpEntity<>(topUpRequestBody),
                String.class
        );

        // Then: The response should be a Bad Request (400)
        assertThat(negativeAmountResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        
        // When: Attempt to top up with an amount of zero
        Map<String, Object> zeroAmountRequestBody = Map.of("amount", BigDecimal.valueOf(0.00));
        ResponseEntity<String> zeroAmountResponse = restTemplate.exchange(
                "/cards/" + cardId + "/topup",
                HttpMethod.POST,
                new HttpEntity<>(zeroAmountRequestBody),
                String.class
        );

        // Then: The response should also be a Bad Request (400)
        assertThat(zeroAmountResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // And: The balance should not have changed and no transactions should be recorded
        Card card = cardRepository.findById(cardId).orElseThrow();
        assertThat(card.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(transactionRepository.findByCardId(cardId)).isEmpty();
    }

    @Test
    void testSpend_onNonExistentCard_shouldReturnNotFound() {
        // Given: A non-existent card ID
        UUID nonExistentCardId = UUID.randomUUID();

        // When: Attempt to spend on the non-existent card
        Map<String, Object> spendRequestBody = Map.of("amount", BigDecimal.valueOf(10.00));
        ResponseEntity<String> response = restTemplate.exchange(
                "/cards/" + nonExistentCardId + "/spend",
                HttpMethod.POST,
                new HttpEntity<>(spendRequestBody),
                String.class
        );

        // Then: The response should be Not Found (404)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
    
    @Test
    void testTopUp_onNonExistentCard_shouldReturnNotFound() {
        // Given: A non-existent card ID
        UUID nonExistentCardId = UUID.randomUUID();

        // When: Attempt to top up on the non-existent card
        Map<String, Object> topUpRequestBody = Map.of("amount", BigDecimal.valueOf(10.00));
        ResponseEntity<String> response = restTemplate.exchange(
                "/cards/" + nonExistentCardId + "/topup",
                HttpMethod.POST,
                new HttpEntity<>(topUpRequestBody),
                String.class
        );

        // Then: The response should be Not Found (404)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}