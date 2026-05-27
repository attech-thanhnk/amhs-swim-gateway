package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to update an existing user (all fields optional)")
public class UpdateUserRequest {

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
}
