package com.banking.transactionservice.entity;

/*
*Transaction Lifecycle flow :
* PENDING -> PROCESSING -> COMPLETED(clean transaction)
*                       -> PENDING_VERIFICATION(Suspicious detected)
*                               -> completed(verified)
*                               -> Flagged(SAGA Refund)
*                        ->FAILED
*                        -FLAGGED
 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    PENDING_VERIFICATION,
    COMPLETED,
    FAILED,
    FLAGGED
}
