package com.geo.analytics.domain.entity;
import com.geo.analytics.domain.enums.PricingPlan;
import com.geo.analytics.domain.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.util.UUID;
@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "username", nullable = false, unique = true, length = 320)
    private String username;
    @Column(name = "password", length = 255)
    private String password;
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Role role = Role.VIEWER;
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_plan", nullable = false, length = 32)
    private PricingPlan pricingPlan = PricingPlan.STANDARD;
    @Lob
    @Column(name = "webauthn_credential_id")
    private byte[] webAuthnCredentialId;
    @Lob
    @Column(name = "webauthn_public_key_cose")
    private byte[] webAuthnPublicKeyCose;
    @Column(name = "webauthn_signature_count", nullable = false)
    private long webAuthnSignatureCount;
    @Column(name = "webauthn_transports", length = 256)
    private String webAuthnTransports;
    public UserEntity() {
    }
    public UUID getId() {
        return id;
    }
    public void setId(UUID id) {
        this.id = id;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public Role getRole() {
        return role;
    }
    public void setRole(Role role) {
        this.role = role;
    }
    public PricingPlan getPricingPlan() {
        return pricingPlan;
    }
    public void setPricingPlan(PricingPlan pricingPlan) {
        this.pricingPlan = pricingPlan;
    }
    public byte[] getWebAuthnCredentialId() {
        return webAuthnCredentialId;
    }
    public void setWebAuthnCredentialId(byte[] webAuthnCredentialId) {
        this.webAuthnCredentialId = webAuthnCredentialId;
    }
    public byte[] getWebAuthnPublicKeyCose() {
        return webAuthnPublicKeyCose;
    }
    public void setWebAuthnPublicKeyCose(byte[] webAuthnPublicKeyCose) {
        this.webAuthnPublicKeyCose = webAuthnPublicKeyCose;
    }
    public long getWebAuthnSignatureCount() {
        return webAuthnSignatureCount;
    }
    public void setWebAuthnSignatureCount(long webAuthnSignatureCount) {
        this.webAuthnSignatureCount = webAuthnSignatureCount;
    }
    public String getWebAuthnTransports() {
        return webAuthnTransports;
    }
    public void setWebAuthnTransports(String webAuthnTransports) {
        this.webAuthnTransports = webAuthnTransports;
    }
}
