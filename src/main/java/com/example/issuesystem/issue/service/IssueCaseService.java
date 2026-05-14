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

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /** 이슈 등록 */
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

    /** 이슈 수정 */
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
                request.getDeploymentVersion()
        );
    }

    /** 단건 상세 조회 */
    @Transactional
    public IssueCaseResponse get(Long id) {
        IssueCase issueCase = issueCaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("이슈 사례를 찾을 수 없습니다."));

        List<IssueAttachment> attachments = issueAttachmentRepository.findByIssueCaseId(id);
        return IssueCaseResponse.from(issueCase, attachments);
    }

    /** 전체 목록 조회 */
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
     *
     * 기간 검색:
     * - startDate: 해당 날짜 00:00:00 이상
     * - endDate: 해당 날짜 다음날 00:00:00 미만
     */
    @Transactional
    public PageResponse<IssueCaseResponse> search(
            String keyword,
            InfraType infraType,
            IssueStatus status,
            String customerName,
            String category,
            String deploymentVersion,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 5 : size;

        // PostgreSQL native query의 날짜 파라미터 타입 추론 오류 방지를 위해 null 대신 기본 범위를 넣는다.
        LocalDateTime startDateTime = startDate != null
                ? startDate.atStartOfDay()
                : LocalDateTime.of(1970, 1, 1, 0, 0);

        LocalDateTime endDateTime = endDate != null
                ? endDate.plusDays(1).atStartOfDay()
                : LocalDateTime.of(9999, 12, 31, 0, 0);

        Page<Long> idPage = issueCaseRepository.searchIds(
                keyword,
                infraType != null ? infraType.name() : null,
                status != null ? status.name() : null,
                customerName,
                category,
                deploymentVersion,
                startDateTime,
                endDateTime,
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

    private List<MultipartFile> emptyIfNull(List<MultipartFile> files) {
        return files == null ? Collections.emptyList() : files;
    }
}
