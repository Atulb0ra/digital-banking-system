package com.banking.transactionservice.service;


import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;

import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor

public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String TRANSACTION_INITIATED_TOPIC="transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC="transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC="transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC = "fraud.detected";


    /*
    *SAGA STEP -1 Initiate Transfer
    * Deducts from sender via feign
    * Saves transaction as PROCESSING
    * publish event  to kafka for fraud check
    * Returns
     */
    public TransactionResponse transfer(TransferRequest request){
        log.info("SAGA Start - Transfer : {} -> {} amount : {}", request.getSenderAccountNumber(), request.getReceiverAccountNumber(), request.getAmount() );

//         SAGA STEP 1 : deduct from Sender
        accountServiceClient.deductBalance(
                request.getSenderAccountNumber(),
                request.getAmount()
        );

        Transaction transaction = new Transaction();

        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Trasaction saved as processing : {}", savedTransaction.getId());

//         SAGA Step 2- publish for fraud check
        HashMap<String, Object> event = new HashMap<>();
        event.put("transactionId", savedTransaction.getId());
        event.put("senderAccountNumber", request.getSenderAccountNumber());
        event.put("receiverAccountNumber", request.getReceiverAccountNumber());
        event.put("amount", request.getAmount());
        event.put("description", request.getDescription());
//        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
//                savedTransaction.getId(),
//                savedTransaction.getSenderAccountNumber(),
//                savedTransaction.getReceiverAccountNumber(),
//                savedTransaction.getAmount(),
//                savedTransaction.getDescription()
//        );

        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC, savedTransaction.getId(), event);
        log.info("SAGA STEP 2 - TransactionInitiatedEvent published : {}", savedTransaction.getId());

        return mapToResponse(savedTransaction);
    }

    public TransactionResponse getTransaction(String transactionId){
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found for id: " + transactionId));

        return mapToResponse(transaction);
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber){

        return transactionRepository.findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

    }

    public TransactionResponse verifyOTP(String transactionId, String otp){


        log.info("Verifying OTP for transaction : {}", transactionId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found" + transactionId));

        String otpKey = "verification:otp" + transactionId;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        if(storedOtp == null){
//            OTP already expired
            log.warn("OTP expired for transaction : {}", transactionId);
            compensateTransaction(transaction, "OTP expired - transaction cancelled amount refunded");
            return mapToResponse(transaction);
        }

        if(!storedOtp.equals(otp)){
//            Block account and refund
            log.warn("Wrong OTP- blocking account and refunding : {}", transactionId);
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transaction, "Wrong otp entered- otp cancelled," +
                    "account blocked for security");

            return mapToResponse(transaction);
        }

//        else OTP correct - complete transaction
        log.info("OTP-verified - completing transaction : {}", transactionId);
        redisTemplate.delete(otpKey);
        completeTransaction(transaction);
        return mapToResponse(transaction);
    }

    private void compensateTransaction(Transaction transaction, String reason){
        log.warn("SAGA COMPENDSATION- refunding : {} amount : {}", transaction.getSenderAccountNumber(), transaction.getAmount());

//        Credit money back top sender synchronously

        accountServiceClient.creditBalance(
                transaction.getSenderAccountNumber(),
                transaction.getAmount()
        );

        transaction.setStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason + " - SAGA compensation executed and amount refunded at" + LocalDateTime.now());

        transactionRepository.save(transaction);

//        publish refund event - notification service will alert user

        Map<String, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount", transaction.getAmount());
        refundEvent.put("reason", reason);

        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC, transaction.getId(), refundEvent);

        log.info("SAGA COMPENSATION COMPLETE - {} refunded to : {}", transaction.getAmount(), transaction.getSenderAccountNumber());
    }

    private void blockAccountAndCompensate(Transaction transaction, String reason){
//        PUBLISH fraud.detected -> account service will block account

        Map<String, Object> fraudEvent = new HashMap<>();

        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("accountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("reason", reason);

        kafkaTemplate.send(FRAUD_DETECTED_TOPIC, transaction.getSenderAccountNumber(), fraudEvent);
        log.warn("frad.detected published - account : {} will be blocked, kindly contact to the bank",
                 transaction.getSenderAccountNumber());

//        SAGA COMPENSATION - refund sender
        compensateTransaction(transaction, reason);
    }

    private void completeTransaction(Transaction transaction){

    transaction.setStatus(TransactionStatus.COMPLETED);
    transaction.setCompletedAt(LocalDateTime.now());
    transactionRepository.save(transaction);

//        publish the event transaction completed event
        HashMap<String, Object> event = new HashMap<>();
        event.put("transactionId", transaction.getId());
        event.put("senderAccountNumber", transaction.getSenderAccountNumber());
        event.put("receiverAccountNumber", transaction.getReceiverAccountNumber());
        event.put("amount", transaction.getAmount());
        event.put("description", transaction.getDescription());

//        TransactionCompletedEvent transactionCompletedEvent = new TransactionCompletedEvent(
//                transaction.getId(),
//                transaction.getSenderAccountNumber(),
//                transaction.getReceiverAccountNumber(),
//                transaction.getAmount(),
//                transaction.getDescription());

        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC, transaction.getId(), event);

        log.info("SAGA complete - Transaction {} completed", transaction.getId());



    }

    public void processCleanResult(String transactionId){
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found" + transactionId));

        if(transaction.getStatus() != TransactionStatus.PROCESSING){
            log.warn("Transaction {} not PROCESSING - skipping", transactionId);
            return;
        }

        completeTransaction(transaction);
    }

    public TransactionResponse mapToResponse(Transaction savedTransaction){
        TransactionResponse response = new TransactionResponse();
        response.setId(savedTransaction.getId());
        response.setSenderAccountNumber(savedTransaction.getSenderAccountNumber());
        response.setReceiverAccountNumber(savedTransaction.getReceiverAccountNumber());
        response.setAmount(savedTransaction.getAmount());
        response.setType(savedTransaction.getType());
        response.setStatus(savedTransaction.getStatus());
        response.setDescription(savedTransaction.getDescription());
        response.setReferenceNumber(savedTransaction.getReferenceNumber());
        response.setFailureReason(savedTransaction.getFailureReason());
        response.setCreatedAt(savedTransaction.getCreatedAt());
        response.setCompletedAt(savedTransaction.getCompletedAt());


        return response;
    }


}
