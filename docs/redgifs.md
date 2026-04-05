# Redgifs Integration

Redgifs is a video hosting service that Reddit embeds. When a post's URL points to `redgifs.com` or `i.redgifs.com`, the app must obtain a Redgifs bearer token and then exchange the GIF ID for a direct MP4 URL.

---

## Overview of the flow

```
Reddit post JSON
  +-- url / oembed thumbnail_url  ->  ParsePost extracts redgifsId
                                          |
                                          v
                                  FetchRedgifsVideoLinks
                                          |
                              +--------- check token ---------+
                              |                               |
                     token valid?                   GET /v2/auth/temporary
                     (in-memory)                             |
                              |                         token + expiry
                              |                         stored in memory
                              |                         + SharedPreferences
                              +---------------------------+---+
                                                          |
                                          GET /v2/gifs/{id}?user-agent=...
                                          Authorization: bearer <token>
                                                          |
                                                  gif.urls.hd  (or sd fallback)
                                                  strip "-silent" suffix if present
                                                          |
                                                     MP4 URL returned to player
```

---

## Step 1: ID extraction (`ParsePost.java`)

`ParsePost` extracts the Redgifs ID from two places in the Reddit JSON:

**From `url` field** (direct link):
```
https://www.redgifs.com/watch/somevideoname  ->  redgifsId = "somevideoname"
```

**From `media.oembed.thumbnail_url`** (embedded):
```
https://thumbs2.redgifs.com/SomeVideoName-mobile.jpg
  1. take substring after last "/"   ->  "SomeVideoName-mobile.jpg"
  2. strip suffix after last "-"     ->  "SomeVideoName"
  3. redgifsId = "SomeVideoName"
```

The ID is stored on the `Post` object via `post.setRedgifsId(redgifsId)`. At this point no network call is made to Redgifs.

---

## Step 2: Token acquisition (`/v2/auth/temporary`)

Before fetching the video URL the app checks whether a valid token is already in memory.

### Token cache (`APIUtils.java:193`)

```java
// In-memory cache -- process-scoped, survives across requests
public static final AtomicReference<RedgifsAuthToken> REDGIFS_TOKEN =
    new AtomicReference<>(new RedgifsAuthToken("", 0));

public static class RedgifsAuthToken {
    public final String token;
    private final long expireAt; // SystemClock.uptimeMillis()

    // Token is considered valid for 23 hours (1 hour leeway before actual 24h expiry)
    public static RedgifsAuthToken expireIn1day(String token) {
        long expireTime = 1000 * 60 * 60 * 23;
        return new RedgifsAuthToken(token, SystemClock.uptimeMillis() + expireTime);
    }

    public boolean isValid() {
        return !token.isEmpty() && expireAt > SystemClock.uptimeMillis();
    }
}
```

If `REDGIFS_TOKEN.get().isValid()` returns false, a new token is fetched:

### Token request

```
GET https://api.redgifs.com/v2/auth/temporary
User-Agent: android:ml.docilealligator.infinityforreddit:6.x.x (by /u/Hostilenemy)
```

No body, no credentials, no Authorization header. This is a fully public endpoint -- Redgifs issues temporary tokens to anyone.

Source: `RedgifsAPI.getRedgifsTemporaryToken()` (`RedgifsAPI.java:23`)

### Token response

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "addr": "...",
  "agent": "...",
  "session": "...",
  "rtfm": "..."
}
```

Key extracted: `"token"` (NOT `"access_token"` -- this is different from the Reddit token response).

After extraction the token is stored in two places:
- `APIUtils.REDGIFS_TOKEN` (AtomicReference, in-memory, used for the validity check)
- `SharedPreferences` under key `"redgifs_access_token"` (`SharedPreferencesUtils.REDGIFS_ACCESS_TOKEN`)

---

## Step 3: Video URL fetch (`/v2/gifs/{id}`)

### Request

```
GET https://api.redgifs.com/v2/gifs/{redgifsId}?user-agent=android:ml.docilealligator...
Authorization: bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
User-Agent: android:ml.docilealligator.infinityforreddit:6.x.x (by /u/Hostilenemy)
```

Note: `user-agent` also appears as a **query parameter** -- this is a Redgifs API quirk, separate from the HTTP `User-Agent` header. Both are sent.

Source: `RedgifsAPI.getRedgifsData()` (`RedgifsAPI.java:16`)

### Response (partial)

```json
{
  "gif": {
    "id": "somevideoname",
    "createDate": 1700000000,
    "hasAudio": true,
    "width": 1920,
    "height": 1080,
    "duration": 12.5,
    "urls": {
      "sd":  "https://media.redgifs.com/SomeVideoName-mobile.mp4",
      "hd":  "https://media.redgifs.com/SomeVideoName.mp4",
      "gif": "https://thumbs2.redgifs.com/SomeVideoName-small.gif"
    }
  }
}
```

### URL selection (`FetchRedgifsVideoLinks.java:195`)

```java
if (urls.has("hd")) {
    mp4 = urls.getString("hd");
} else if (urls.has("sd")) {
    mp4 = urls.getString("sd");
}

// Strip "-silent" suffix added by Redgifs for muted encodes
if (mp4.contains("-silent")) {
    mp4 = mp4.substring(0, mp4.indexOf("-silent")) + ".mp4";
    // "SomeVideo-silent.mp4" -> "SomeVideo.mp4"
}
```

HD is preferred; SD is the fallback. The `-silent` strip is needed because Redgifs sometimes appends `-silent` to the filename for audio-free versions; removing it produces the URL with audio.

---

## Token refresh (on 401/400)

The `RedgifsAccessTokenAuthenticator` is wired as an OkHttp **Interceptor** (not `Authenticator`) on the Redgifs `OkHttpClient`:

```
NetworkModule.provideRedgifsRetrofit()
  +-- OkHttpClient
       +-- Interceptor: sets User-Agent header on every request
       +-- Interceptor: RedgifsAccessTokenAuthenticator
```

Because it is an `Interceptor` it sees every response, including 401 and 400. On either:

1. Extracts the bearer token from the failed request's `Authorization` header
2. Compares it to what is in `SharedPreferences`
   - **Match** (this thread's token is stale): calls `/v2/auth/temporary`, stores new token, retries
   - **No match** (another thread already refreshed it): retries with the `SharedPreferences` token directly

The `synchronized` block prevents two threads from fetching a new token simultaneously.

---

## Old OAuth flow (dead code)

The commented-out class at the top of `RedgifsAccessTokenAuthenticator.java` used `POST /v2/oauth/client` with hardcoded credentials:

```
POST https://api.redgifs.com/v2/oauth/client
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
&client_id=1828d0bcc93-15ac-bde6-0005-d2ecbe8daab3
&client_secret=TJBlw7jRXW65NAGgFBtgZHu97WlzRXHYybK81sZ9dLM=
```

This is where `APIUtils.GRANT_TYPE_CLIENT_CREDENTIALS`, `REDGIFS_CLIENT_ID`, and `REDGIFS_CLIENT_SECRET` come from. The response would have returned `{"access_token": "..."}`. This flow was replaced by the unauthenticated `/v2/auth/temporary` endpoint, which requires no credentials and returns a token good for 24 hours.

---

## How to test

### 1. Get a temporary token

```bash
curl -s "https://api.redgifs.com/v2/auth/temporary" \
  | python3 -m json.tool
```

Expected: JSON with a `"token"` key containing a JWT string.

### 2. Fetch a GIF's video URLs

```bash
TOKEN=$(curl -s "https://api.redgifs.com/v2/auth/temporary" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

curl -s "https://api.redgifs.com/v2/gifs/tinycoloredaddax?user-agent=test" \
  -H "Authorization: bearer $TOKEN" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['gif']['urls'])"
```

Expected: dict with `hd` and/or `sd` keys pointing to `.mp4` URLs.

### 3. Confirm token rejection without auth

```bash
curl -s "https://api.redgifs.com/v2/gifs/tinycoloredaddax?user-agent=test"
```

Expected: `401` or a JSON error -- unlike the temporary token endpoint, the GIF data endpoint requires a bearer token.

### 4. Verify `-silent` stripping matters

```bash
# Some IDs return a "-silent" filename -- check with:
curl -s "https://api.redgifs.com/v2/gifs/{id}?user-agent=test" \
  -H "Authorization: bearer $TOKEN" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); urls=d['gif']['urls']; print(urls.get('hd') or urls.get('sd'))"
```

If the URL contains `-silent`, the audio version is at the same path without that segment.
