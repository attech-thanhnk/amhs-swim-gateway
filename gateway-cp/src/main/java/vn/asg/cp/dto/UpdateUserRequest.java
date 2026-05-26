package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to update an existing user (all fields optional)")
public class UpdateUserRequest {

    public UpdateUserRequest() {}

    @Schema(description = "New username",
            example = "operator01_updated")
    private String username;

    @Schema(description = "New password (if changing)",
            example = "newpassword456")
    private String password;

    @Schema(description = "New role",
            example = "USER",
            allowableValues = {"ADMIN", "OPERATOR", "USER"})
    private String role;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
