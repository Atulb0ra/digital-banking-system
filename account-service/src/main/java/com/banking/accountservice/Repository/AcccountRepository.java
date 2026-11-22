package com.banking.accountservice.Repository;

import com.banking.accountservice.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AcccountRepository extends JpaRepository<Account,String> {

    boolean existsByEmail(String email);
    boolean existsByAccountNumber(String accountNumber);
    Optional<Account> findByAccountNumber(String accountNumber);
}
