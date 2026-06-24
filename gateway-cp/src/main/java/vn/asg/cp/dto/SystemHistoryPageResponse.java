package vn.asg.cp.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import vn.asg.cp.dto.SystemHistoryResponseDto;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class SystemHistoryPageResponse {
    private Page<SystemHistoryResponseDto> histories;
    private long unreadCount;
    
    // Constructors, getters, setters
    public SystemHistoryPageResponse(Page<SystemHistoryResponseDto> histories, long unreadCount) {
        this.histories = histories;
        this.unreadCount = unreadCount;
    }
}
