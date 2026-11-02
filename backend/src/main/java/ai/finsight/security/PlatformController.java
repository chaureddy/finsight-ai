package ai.finsight.security;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
public class PlatformController {
 @GetMapping("/api/auth/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("headerName",token.getHeaderName(),"token",token.getToken());}
 @GetMapping("/api/health") public Map<String,String> health(){return Map.of("status","ok");}
}
