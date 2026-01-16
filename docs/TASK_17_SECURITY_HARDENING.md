# Task 17: Security Hardening Implementation

## Overview

This document describes the security hardening implementations for the Smart Health Tracker application, covering secure token storage, request signing, model integrity verification, and certificate pinning.

## Implemented Features

### 17.1 Secure Token Storage with Android Keystore

**Location:** `data/src/main/kotlin/com/healthtracker/data/security/SecureTokenStorage.kt`

**Features:**
- Short-lived access tokens (15 minutes default expiry)
- Long-lived refresh tokens (30 days default expiry)
- Automatic token expiration checking
- Android Keystore backed encryption using EncryptedSharedPreferences
- AES-256-GCM encryption for values
- AES-256-SIV encryption for keys

**Key Methods:**
```kotlin
// Store token with expiration
suspend fun storeToken(token: String, expiresInSeconds: Long = 900)

// Retrieve token (returns null if expired)
suspend fun retrieveToken(): String?

// Check if token is expired
suspend fun isTokenExpired(): Boolean

// Store refresh token with expiration
suspend fun storeRefreshToken(token: String, expiresInSeconds: Long = 2592000)

// Retrieve refresh token (returns null if expired)
suspend fun retrieveRefreshToken(): String?
```

**Security Properties:**
- Tokens are automatically cleared when expired
- All operations run on IO dispatcher
- Fallback to regular SharedPreferences if EncryptedSharedPreferences fails (logs error)
- Timestamps stored in seconds since epoch

**Usage Example:**
```kotlin
@Inject lateinit var tokenStorage: SecureTokenStorage

// Store access token (expires in 15 minutes)
tokenStorage.storeToken("access_token_here")

// Store refresh token (expires in 30 days)
tokenStorage.storeRefreshToken("refresh_token_here")

// Retrieve token (null if expired)
val token = tokenStorage.retrieveToken()
if (token == null) {
    // Token expired, refresh it
    val refreshToken = tokenStorage.retrieveRefreshToken()
    // Use refresh token to get new access token
}
```

---

### 17.2 Request Signing for Replay Protection

**Location:** `data/src/main/kotlin/com/healthtracker/data/security/RequestSigner.kt`

**Features:**
- HMAC-SHA256 signature generation
- Timestamp validation (±5 minute window)
- Cryptographically secure nonce generation
- Constant-time signature comparison (prevents timing attacks)

**Key Components:**
```kotlin
data class SyncRequest(
    val userId: String,
    val data: String,
    val endpoint: String
)

data class SignedRequest(
    val request: SyncRequest,
    val timestamp: Long,
    val nonce: String,
    val signature: String
)
```

**Key Methods:**
```kotlin
// Sign a request
fun signRequest(request: SyncRequest, timestamp: Long, nonce: String): SignedRequest

// Verify signature
fun verifySignature(signedRequest: SignedRequest, secretKey: String): Boolean

// Check timestamp freshness
fun isTimestampValid(timestamp: Long, windowSeconds: Long = 300): Boolean

// Generate secure nonce
fun generateNonce(): String
```

**Security Properties:**
- Message format: `userId|data|endpoint|timestamp|nonce`
- 32-byte cryptographically secure nonces
- Constant-time comparison prevents timing attacks
- 5-minute timestamp window prevents replay attacks

**Usage Example:**
```kotlin
@Inject lateinit var requestSigner: RequestSigner

// Create request
val request = SyncRequest(
    userId = "user123",
    data = "health_data_json",
    endpoint = "/api/sync"
)

// Sign request
val timestamp = System.currentTimeMillis() / 1000
val nonce = requestSigner.generateNonce()
val signedRequest = requestSigner.signRequest(request, timestamp, nonce)

// Server-side: Verify signature
val isValid = requestSigner.verifySignature(signedRequest, secretKey)
val isTimestampFresh = requestSigner.isTimestampValid(signedRequest.timestamp)

if (isValid && isTimestampFresh) {
    // Process request
} else {
    // Reject request
}
```

**Important Notes:**
- The secret key is currently a placeholder and MUST be replaced in production
- Server should maintain a nonce cache to prevent replay attacks
- Nonces should be stored for at least the timestamp window duration (5 minutes)

---

### 17.3 Model Integrity Verification

**Location:** `ml/src/main/kotlin/com/healthtracker/ml/ModelVerifier.kt`

**Features:**
- SHA-256 hash verification for ML models
- Prevents model tampering and poisoning attacks
- Detects corrupted model files
- Streaming hash computation for large files

**Key Methods:**
```kotlin
// Verify model integrity (simple boolean result)
suspend fun verifyModelIntegrity(modelFile: File, expectedHash: String): Boolean

// Verify with detailed result
suspend fun verifyModelIntegrityDetailed(
    modelFile: File, 
    expectedHash: String
): ModelVerificationResult

// Compute file hash
suspend fun computeFileHash(file: File): String

// Download and verify model (placeholder)
suspend fun downloadAndVerifyModel(
    modelUrl: String,
    expectedHash: String,
    destinationFile: File
): ModelVerificationResult
```

**Result Types:**
```kotlin
sealed class ModelVerificationResult {
    data class Success(val modelFile: File)
    data class HashMismatch(val expected: String, val actual: String)
    data class FileNotFound(val path: String)
    data class Error(val message: String, val cause: Throwable?)
}
```

**Security Properties:**
- SHA-256 cryptographic hash
- 8KB buffer for efficient streaming
- Case-insensitive hash comparison
- Automatic rejection of tampered models

**Usage Example:**
```kotlin
@Inject lateinit var modelVerifier: ModelVerifier

// Expected hash (should be stored in secure configuration)
val expectedHash = "abc123def456..." // SHA-256 hash of trusted model

// Verify model before loading
val modelFile = File(context.filesDir, "model.tflite")
val result = modelVerifier.verifyModelIntegrityDetailed(modelFile, expectedHash)

when (result) {
    is ModelVerificationResult.Success -> {
        // Load model safely
        val interpreter = Interpreter(result.modelFile)
    }
    is ModelVerificationResult.HashMismatch -> {
        // Model tampered! Re-download or alert
        Log.e("Security", "Model hash mismatch!")
    }
    is ModelVerificationResult.FileNotFound -> {
        // Download model
    }
    is ModelVerificationResult.Error -> {
        // Handle error
    }
}
```

**Best Practices:**
- Store expected hashes in Firebase Remote Config or secure backend
- Verify models on first use and after updates
- Re-download models if verification fails
- Log verification failures for security monitoring

---

### 17.4 Certificate Pinning

**Locations:**
- `data/src/main/kotlin/com/healthtracker/data/security/CertificatePinner.kt`
- `app/src/main/res/xml/network_security_config.xml`
- `app/src/main/AndroidManifest.xml`

**Features:**
- SHA-256 certificate pinning for Firebase domains
- TLS 1.3 enforcement
- Cleartext traffic blocking
- Backup pins for certificate rotation
- Debug overrides for localhost

**Pinned Domains:**
- `*.firebaseio.com` (Realtime Database)
- `*.firebaseapp.com` (Firebase App)
- `firestore.googleapis.com` (Firestore)
- `*.googleapis.com` (Firebase Storage and APIs)

**Key Methods:**
```kotlin
// Create secure OkHttpClient with pinning
fun createSecureClient(): OkHttpClient

// Get certificate pinner configuration
fun getCertificatePinner(): CertificatePinner
```

**Network Security Configuration:**
```xml
<network-security-config>
    <!-- Block cleartext traffic globally -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
    
    <!-- Pin Firebase certificates -->
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">firebaseio.com</domain>
        <pin-set expiration="2026-12-31">
            <pin digest="SHA-256">PRIMARY_PIN</pin>
            <pin digest="SHA-256">BACKUP_PIN</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

**Security Properties:**
- Multiple pins per domain (primary + backup)
- Pin expiration dates
- Cleartext traffic disabled
- Debug overrides for development

**Usage Example:**
```kotlin
@Inject lateinit var certificatePinner: CertificatePinnerManager

// Create secure HTTP client
val client = certificatePinner.createSecureClient()

// Use with Retrofit
val retrofit = Retrofit.Builder()
    .baseUrl("https://firebaseio.com/")
    .client(client)
    .build()
```

**CRITICAL: Certificate Pin Updates**

The current implementation uses **PLACEHOLDER** pins that MUST be replaced with actual Firebase certificate pins before production deployment.

**How to Get Real Certificate Pins:**

1. **Using OpenSSL:**
```bash
openssl s_client -connect firebaseio.com:443 | \
  openssl x509 -pubkey -noout | \
  openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | \
  openssl enc -base64
```

2. **Using SSL Labs:**
   - Visit: https://www.ssllabs.com/ssltest/
   - Enter domain: firebaseio.com
   - Get certificate details and public key hash

3. **Using Chrome DevTools:**
   - Open DevTools → Security tab
   - View certificate details
   - Extract public key hash

**Pin Update Locations:**
1. `CertificatePinner.kt` - Update constants
2. `network_security_config.xml` - Update pin digests
3. Firebase Remote Config - Store backup pins

**Certificate Rotation Strategy:**
- Always include 2+ pins per domain (primary + backup)
- Update pins before expiration (set expiration date in XML)
- Test pin updates in staging before production
- Monitor pin validation failures

---

## Security Checklist

### Before Production Deployment:

- [ ] Replace placeholder certificate pins with actual Firebase pins
- [ ] Replace placeholder secret key in RequestSigner
- [ ] Store expected model hashes in secure configuration
- [ ] Test token expiration and refresh flow
- [ ] Test certificate pinning with real Firebase connections
- [ ] Verify model integrity on first load
- [ ] Set up nonce cache on server for replay protection
- [ ] Configure Firebase Remote Config for feature flags
- [ ] Test security in staging environment
- [ ] Conduct security audit
- [ ] Set up monitoring for security events

### Monitoring & Alerts:

- [ ] Log token expiration events
- [ ] Alert on signature verification failures
- [ ] Alert on model hash mismatches
- [ ] Alert on certificate pinning failures
- [ ] Track nonce reuse attempts
- [ ] Monitor token refresh rates

### Testing:

- [ ] Unit tests for token expiration logic
- [ ] Unit tests for signature generation/verification
- [ ] Unit tests for model hash computation
- [ ] Integration tests for certificate pinning
- [ ] Test replay attack prevention
- [ ] Test token refresh flow
- [ ] Test model verification failure handling

---

## Security Threat Mitigations

| Threat | Mitigation | Implementation |
|--------|------------|----------------|
| Token leakage | Secure storage with expiration | SecureTokenStorage with Android Keystore |
| Replay attacks | Request signing with nonce | RequestSigner with HMAC-SHA256 |
| Model poisoning | Integrity verification | ModelVerifier with SHA-256 |
| MITM attacks | Certificate pinning | CertificatePinner + network_security_config.xml |
| Token theft | Short-lived tokens | 15-minute access token expiry |
| Timing attacks | Constant-time comparison | MessageDigest.isEqual() |

---

## Dependencies

All required dependencies are already included in `gradle/libs.versions.toml`:
- `security-crypto` - EncryptedSharedPreferences
- `okhttp` - Certificate pinning
- Standard Android security libraries

---

## References

- [Android Keystore System](https://developer.android.com/training/articles/keystore)
- [EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
- [Network Security Configuration](https://developer.android.com/training/articles/security-config)
- [Certificate Pinning](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/)
- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)

---

## Next Steps

1. Replace all placeholder values with production values
2. Set up server-side nonce validation
3. Configure Firebase Remote Config for model hashes
4. Implement token refresh flow in authentication layer
5. Add security monitoring and alerting
6. Conduct penetration testing
7. Document security incident response procedures
