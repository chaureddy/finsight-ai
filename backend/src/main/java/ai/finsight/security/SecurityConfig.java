package ai.finsight.security;
import ai.finsight.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import java.util.List;
@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
 @Bean UserDetailsService userDetailsService(UserRepository users){return email->users.findByEmailIgnoreCase(email).map(u->User.withUsername(u.getEmail()).password(u.getPasswordHash()).roles("USER").build()).orElseThrow(()->new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found"));}
 @Bean SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
  http.cors(Customizer.withDefaults()).csrf(Customizer.withDefaults())
   .authorizeHttpRequests(a->a.requestMatchers("/api/auth/register","/api/auth/csrf","/api/health").permitAll().requestMatchers(HttpMethod.OPTIONS,"/**").permitAll().anyRequest().authenticated())
   .formLogin(f->f.loginProcessingUrl("/api/auth/login").usernameParameter("email").successHandler((req,res,auth)->{res.setStatus(204);}).failureHandler((req,res,e)->{res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"error\":\"Invalid credentials\"}");}))
   .logout(l->l.logoutUrl("/api/auth/logout").logoutSuccessHandler((req,res,auth)->res.setStatus(204)))
   .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"error\":\"Authentication required\"}");}));
  return http.build();
 }
 @Bean CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origin}") String origin){CorsConfiguration c=new CorsConfiguration();c.setAllowedOrigins(List.of(origin));c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("Content-Type","X-CSRF-TOKEN","X-XSRF-TOKEN"));c.setAllowCredentials(true);c.setMaxAge(3600L);UrlBasedCorsConfigurationSource s=new UrlBasedCorsConfigurationSource();s.registerCorsConfiguration("/**",c);return s;}
}
