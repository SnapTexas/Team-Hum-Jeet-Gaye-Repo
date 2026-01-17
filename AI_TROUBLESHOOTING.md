# AI Avatar Troubleshooting Guide

## Problem: OpenAI aur Edge TTS response nahi de rahe

### Fixes Applied:

1. **OpenAI Model Fixed**
   - Changed from `gpt-5-nano` (doesn't exist) to `gpt-4o-mini` (working model)
   - Added better error messages for API issues

2. **Edge TTS Improved**
   - Updated headers for better compatibility
   - Added detailed logging
   - Fallback to Android TTS if Edge fails

3. **Better Error Messages**
   - API key issues clearly shown
   - Network errors properly handled
   - Rate limit messages added

### How to Test:

#### 1. Check API Key
```bash
# Open local.properties and verify OPENAI_API_KEY is present
# It should start with "sk-proj-" or "sk-"
```

#### 2. Rebuild the App
```bash
# Clean and rebuild to ensure BuildConfig is updated
gradlew clean
gradlew assembleDebug
```

#### 3. Check Logcat for Errors
```bash
# Filter for AI service logs
adb logcat | findstr "OpenAIService EdgeTTSService"
```

### Common Issues:

#### Issue 1: "API key not configured"
**Solution:** 
- Check `local.properties` has `OPENAI_API_KEY=sk-...`
- Rebuild the app after adding key
- Make sure no spaces around the `=` sign

#### Issue 2: "Invalid API key"
**Solution:**
- Verify API key is valid on OpenAI dashboard
- Check if key has expired
- Ensure key has proper permissions

#### Issue 3: "Rate limit exceeded"
**Solution:**
- Wait a few minutes before trying again
- Check OpenAI usage limits on dashboard
- Consider upgrading OpenAI plan if needed

#### Issue 4: Edge TTS not working
**Solution:**
- App will automatically fallback to Android TTS
- Check internet connection
- Edge TTS is optional, OpenAI should still work

### Testing Steps:

1. **Open AI Chat Screen**
   - Type: "Hello, how are you?"
   - Should get response within 3-5 seconds
   - Should hear voice (if TTS working)

2. **Check Logs**
   ```
   Look for:
   ✅ "API key loaded successfully"
   ✅ "Response received, parsing..."
   ✅ "Response generated successfully"
   
   ❌ "API key is EMPTY"
   ❌ "API error: 401"
   ❌ "Failed to generate response"
   ```

3. **Test Different Queries**
   - "How many steps today?"
   - "Give me health advice"
   - "What should I eat?"

### Debug Commands:

```bash
# View real-time logs
adb logcat -s OpenAIService EdgeTTSService AIChatViewModel

# Clear app data and restart
adb shell pm clear com.healthtracker
adb shell am start -n com.healthtracker/.presentation.MainActivity

# Check if API key is in BuildConfig
# After building, check: app/build/generated/source/buildConfig/debug/com/healthtracker/BuildConfig.java
```

### What Changed:

**OpenAIService.kt:**
- Model: `gpt-5-nano` → `gpt-4o-mini` ✅
- Better error messages with user-friendly text
- Improved logging for debugging

**EdgeTTSService.kt:**
- Enhanced headers for Edge API
- Better error handling
- Detailed logging at each step
- Automatic fallback to Android TTS

**AIChatViewModel.kt:**
- Better error messages with emojis
- Detailed logging for debugging
- Proper error categorization

### Expected Behavior:

1. **User types message** → Shows in chat
2. **Loading indicator** → Shows while waiting
3. **AI responds** → Text appears in chat
4. **Voice speaks** → Hears response (optional)

### If Still Not Working:

1. Check internet connection
2. Verify API key is valid
3. Look at logcat for specific errors
4. Try rebuilding: `gradlew clean assembleDebug`
5. Check OpenAI dashboard for API status

### Contact Points:

- OpenAI API Status: https://status.openai.com/
- Check API usage: https://platform.openai.com/usage
- API key management: https://platform.openai.com/api-keys
