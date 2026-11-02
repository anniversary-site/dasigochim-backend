package com.dasigochim.photo.domain;

import com.dasigochim.report.domain.Report;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "report_photos")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportPhoto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "report_id", nullable = false)
    private Report report;
    @Column(nullable = false, length = 500)
    private String url;
    @Column(name = "original_filename")
    private String originalFilename;
    @Column(name = "mime_type", length = 50)
    private String mimeType;
    @Column(name = "size_bytes")
    private Long sizeBytes;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ReportPhoto(Report report, String url, String originalFilename, String mimeType, long sizeBytes, int displayOrder) {
        this.report = report;
        this.url = url;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.displayOrder = displayOrder;
        this.createdAt = Instant.now();
    }
}
