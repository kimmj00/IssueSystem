package com.example.issuesystem.account.service;

import com.example.issuesystem.account.domain.Account;
import com.example.issuesystem.account.domain.AccountRole;
import com.example.issuesystem.account.dto.AccountResponse;
import com.example.issuesystem.account.dto.AdminPasswordChangeRequest;
import com.example.issuesystem.account.dto.LoginRequest;
import com.example.issuesystem.account.dto.SignupRequest;
import com.example.issuesystem.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AccountResponse signup(SignupRequest request) {
        String userId = request.userId().trim();
        if (accountRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("이미 사용 중인 ID입니다.");
        }

        Account account = new Account(userId, passwordEncoder.encode(request.password()), request.name().trim(), AccountRole.USER);
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public Account login(LoginRequest request) {
        Account account = accountRepository.findByUserId(request.userId().trim())
                .orElseThrow(() -> new IllegalArgumentException("ID 또는 비밀번호가 올바르지 않습니다."));
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new IllegalArgumentException("ID 또는 비밀번호가 올바르지 않습니다.");
        }
        return account;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findAll() {
        return accountRepository.findAll().stream().map(AccountResponse::from).toList();
    }

    @Transactional
    public void changeUserPassword(Long accountId, AdminPasswordChangeRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));

        if (account.getRole() != AccountRole.USER) {
            throw new IllegalArgumentException("일반 사용자 계정의 비밀번호만 변경할 수 있습니다.");
        }

        account.changePassword(passwordEncoder.encode(request.password()));
    }
}
