package com.banking.notificationservice.service;


import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class NotificationService {

    @KafkaListener(topics = "transaction.otp.generated")
    public void consumeOtpGenerated(
            @Payload Map<String, Object> payload
    ){
        try{
            String accountNumber = (String) payload.get("accountNumber");
            String otp = (String) payload.get("otp");
            String transactionId = (String) payload.get("transactionId");
            String amount = payload.get("amount").toString();
            String reason =  (String) payload.get("reason");

            sendAlert(
                    accountNumber,
                    "TRANSACTION VERIFICATION REQUIRED",
                    String.format(
                            "Suspicious activity detected on your account. "+
                                    "Reason : %s "+
                                    "A transaction of %s is pending verification. "+
                                    "Your otp is : %s. Valid for 5 minutes. "+
                                    "IF this wasn't you - ignore this message."
                            , reason, amount, otp
                    )
            );
        }
        catch (Exception e){
            log.error("Error sending otp notification", e.getMessage());
        }
    }


    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(@Payload Map<String, Object> payload) {
        try{
            String senderAccount =(String) payload.get("senderAccountNumber");
            String receiverAccount =(String) payload.get("receiverAccountNumber");
            String amount =  payload.get("amount").toString();

//            Debit Alert
            sendAlert(senderAccount, "DEBIT ALERT", String.format("A transaction of %s has been debited from your account. If this wasn't you - contact us immediately.", amount));
//            Credit Alert
            sendAlert(receiverAccount, "CREDIT ALERT", String.format("A transaction of %s has been credited to your account.", amount));
        }
        catch(Exception e){
            log.error("Error sending transaction notification", e.getMessage());
        }
    }

    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetetced(@Payload Map<String, Object> payload){
        try{
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");
            sendAlert(accountNumber, "SUSPICIOUS ACTIVITY DETECTED", String.format("Your Account %s has been blocked. Suspicious activity detected on your account. Reason : %s. If this wasn't you - contact us immediately.", accountNumber, reason));
        }
        catch(Exception e){
            log.error("Error sending fraud notification", e.getMessage());
        }
    }

    @KafkaListener(topics = "transaction.refunded")
    public void consumeTransactionRefunded(@Payload Map<String, Object> payload){
        try{
            String accountNumber = (String) payload.get("senderAccountNumber");
            String amount =  payload.get("amount").toString();
            String reason = (String) payload.get("reason");
            sendAlert(accountNumber, "REFUND PROCESSED", String.format("Your transaction of %s was cancelled." +
                    "Reason : %s" +
                    "%s has been refunded to your account %s.", amount, reason, amount, accountNumber));
        }
        catch(Exception e){
            log.error("Error sending refund notification", e.getMessage());
        }
    }

    @KafkaListener(topics="payment.completed")
    public void consumePaymentCompleted(@Payload Map<String, Object> payload){
        try{
            String accountNumber = (String) payload.get("accountNumber");
            String amount =  payload.get("amount").toString();

            sendAlert(accountNumber, "PAYMENT SUCCESSFUL", String.format("Your payment of %s has been completed successfully."+
                    "Razorpay Id %s", amount, payload.get("razorpayPaymentId")));
        }
        catch(Exception e){
            log.error("Error sending payment notification", e.getMessage());
        }
    }

    @KafkaListener(topics="payment.failed")
    public void consumePaymentFailed(@Payload Map<String, Object> payload){
        try{
            String accountNumber = (String) payload.get("accountNumber");
            String amount =  payload.get("amount").toString();

            sendAlert(accountNumber, "PAYMENT FAILED", String.format("Your payment of %s has failed."+
                    "Please try again or contact support", amount));
        }
        catch(Exception e){
            log.error("Error sending payment failure notification", e.getMessage());
        }
    }
    private void sendAlert(String accountNumber, String subject, String message){
        log.info("-------------------------------------");
        log.info("Account: {} ", accountNumber);
        log.info("Subject: {} ", subject);
        log.info("Message: {} ", message);
        log.info("--------------------------------------");
    }
}
