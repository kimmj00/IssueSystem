package com.example.issuesystem.account.controller;

import com.example.issuesystem.account.domain.Account;
import com.example.issuesystem.account.domain.AccountRole;
import com.example.issuesystem.account.dto.AccountResponse;
import com.example.issuesystem.account.dto.AdminPasswordChangeRequest;
import com.example.issuesystem.account.dto.LoginRequest;
import com.example.issuesystem.account.dto.SignupRequest;
import com.example.issuesystem.account.service.AccountService;
import com.example.issuesystem.common.ApiResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {
    private static final String SESSION_ACCOUNT_ID = "accountId";
    private static final String SESSION_ACCOUNT_ROLE = "accountRole";
    private final AccountService accountService;

    @PostMapping("/signup")
    public ApiResponse<AccountResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(accountService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<AccountResponse> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        Account account = accountService.login(request);
        session.setAttribute(SESSION_ACCOUNT_ID, account.getId());
        session.setAttribute(SESSION_ACCOUNT_ROLE, account.getRole());
        return ApiResponse.ok(AccountResponse.from(account));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.okMessage("로그아웃되었습니다.");
    }

    @GetMapping
    public ApiResponse<List<AccountResponse>> findAll(HttpSession session) {
        validateAdmin(session);
        return ApiResponse.ok(accountService.findAll());
    }

    @PatchMapping("/{accountId}/password")
    public ApiResponse<Void> changePassword(
            @PathVariable Long accountId,
            @Valid @RequestBody AdminPasswordChangeRequest request,
            HttpSession session
    ) {
        validateAdmin(session);
        accountService.changeUserPassword(accountId, request);
        return ApiResponse.okMessage("비밀번호가 변경되었습니다.");
    }

    private void validateAdmin(HttpSession session) {
        if (session.getAttribute(SESSION_ACCOUNT_ID) == null
                || session.getAttribute(SESSION_ACCOUNT_ROLE) != AccountRole.ADMIN) {
            throw new IllegalArgumentException("관리자만 사용할 수 있습니다.");
        }
    }
}
