package com.example.issuesystem.issue.service;

import com.example.issuesystem.common.PageResponse;
import com.example.issuesystem.issue.domain.InfraType;
import com.example.issuesystem.issue.domain.IssueCase;
import com.example.issuesystem.issue.domain.IssueStatus;
import com.example.issuesystem.issue.dto.IssueCaseCreateRequest;
import com.example.issuesystem.issue.dto.IssueCaseResponse;
import com.example.issuesystem.issue.dto.IssueCaseUpdateRequest;
import com.example.issuesystem.issue.repository.IssueCaseRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueCaseService {

    private final IssueCaseRepository issueCaseRepository;

    /**
     * 이슈 등록
     *
     * issue_attachment 테이블을 사용하지 않으므로 첨부파일 저장 로직은 제거했다.
     */
    @Transactional
    public Long create(IssueCaseCreateRequest request) {
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

        return issueCaseRepository.save(issueCase).getId();
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

        return IssueCaseResponse.from(issueCase);
    }

    /** 전체 목록 조회 */
    @Transactional
    public List<IssueCaseResponse> getAll() {
        return issueCaseRepository.findAll().stream()
                .map(IssueCaseResponse::from)
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

        // searchIds()에서 가져온 ID 순서를 유지한다.
        // findByIdIn()은 DB가 반환 순서를 보장하지 않으므로 idPage의 ids 순서대로 다시 정렬한다.
        List<IssueCaseResponse> content = ids.stream()
                .map(issueMap::get)
                .filter(issue -> issue != null)
                .map(IssueCaseResponse::from)
                .toList();

        Page<IssueCaseResponse> responsePage = new PageImpl<>(
                content,
                idPage.getPageable(),
                idPage.getTotalElements()
        );

        return PageResponse.from(responsePage);
    }
}
