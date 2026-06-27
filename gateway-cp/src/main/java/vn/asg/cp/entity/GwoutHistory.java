package vn.asg.cp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GwoutHistory Table — Represents archived gwout records.
 */
@Entity
@Table(name = "gwout_history")
@Data
@NoArgsConstructor
public class GwoutHistory {



    @Id
    private Long msgid;

    @Column(name = "amhsid", length = 200)
    private String amhsid;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "time")
    private LocalDateTime time;

    @Column(name = "filing_time", length = 6)
    private String filingTime;

    @Column(name = "TEXT", columnDefinition = "MEDIUMTEXT")
    private String text;

    @Column(name = "body_type", length = 10)
    private String bodyType = "text";

    @Column(name = "origin", length = 200)
    private String origin;

    @Column(name = "address", length = 1000)
    private String address;

    @Column(name = "optional_heading", length = 60)
    private String optionalHeading;

    @Column(name = "amhs_ttl")
    private LocalDateTime amhsTtl;

    @Column(name = "amhs_registered_id", length = 200)
    private String amhsRegisteredId;

    @Column(name = "amhs_delivery_report")
    private Boolean amhsDeliveryReport = false;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "status")
    private Integer status = MessageStatus.OUT_PENDING.getValue();

    @Column(name = "error_type")
    private Integer errorType = ErrorType.UNDEFINED.getValue();

    @Column(name = "payload_content", columnDefinition = "MEDIUMTEXT")
    private String payloadContent;
}
