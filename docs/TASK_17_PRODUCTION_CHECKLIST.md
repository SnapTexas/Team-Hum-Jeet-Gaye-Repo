# Task 17: Production Deployment Checklist

## ⚠️ CRITICAL: Complete Before Production Deployment

This checklist MUST be completed before deploying the security hardening features to production.

---

## 1. Certificate Pinning Configuration

### Get Real Certificate Pins

**For each Firebase domain, obtain the certificate pins:**

```bash
# Firebase Realtime Database
openssl s_client -connect YOUR_PROJECT.firebaseio.com:443 | \
  openssl x509 -pubkey -noout | \
  openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | \
  openssl enc -base64

# Firestore
openssl s_client -connect firestore.googleapis.com:443 | \
  openssl x509 -pubkey -noout | \
  openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | \
  openssl enc -base64

# Firebase Storage
openssl s_client -connect storage.googleapis.com:443 | \
  openssl x509 -pubkey -noout | \
  openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | \
  openssl enc -base64
```

### Update Certificate Pins

- [ ] Update `data/src/main/kotlin/com/healthtracker/data/security/CertificatePinner.kt`
  - Replace `PLACEHOLDER_FIREBASE_PIN_1`
  - Replace `PLACEHOLDER_FIREBASE_PIN_2`
  - Replace `PLACEHOLDER_GOOGLE_ROOT_CA_1`
  - Replace `PLACEHOLDER_GOOGLE_ROOT_CA_2`

- [ ] Update `app/src/main/res/xml/network_security_config.xml`
  - Replace `PLACEHOLDER_FIREBASE_PIN_1_BASE64`
  - Replace `PLACEHOLDER_FIREBASE_PIN_2_BASE64`
  - Replace `PLACEHOLDER_GOOGLE_ROOT_CA_1_BASE64`
  - Replace `PLACEHOLDER_GOOGLE_ROOT_CA_2_BASE64`

- [ ] Set pin expiration dates (recommend 1 year from deployment)

- [ ] Test certificate pinning with real Firebase connections

---

## 2. Request Signing Secret Key

### Generate Secure Secret Key

```bash
# Generate a 256-bit secret key
openssl rand -base64 32
```

### Update Secret Key

- [ ] Update `data/src/main/kotlin/com/healthtracker/data/security/RequestSigner.kt`
  - Replace `PLACEHOLDER_SECRET_KEY_REPLACE_IN_PRODUCTION`
  - Use the generated secret key

- [ ] Store secret key securely on server
  - Use environment variables or secret management service
  - Never commit to version control

- [ ] Implement key rotation strategy
  - Plan for periodic key rotation (every 90 days recommended)
  - Support multiple keys during rotation period

---

## 3. Model Integrity Verification

### Generate Model Hashes

```bash
# For each ML model
sha256sum model.tflite
# Or on Windows:
certutil -hashfile model.tflite SHA256
```

### Configure Model Hashes

- [ ] Store expected model hashes in Firebase Remote Config
  - Key: `ml_model_food_classification_hash`
  - Key: `ml_model_anomaly_detection_hash`
  - Key: `ml_model_suggestion_engine_hash`

- [ ] Update model loading code to verify before use
  - Check `ml/src/main/kotlin/com/healthtracker/ml/OptimizedMLService.kt`
  - Add verification before `Interpreter` creation

- [ ] Set up model update pipeline
  - Generate hash when building new models
  - Update Remote Config with new hash
  - Test model verification in staging

---

## 4. Server-Side Implementation

### Nonce Cache Setup

- [ ] Implement nonce storage on server (Redis recommended)
  - Store nonces with 5-minute TTL
  - Check for nonce existence before processing request
  - Reject requests with reused nonces

### Request Validation Endpoint

- [ ] Implement signature verification on server
  - Extract timestamp, nonce, signature from request
  - Verify signature using shared secret key
  - Check timestamp is within ±5 minute window
  - Check nonce hasn't been used before
  - Return 401 Unauthorized if validation fails

### Example Server Code (Node.js)

```javascript
const crypto = require('crypto');
const redis = require('redis');

async function validateRequest(req) {
  const { userId, data, endpoint, timestamp, nonce, signature } = req.body;
  
  // Check timestamp
  const now = Math.floor(Date.now() / 1000);
  if (Math.abs(now - timestamp) > 300) {
    return { valid: false, reason: 'Timestamp expired' };
  }
  
  // Check nonce
  const nonceKey = `nonce:${nonce}`;
  const nonceExists = await redis.exists(nonceKey);
  if (nonceExists) {
    return { valid: false, reason: 'Nonce reused' };
  }
  
  // Verify signature
  const message = `${userId}|${data}|${endpoint}|${timestamp}|${nonce}`;
  const expectedSignature = crypto
    .createHmac('sha256', process.env.SECRET_KEY)
    .update(message)
    .digest('hex');
  
  if (signature !== expectedSignature) {
    return { valid: false, reason: 'Invalid signature' };
  }
  
  // Store nonce
  await redis.setex(nonceKey, 300, '1');
  
  return { valid: true };
}
```

---

## 5. Token Management

### Token Refresh Flow

- [ ] Implement token refresh endpoint on server
  - Accept refresh token
  - Validate refresh token
  - Issue new access token (15 min expiry)
  - Issue new refresh token (30 days expiry)

- [ ] Implement client-side token refresh logic
  - Check token expiration before API calls
  - Automatically refresh if expired
  - Retry failed requests after refresh
  - Handle refresh token expiration (force re-login)

### Example Client Code

```kotlin
suspend fun makeAuthenticatedRequest(endpoint: String): Result<Response> {
    var token = tokenStorage.retrieveToken()
    
    if (token == null) {
        // Token expired, try to refresh
        val refreshToken = tokenStorage.retrieveRefreshToken()
        if (refreshToken == null) {
            // Refresh token also expired, force re-login
            return Result.Error("Session expired, please login")
        }
        
        // Refresh access token
        val newTokens = authService.refreshToken(refreshToken)
        tokenStorage.storeToken(newTokens.accessToken)
        tokenStorage.storeRefreshToken(newTokens.refreshToken)
        token = newTokens.accessToken
    }
    
    // Make request with valid token
    return apiService.makeRequest(endpoint, token)
}
```

---

## 6. Testing

### Security Testing

- [ ] Test token expiration and refresh flow
  - Verify tokens expire at correct time
  - Verify refresh flow works correctly
  - Verify expired refresh token forces re-login

- [ ] Test request signing
  - Verify valid signatures are accepted
  - Verify invalid signatures are rejected
  - Verify tampered data is rejected
  - Verify old timestamps are rejected
  - Verify reused nonces are rejected

- [ ] Test model verification
  - Verify valid models load successfully
  - Verify tampered models are rejected
  - Verify missing models trigger download

- [ ] Test certificate pinning
  - Verify Firebase connections work
  - Verify MITM attacks are blocked
  - Test with Charles Proxy or similar tool

### Load Testing

- [ ] Test signature verification performance
  - Measure latency impact
  - Ensure <10ms overhead per request

- [ ] Test nonce cache performance
  - Measure Redis lookup time
  - Ensure cache doesn't become bottleneck

---

## 7. Monitoring & Alerting

### Set Up Monitoring

- [ ] Log security events
  - Token expiration events
  - Token refresh events
  - Signature verification failures
  - Model verification failures
  - Certificate pinning failures
  - Nonce reuse attempts

- [ ] Set up alerts
  - Alert on high rate of signature failures (>10/min)
  - Alert on model verification failures
  - Alert on certificate pinning failures
  - Alert on nonce reuse attempts

### Metrics to Track

- [ ] Token refresh rate
- [ ] Signature verification success rate
- [ ] Model verification success rate
- [ ] Certificate pinning success rate
- [ ] Average request signing overhead

---

## 8. Documentation

### Update Documentation

- [ ] Document token refresh flow for developers
- [ ] Document request signing for API consumers
- [ ] Document model update process
- [ ] Document certificate pin rotation process
- [ ] Document incident response procedures

### Security Audit

- [ ] Conduct internal security review
- [ ] Consider external penetration testing
- [ ] Document findings and remediation

---

## 9. Rollout Strategy

### Staged Rollout

- [ ] Deploy to development environment
  - Test all security features
  - Verify no breaking changes

- [ ] Deploy to staging environment
  - Test with production-like data
  - Conduct security testing
  - Verify monitoring and alerting

- [ ] Deploy to production (phased)
  - Start with 10% of users
  - Monitor for issues
  - Gradually increase to 100%

### Rollback Plan

- [ ] Document rollback procedure
- [ ] Test rollback in staging
- [ ] Prepare feature flags for quick disable
  - `enable_request_signing`
  - `enable_model_verification`
  - `enable_certificate_pinning`

---

## 10. Post-Deployment

### Verification

- [ ] Verify all security features are working
- [ ] Check monitoring dashboards
- [ ] Review logs for errors
- [ ] Verify no performance degradation

### Maintenance

- [ ] Schedule certificate pin rotation (before expiry)
- [ ] Schedule secret key rotation (every 90 days)
- [ ] Schedule security audit (quarterly)
- [ ] Keep dependencies updated

---

## Sign-Off

### Required Approvals

- [ ] Security Team Lead: _____________________ Date: _______
- [ ] Backend Team Lead: _____________________ Date: _______
- [ ] Mobile Team Lead: _____________________ Date: _______
- [ ] QA Lead: _____________________ Date: _______
- [ ] Product Manager: _____________________ Date: _______

---

## Emergency Contacts

- Security Team: security@healthtracker.com
- On-Call Engineer: oncall@healthtracker.com
- Incident Response: incident@healthtracker.com

---

## Additional Resources

- [Task 17 Implementation Guide](./TASK_17_SECURITY_HARDENING.md)
- [Security Implementation Summary](./SECURITY_IMPLEMENTATION_SUMMARY.md)
- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
