package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to change user password")
public class ChangePasswordRequest {

    public ChangePasswordRequest() {}

    @Schema(description = "Current password",
            example = "oldpass123",
            required = true)
    private String oldPassword;

    @Schema(description = "New password",
            example = "newpass456",
            required = true)
    private String newPassword;

    public String getOldPassword() { return oldPassword; }
    public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
