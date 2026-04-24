package com.example.issuesystem.issue.service;

import com.example.issuesystem.common.PageResponse;
import com.example.issuesystem.issue.domain.InfraType;
import com.example.issuesystem.issue.domain.IssueAttachment;
import com.example.issuesystem.issue.domain.IssueCase;
import com.example.issuesystem.issue.domain.IssueStatus;
import com.example.issuesystem.issue.dto.IssueCaseCreateRequest;
import com.example.issuesystem.issue.dto.IssueCaseResponse;
import com.example.issuesystem.issue.dto.IssueCaseUpdateRequest;
import com.example.issuesystem.issue.repository.IssueAttachmentRepository;
import com.example.issuesystem.issue.repository.IssueCaseRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueCaseService {

    private final IssueCaseRepository issueCaseRepository;
    private final IssueAttachmentRepository issueAttachmentRepository;
    private final FileStorageService fileStorageService;

    /**
     * 이슈 등록
     * - 이슈 본문 저장
     * - 첨부파일이 있으면 파일 저장 후 첨부 테이블에 기록
     */
    @Transactional
    public Long create(IssueCaseCreateRequest request, List<MultipartFile> files) {
        IssueCase issueCase = IssueCase.builder()
                .title(request.getTitle())
                .infraType(request.getInfraType())
                .systemName(request.getSystemName())
                .customerName(request.getCustomerName())
                .versionInfo(request.getVersionInfo())
                .status(request.getStatus())
                .symptomSummary(request.getSymptomSummary())
                .symptomDetail(request.getSymptomDetail())
                .causeDetail(request.getCauseDetail())
                .actionDetail(request.getActionDetail())
                .tags(request.getTags())
                .authorName(request.getAuthorName())
                .category(request.getCategory())
                .deploymentVersion(request.getDeploymentVersion())
                .agentManagerVersion(request.getAgentManagerVersion())
                .category(request.getCategory())
                .build();

        IssueCase saved = issueCaseRepository.save(issueCase);

        for (MultipartFile file : emptyIfNull(files)) {
            FileStorageService.StoredFileInfo stored = fileStorageService.store(file, saved.getId());

            IssueAttachment attachment = IssueAttachment.builder()
                    .issueCase(saved)
                    .originalFileName(stored.originalFileName())
                    .storedFileName(stored.storedFileName())
                    .storedPath(stored.storedPath())
                    .fileSize(stored.fileSize())
                    .build();

            issueAttachmentRepository.save(attachment);
        }

        return saved.getId();
    }

    /**
     * 이슈 수정
     */
    @Transactional
    public void update(Long id, IssueCaseUpdateRequest request) {
        IssueCase issueCase = issueCaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("이슈 사례를 찾을 수 없습니다."));

        issueCase.update(
                request.getTitle(),
                request.getInfraType(),
                request.getSystemName(),
                request.getCustomerName(),
                request.getVersionInfo(),
                request.getStatus(),
                request.getSymptomSummary(),
                request.getSymptomDetail(),
                request.getCauseDetail(),
                request.getActionDetail(),
                request.getTags(),
                request.getCategory(),
                request.getDeploymentVersion(),
                request.getAgentManagerVersion()
        );
    }

    /**
     * 단건 상세 조회
     */
    @Transactional
    public IssueCaseResponse get(Long id) {
        IssueCase issueCase = issueCaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("이슈 사례를 찾을 수 없습니다."));

        List<IssueAttachment> attachments = issueAttachmentRepository.findByIssueCaseId(id);
        return IssueCaseResponse.from(issueCase, attachments);
    }

    /**
     * 전체 목록 조회
     * 현재는 주로 search API를 쓰므로 우선순위는 낮지만,
     * 관리자용 또는 단순 전체 조회용으로 유지
     */
    @Transactional
    public List<IssueCaseResponse> getAll() {
        List<IssueCase> issues = issueCaseRepository.findAll();

        if (issues.isEmpty()) {
            return List.of();
        }

        List<Long> ids = issues.stream()
                .map(IssueCase::getId)
                .toList();

        Map<Long, List<IssueAttachment>> attachmentMap = issueAttachmentRepository.findByIssueCaseIdIn(ids)
                .stream()
                .collect(Collectors.groupingBy(file -> file.getIssueCase().getId()));

        return issues.stream()
                .map(issueCase -> IssueCaseResponse.from(
                        issueCase,
                        attachmentMap.getOrDefault(issueCase.getId(), List.of())
                ))
                .toList();
    }

    /**
     * 검색 + 페이징 조회
     * 1) 조건에 맞는 ID만 먼저 페이지 조회
     * 2) 해당 ID들로 엔티티 재조회
     * 3) 첨부파일도 한 번에 조회해서 N+1 완화
     * 4) 원래 ID 순서대로 응답 순서 복원
     */
    @Transactional
    public PageResponse<IssueCaseResponse> search(
            String keyword,
            InfraType infraType,
            IssueStatus status,
            String customerName,
            int page,
            int size
    ) {
        // 방어 로직: 음수 페이지나 0 이하 size 방지
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 5 : size;

        Page<Long> idPage = issueCaseRepository.searchIds(
                keyword,
                infraType != null ? infraType.name() : null,
                status != null ? status.name() : null,
                customerName,
                PageRequest.of(safePage, safeSize)
        );

        List<Long> ids = idPage.getContent();

        if (ids.isEmpty()) {
            Page<IssueCaseResponse> emptyPage = new PageImpl<>(
                    List.of(),
                    idPage.getPageable(),
                    idPage.getTotalElements()
            );
            return PageResponse.from(emptyPage);
        }

        List<IssueCase> issues = issueCaseRepository.findByIdIn(ids);

        Map<Long, IssueCase> issueMap = issues.stream()
                .collect(Collectors.toMap(IssueCase::getId, Function.identity()));

        Map<Long, List<IssueAttachment>> attachmentMap = issueAttachmentRepository.findByIssueCaseIdIn(ids)
                .stream()
                .collect(Collectors.groupingBy(file -> file.getIssueCase().getId()));

        List<IssueCaseResponse> content = ids.stream()
                .map(issueMap::get)
                .filter(issue -> issue != null)
                .map(issueCase -> IssueCaseResponse.from(
                        issueCase,
                        attachmentMap.getOrDefault(issueCase.getId(), List.of())
                ))
                .toList();

        Page<IssueCaseResponse> responsePage = new PageImpl<>(
                content,
                idPage.getPageable(),
                idPage.getTotalElements()
        );

        return PageResponse.from(responsePage);
    }

    /**
     * null 파일 리스트 방지용 유틸
     */
    private List<MultipartFile> emptyIfNull(List<MultipartFile> files) {
        return files == null ? Collections.emptyList() : files;
    }
}