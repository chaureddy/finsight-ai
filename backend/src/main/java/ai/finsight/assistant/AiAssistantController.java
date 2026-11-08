package ai.finsight.assistant;

import ai.finsight.intelligence.AdvancedFinancialAnalyticsService;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AiAssistantController {
    private final AiAssistantService assistantService;
    private final AdvancedFinancialAnalyticsService advancedAnalytics;

    public AiAssistantController(AiAssistantService assistantService, AdvancedFinancialAnalyticsService advancedAnalytics) {
        this.assistantService = assistantService;
        this.advancedAnalytics = advancedAnalytics;
    }

    public record ChatRequest(@Size(max = 1000) String message) {}

    @PostMapping("/assistant/chat")
    public AiAssistantService.ChatResponse chat(@RequestBody(required = false) ChatRequest request, Authentication auth) {
        String msg = request != null && request.message() != null ? request.message() : "";
        return assistantService.chat(auth.getName(), msg);
    }

    @GetMapping("/assistant/insights")
    public List<AiAssistantService.SmartInsight> insights(Authentication auth) {
        return assistantService.getInsights(auth.getName());
    }

    @GetMapping("/intelligence/advanced")
    public AdvancedFinancialAnalyticsService.ComprehensiveIntelligence advanced(Authentication auth) {
        return advancedAnalytics.analyze(auth.getName());
    }
}

