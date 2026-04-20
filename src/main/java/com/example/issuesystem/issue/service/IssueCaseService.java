package com.example.issuesystem.issue.service;

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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IssueCaseService {

    private final IssueCaseRepository issueCaseRepository;
    private final IssueAttachmentRepository issueAttachmentRepository;
    private final FileStorageService fileStorageService;

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
                request.getTags()
        );
    }

    @Transactional
    public IssueCaseResponse get(Long id) {
        IssueCase issueCase = issueCaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("이슈 사례를 찾을 수 없습니다."));

        List<IssueAttachment> attachments = issueAttachmentRepository.findByIssueCaseId(id);
        return IssueCaseResponse.from(issueCase, attachments);
    }

    @Transactional
    public List<IssueCaseResponse> getAll() {
        return issueCaseRepository.findAll().stream()
                .map(issueCase -> IssueCaseResponse.from(
                        issueCase,
                        issueAttachmentRepository.findByIssueCaseId(issueCase.getId())
                ))
                .toList();
    }

    @Transactional
    public List<IssueCaseResponse> search(String keyword, InfraType infraType, IssueStatus status, String customerName) {
        return issueCaseRepository.search(
                        keyword,
                        infraType != null ? infraType.name() : null,
                        status != null ? status.name() : null,
                        customerName
                ).stream()
                .map(issueCase -> IssueCaseResponse.from(
                        issueCase,
                        issueAttachmentRepository.findByIssueCaseId(issueCase.getId())
                ))
                .toList();
    }

    private List<MultipartFile> emptyIfNull(List<MultipartFile>files) {
        return files == null ? Collections.emptyList() : files;
    }
}
