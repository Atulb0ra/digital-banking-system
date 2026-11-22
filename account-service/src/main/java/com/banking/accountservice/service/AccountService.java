package com.banking.accountservice.service;

import com.banking.accountservice.Repository.AcccountRepository;
import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Slf4j

public class AccountService {

    private final AcccountRepository accountRepository;
    private static SecureRandom secureRandom = new SecureRandom();

    public AccountResponse createAccount(CreateAccountRequest request){
        log.info("Creating account for : {}", request.getEmail());
        if(accountRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Account already exists for email: " + request.getEmail());
        }

        Account account = Account.builder()
                .accountHolderName(request.getAccountHolderName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .accountType(request.getAccountType())
                .accountStatus(AccountStatus.ACTIVE)
                .balance(request.getInitialDeposit())
                .accountNumber(generateAccountNumber())
                .dailyTansactionLimit(request.getAccountType() == AccountType.SAVINGS ?
                        new BigDecimal("100000") : new BigDecimal("500000"))
                .build();

        Account savedAccount = accountRepository.save(account);
        log.info("Account created for : {}", savedAccount.getAccountNumber());

        return mapToResponse(savedAccount);
    }

    public AccountResponse getAccount(String accountNumber){
        Account account = accountRepository.findByAccountNumber(accountNumber).
                orElseThrow(() -> new RuntimeException("Account not found for account number: " + accountNumber));

        return mapToResponse(account);
    }

    public BigDecimal getBalance(String accountNumber){
        Account account = accountRepository.findByAccountNumber(accountNumber).
                orElseThrow(() -> new RuntimeException("Account not found for account number: " + accountNumber));

        return account.getBalance();
    }

    /*
     *Block Account called by Fraud detection service via kafka
     */
    public void blockAccount(String accountNumber){
        log.info("Blocking account for account number: {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber).
                orElseThrow(() -> new RuntimeException("Account not found for account number: " + accountNumber));

        account.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked for account number: {}", accountNumber);
    }


    /*
    *Deduction of Balance from senders account and it is called by transaction service
     */
    public void deductBalance(String accountNumber, BigDecimal amount){
        log.info("Deducting balance for account number : {} with the given amount : {} ", accountNumber, amount);

        Account account = accountRepository.findByAccountNumber(accountNumber).
                orElseThrow(() -> new RuntimeException("Account not found for account number: " + accountNumber));

        if(account.getAccountStatus() != AccountStatus.ACTIVE){
            throw new RuntimeException("Account is not active for account number: " + accountNumber);
        }

        if(account.getBalance().compareTo(amount) < 0){
            throw new RuntimeException("Balance cannot be less than zero for account number: " + accountNumber);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Account deducted for account number: {}", accountNumber);
    }


    /*
    *credit Balance and called by transaction service via kafka
     */

    public  void creditBalance(String accountNumber, BigDecimal amount){
        log.info("Credit balance for account number : {} with the given amount : {}",accountNumber, amount);
        Account account = accountRepository.findByAccountNumber(accountNumber).
                orElseThrow(() -> new RuntimeException("Account not found for account number: " + accountNumber));

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.info("Account credited for account number: {} with new Balance:{}", accountNumber, account.getBalance());
    }


    private String generateAccountNumber(){
        String accountNumber;

        do{
            long number = secureRandom.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%012d", number);
        }
        while(accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    private AccountResponse mapToResponse(Account account){
        AccountResponse response = AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountHolderName(account.getAccountHolderName())
                .email(account.getEmail())
                .phone(account.getPhone())
                .accountType(account.getAccountType())
                .status(account.getAccountStatus())
                .balance(account.getBalance())
                .dailyTansactionLimit(account.getDailyTansactionLimit())
                .createdAt(account.getCreatedAt())
                .build();

        return response;
    }
}
