package com.example.issuesystem.account.config;

import com.example.issuesystem.account.domain.Account;
import com.example.issuesystem.account.domain.AccountRole;
import com.example.issuesystem.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!accountRepository.existsByUserId("admin")) {
            accountRepository.save(new Account("admin", passwordEncoder.encode("admin135!"), "관리자", AccountRole.ADMIN));
        }
    }
}
