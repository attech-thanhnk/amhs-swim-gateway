package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to create a new user")
public class CreateUserRequest {

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
}
