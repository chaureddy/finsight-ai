package ai.finsight.user;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
@RestController @RequestMapping("/api/auth")
public class AuthController {
 private final UserRepository users; private final PasswordEncoder encoder;
 public AuthController(UserRepository users,PasswordEncoder encoder){this.users=users;this.encoder=encoder;}
 public record Registration(@Email @NotBlank @Size(max=254) String email,@NotBlank @Size(min=12,max=72) String password,@NotBlank @Size(min=1,max=100) String displayName){}
 @PostMapping("/register") public ResponseEntity<?> register(@Valid @RequestBody Registration input){String email=input.email().trim().toLowerCase();if(users.findByEmailIgnoreCase(email).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Email is already registered");AppUser user=users.save(new AppUser(email,encoder.encode(input.password()),input.displayName().trim()));return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id",user.getId(),"email",user.getEmail(),"displayName",user.getDisplayName()));}
 @GetMapping("/me") public Map<String,Object> me(org.springframework.security.core.Authentication auth){AppUser user=users.findByEmailIgnoreCase(auth.getName()).orElseThrow();return Map.of("id",user.getId(),"email",user.getEmail(),"displayName",user.getDisplayName());}
}
