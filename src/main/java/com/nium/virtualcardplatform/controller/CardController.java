package com.nium.virtualcardplatform.controller;

import com.nium.virtualcardplatform.model.Card;
import com.nium.virtualcardplatform.model.Transaction;
import com.nium.virtualcardplatform.service.CardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/cards") // Base path for all endpoints in this controller
public class CardController {

    private final CardService cardService;

    // Dependency Injection
    @Autowired
    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    /**
     * Endpoint to create a new virtual card.
     * POST /cards
     * Request Body: {"cardholderName": "Alice", "initialBalance": 100.00}
     */
    @PostMapping
    public ResponseEntity<Card> createCard(@RequestBody Map<String, Object> payload) {
        String cardholderName = (String) payload.get("cardholderName");
        BigDecimal initialBalance = new BigDecimal(payload.get("initialBalance").toString());

        // Basic validation for request payload
        if (cardholderName == null || cardholderName.trim().isEmpty()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        if (initialBalance == null || initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        try {
            Card newCard = cardService.createCard(cardholderName, initialBalance);
            return new ResponseEntity<>(newCard, HttpStatus.CREATED); // 201 Created
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST); // 400 Bad Request for invalid input
        }
    }

    /**
     * Endpoint to get card details by ID.
     * GET /cards/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Card> getCardById(@PathVariable UUID id) {
        return cardService.getCardById(id)
                .map(card -> new ResponseEntity<>(card, HttpStatus.OK)) // 200 OK if found
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND)); // 404 Not Found if not found
    }

    /**
     * Endpoint to add funds (top-up) to a card.
     * POST /cards/{id}/topup
     * Request Body: {"amount": 50.00}
     */
    @PostMapping("/{id}/topup")
    public ResponseEntity<Card> topUpCard(@PathVariable UUID id, @RequestBody Map<String, Object> payload) {
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST); // Invalid amount
        }

        try {
            Card updatedCard = cardService.topUp(id, amount);
            return new ResponseEntity<>(updatedCard, HttpStatus.OK); // 200 OK
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND); // Card not found
        } catch (Exception e) { // Catch any other unexpected errors from service layer
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR); // 500 Internal Server Error
        }
    }

    /**
     * Endpoint to spend from a card.
     * POST /cards/{id}/spend
     * Request Body: {"amount": 30.00}
     */
    @PostMapping("/{id}/spend")
    public ResponseEntity<Card> spendFromCard(@PathVariable UUID id, @RequestBody Map<String, Object> payload) {
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST); // Invalid amount
        }

        try {
            Card updatedCard = cardService.spend(id, amount);
            return new ResponseEntity<>(updatedCard, HttpStatus.OK); // 200 OK
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND); // Card not found
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST); // 400 Bad Request for insufficient balance
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR); // 500 Internal Server Error
        }
    }

    /**
     * Endpoint to get transaction history for a card.
     * GET /cards/{id}/transactions
     */
    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<Transaction>> getCardTransactions(@PathVariable UUID id) {
        // First, check if the card exists. If not, return 404.
        if (cardService.getCardById(id).isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        List<Transaction> transactions = cardService.getCardTransactions(id);
        return new ResponseEntity<>(transactions, HttpStatus.OK); // 200 OK
    }
}