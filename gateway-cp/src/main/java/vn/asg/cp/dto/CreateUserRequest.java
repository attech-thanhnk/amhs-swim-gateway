package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to create a new user")
public class CreateUserRequest {

    public CreateUserRequest() {}

    @Schema(description = "Username (unique)",
            example = "operator01",
            required = true)
    private String username;

    @Schema(description = "Password",
            example = "password123",
            required = true)
    private String password;

    @Schema(description = "User role",
            example = "OPERATOR",
            allowableValues = {"ADMIN", "OPERATOR", "USER"},
            required = true)
    private String role;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
