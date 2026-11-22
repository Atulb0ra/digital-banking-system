package com.banking.paymentservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;

@Entity
@Table(name = "payments")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String razorpayOrderId;
    private String razorpayPaymentId;

    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String description;

    @Column(name = "failure_reason")
    private String failureReason;

    @CreationTimestamp
    private String createdAt;

    @UpdateTimestamp
    private String updatedAt;
}
