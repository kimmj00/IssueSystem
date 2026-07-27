package com.example.issuesystem.account.repository;

import com.example.issuesystem.account.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByUserId(String userId);
    boolean existsByUserId(String userId);
}
