package ai.finsight.user;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="app_user")
public class AppUser {
 @Id private UUID id; @Column(nullable=false,unique=true,length=254) private String email; @Column(name="password_hash",nullable=false,length=100) private String passwordHash; @Column(name="display_name",nullable=false,length=100) private String displayName; @Column(name="created_at",nullable=false) private Instant createdAt;
 protected AppUser() {}
 public AppUser(String email,String passwordHash,String displayName){this.id=UUID.randomUUID();this.email=email;this.passwordHash=passwordHash;this.displayName=displayName;this.createdAt=Instant.now();}
 public UUID getId(){return id;} public String getEmail(){return email;} public String getPasswordHash(){return passwordHash;} public String getDisplayName(){return displayName;}
}
