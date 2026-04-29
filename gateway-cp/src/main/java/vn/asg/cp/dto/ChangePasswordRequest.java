package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to change user password")
public class ChangePasswordRequest {

    @Schema(description = "Current password",
            example = "oldpass123",
            required = true)
    private String oldPassword;

    @Schema(description = "New password",
            example = "newpass456",
            required = true)
    private String newPassword;
}
