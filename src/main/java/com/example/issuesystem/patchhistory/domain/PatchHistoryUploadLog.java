package com.example.issuesystem.patchhistory.domain;

import com.example.issuesystem.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "patch_history_upload_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatchHistoryUploadLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false)
    private int savedCount;

    @Column(nullable = false)
    private int excludedCount;

    public PatchHistoryUploadLog(String fileName, int savedCount, int excludedCount) {
        this.fileName = fileName;
        this.savedCount = savedCount;
        this.excludedCount = excludedCount;
    }
}
