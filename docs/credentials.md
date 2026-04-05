# Credentials & Secrets Inventory

All hardcoded credentials in the source tree. Source of truth: `APIUtils.java` and `strings.xml`.

---

## Reddit

**File:** `APIUtils.java:36`

```
CLIENT_ID = "NOe2iKrPPzwscA"
CLIENT_SECRET = "" (empty -- installed app type, no secret)
```

**Fork / GQL reference client ID** (not in this repo's code):

```
CLIENT_ID = "ohXpoqrZYub1kg"
CLIENT_SECRET = "" (empty -- installed app type)
```

This is Reddit's official Android app client ID, used by the fork that this GQL port is based on. Not wired into the code here — kept for reference in case the GQL endpoint requires tokens issued under this client ID.

Reddit's OAuth flow for installed apps (mobile) does not issue a client secret. The client ID alone is sufficient. It is sent as HTTP Basic auth with an empty password:

```
Authorization: Basic base64("NOe2iKrPPzwscA:")
```

`getHttpBasicAuthHeader()` (`APIUtils.java:143`) constructs this at runtime.

**OAuth scopes** (`APIUtils.java:50`):
```
identity edit flair history modconfig modflair modlog modposts modwiki
mysubreddits privatemessages read report save submit subscribe vote
wikiedit wikiread creddits modcontributors modmail modothers livemanage
account modself
```

**Redirect URI** (`APIUtils.java:46`):
```
infinity://localhost
```

**OAuth state nonce** (`APIUtils.java:44`):
```
STATE = "23ro8xlxvzp4asqd"
```

This is a hardcoded CSRF protection nonce. It does not change between sessions. Normally this should be a random value per request; the hardcoded value means CSRF protection against the OAuth callback is nominal only.

---

## Imgur

**File:** `APIUtils.java:37`

```
IMGUR_CLIENT_ID = "cc671794e0ab397"
```

Sent as the full value of the `Authorization` header (the string already includes the `Client-ID` prefix):

```java
public static final String IMGUR_CLIENT_ID = "Client-ID cc671794e0ab397";
```

So the header on Imgur API requests is:
```
Authorization: Client-ID cc671794e0ab397
```

This is an anonymous Imgur client registration -- no secret, rate-limited by client ID.

---

## Redgifs

### Active token flow

No hardcoded credentials are used. The app calls `GET https://api.redgifs.com/v2/auth/temporary` (no auth required) and receives a temporary JWT. See `docs/redgifs.md` for the full flow.

### Dead OAuth credentials (commented-out code)

**File:** `APIUtils.java:38-39`, referenced in the commented-out class in `RedgifsAccessTokenAuthenticator.java`

```
REDGIFS_CLIENT_ID     = "1828d0bcc93-15ac-bde6-0005-d2ecbe8daab3"
REDGIFS_CLIENT_SECRET = "TJBlw7jRXW65NAGgFBtgZHu97WlzRXHYybK81sZ9dLM="
```

These were used with `POST /v2/oauth/client` (`grant_type=client_credentials`). The endpoint and the code path that sent them are both commented out. The constants remain in `APIUtils.java` but are not referenced by any live code path.

---

## Giphy

**File:** `APIUtils.java:40`

```
GIPHY_GIF_API_KEY = "" (empty string)
```

Giphy GIF support is not implemented -- the key is empty and the feature is disabled.

---

## Backup file password

**File:** `app/src/main/res/values/strings.xml:607`

```
password = "123321"
```

The app's settings backup/restore feature encrypts the export file with this hardcoded password. It is displayed to the user in the UI (`settings_backup_settings_summary`). Anyone with the backup file can decrypt it with this password.

---

## Reveddit spoofed headers

**File:** `APIUtils.java:123-125`

```
Origin:  https://www.reveddit.com
Referer: https://www.reveddit.com/
```

Not credentials, but the app spoofs these headers when calling the Reveddit API (`getRevedditHeader()`). This makes requests appear to originate from the Reveddit web frontend rather than a third-party app, likely to avoid rate limiting or API key requirements on Reveddit's side.

---

## Summary table

| Service | Credential | Value | Status |
|---------|-----------|-------|--------|
| Reddit | client_id | `NOe2iKrPPzwscA` | Active |
| Reddit (fork / GQL) | client_id | `ohXpoqrZYub1kg` | Reference only (official Android app ID) |
| Reddit | client_secret | `""` (none) | Active |
| Reddit | OAuth state nonce | `23ro8xlxvzp4asqd` | Active (hardcoded, not rotated) |
| Imgur | client_id | `cc671794e0ab397` | Active |
| Redgifs | client_id | `1828d0bcc93-15ac-bde6-0005-d2ecbe8daab3` | Dead (commented out) |
| Redgifs | client_secret | `TJBlw7jRXW65NAGgFBtgZHu97WlzRXHYybK81sZ9dLM=` | Dead (commented out) |
| Giphy | api_key | `""` (empty) | Not implemented |
| Backup | file password | `123321` | Active (shown in UI) |
