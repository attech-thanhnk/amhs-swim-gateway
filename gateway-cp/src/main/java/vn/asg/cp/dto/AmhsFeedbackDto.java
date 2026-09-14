package vn.asg.cp.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một dòng phản hồi AMHS hiển thị trên Control Position.
 * <p>
 * Bốn loại: RN / NRN (thông báo do người nhận tạo) và DR / NDR (báo cáo do MTA tạo).
 */
@Data
@NoArgsConstructor
public class AmhsFeedbackDto {

    private Long id;

    /** RN | NRN | DR | NDR */
    private String ipnType;

    /** IPM-Identifier điện văn gốc — khoá đối chiếu của RN/NRN */
    private String subjectIpm;

    /** MTS-Identifier điện văn gốc — khoá đối chiếu của DR/NDR */
    private String subjectMts;

    /** Bên phát phản hồi */
    private String origin;

    /** Bên mà phản hồi này nói tới */
    private String recipient;

    /** receipt-time — chỉ RN */
    private String receiptTime;

    /** non-receipt-reason — chỉ NRN */
    private Integer nonReceiptReason;

    /** discard-reason — chỉ NRN */
    private Integer discardReason;

    /** non-delivery-reason-code — chỉ NDR */
    private String reasonCode;

    /** non-delivery-diagnostic-code — chỉ NDR */
    private String diagnosticCode;

    /** Nghĩa phụ thuộc ipnType: suppl-receipt-info với RN, supplementary-information với NDR */
    private String supplementaryInfo;

    private Integer status;
}
