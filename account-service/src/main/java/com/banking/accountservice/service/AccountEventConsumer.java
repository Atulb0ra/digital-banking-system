package com.banking.accountservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEventConsumer {

    private final AccountService accountService;

    /*
    consume transaction completed event from kafka
    credits receiver account no fraud detected
     */

    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(
            @Payload Map<String, Object> payload
    ){
        try{
            String receiverAccount = (String) payload.get("receiverAccountNumber");
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());

            log.info("Crediting amount : {} to account : {}", amount, receiverAccount);

            accountService.creditBalance(receiverAccount, amount);
        }
        catch(Exception e){
            log.error("Error while processing transaction : {}", e.getMessage());
        }
    }

    /*
    consume fraud detected event from akfka
    Blocks the flagged account
     */

    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(
            @Payload Map<String, Object> payload
    ){
        try{
            String receiverAccount = (String) payload.get("accountNumber");
            log.info("Blocking account : {} due to fraud detection", receiverAccount);
            accountService.blockAccount(receiverAccount);
        }
        catch(Exception e){
            log.error("Error while processing transaction : {}", e.getMessage());
        }
    }
}
