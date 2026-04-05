# NSFW Post Handling

## API vs Web: Why the "login wall" doesn't apply here

Reddit added a "NSFW requires login" wall on `reddit.com`, but this is a **web frontend restriction only** — a JavaScript modal overlay on the website. The API has never enforced it for any token type, including no token at all.

---

## How anonymous users get NSFW content

**There is no token exchange.** The app does not call `accounts.reddit.com/api/access_token` for anonymous users. `GRANT_TYPE_CLIENT_CREDENTIALS` exists as a constant in `APIUtils.java` but is only used for Redgifs — not for Reddit.

Anonymous users hit `www.reddit.com` directly with **no Authorization header**:

### Request (anonymous subreddit browse)

```
GET https://www.reddit.com/r/{subredditName}/{sortType}.json?raw_json=1&always_show_media=1&limit=25
User-Agent: ml.docilealligator.infinityforreddit:6.x.x (by /u/Hostilenemy)
```

- No `Authorization` header
- Base URL: `https://www.reddit.com` (`APIUtils.API_BASE_URI`)
- User-Agent: `APIUtils.ANONYMOUS_USER_AGENT` — note the lack of the `android:` prefix present in authenticated requests
- Source: `RedditAPI.getSubredditBestPostsListenableFuture()` (`RedditAPI.java:284`)

For the home/multireddit page the User-Agent is passed explicitly as a `@Header`; for regular subreddit browsing it comes from the OkHttp client's default interceptor.

### Response (partial — one NSFW post)

```json
{
  "kind": "Listing",
  "data": {
    "after": "t3_xxxxxx",
    "children": [
      {
        "kind": "t3",
        "data": {
          "subreddit": "nsfw",
          "title": "...",
          "over_18": true,
          "url": "https://i.redd.it/...",
          "is_video": false,
          "score": 1234,
          "author": "some_user",
          "id": "abcdef",
          "name": "t3_abcdef"
        }
      }
    ]
  }
}
```

Key field: `over_18: true`. This maps to `JSONUtils.NSFW_KEY` in `ParsePost.parseBasicData()`.

Reddit returns NSFW posts in this response for any public subreddit — no auth required, no client ID involved. The restriction is entirely in the web frontend.

---

## How to test this

### 1. Verify unauthenticated NSFW content works

```bash
curl -s -A "ml.docilealligator.infinityforreddit:6.1.0 (by /u/Hostilenemy)" \
  "https://www.reddit.com/r/nsfw.json?raw_json=1&always_show_media=1&limit=5" \
  | python3 -m json.tool | grep -E '"over_18"|"title"' | head -20
```

Expected: posts appear with `"over_18": true`. No 401 or 403.

### 2. Confirm no Authorization header is sent

```bash
curl -v -A "ml.docilealligator.infinityforreddit:6.1.0 (by /u/Hostilenemy)" \
  "https://www.reddit.com/r/nsfw.json?raw_json=1&limit=1" 2>&1 | grep -E "^> "
```

Expected: you will see `> User-Agent:` and `> Accept:` but **no** `> Authorization:` line.

### 3. Verify the same endpoint works with zero headers

```bash
curl -s "https://www.reddit.com/r/nsfw.json?raw_json=1&limit=1" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['children'][0]['data']['over_18'])"
```

Expected: `True`. This confirms the NSFW gate is purely web-side.

### 4. Check what happens with a bogus token

```bash
curl -s -H "Authorization: bearer fake_token_abc123" \
  "https://oauth.reddit.com/r/nsfw.json?raw_json=1&limit=1"
```

Expected: `{"message": "Unauthorized", "error": 401}`. The OAuth endpoint enforces valid tokens; the plain `.json` endpoint does not.

---

## Authenticated users

Authenticated users go through `oauth.reddit.com` with a `Bearer` token:

```
GET https://oauth.reddit.com/r/{subredditName}/{sortType}.json?raw_json=1&always_show_media=1
Authorization: bearer <access_token>
User-Agent: android:ml.docilealligator.infinityforreddit:6.x.x (by /u/Hostilenemy)
```

Note the `android:` prefix in the User-Agent and the `oauth.reddit.com` base URL. Source: `RedditAPI.getSubredditBestPostsOauthListenableFuture()`.

The `CLIENT_ID = "NOe2iKrPPzwscA"` (`APIUtils.java:36`) is relevant here — it determines which OAuth scopes and API features the token can access. Whether NSFW content is returned by the OAuth endpoint depends on the client ID's registered permissions with Reddit. A client ID without NSFW permissions would not receive NSFW posts from `oauth.reddit.com`. The anonymous path has no such gate because it has no client ID at all.

---

## GQL and NSFW

Anonymous users **do not use GQL** — they stay on the REST `.json` path described above. Only authenticated users are routed through GQL (`gql-fed.reddit.com`), and that endpoint returns NSFW content identically to the OAuth REST path.

---

## NSFW field mapping across paths

| Path | Endpoint | JSON field | Constant | Source method |
|------|----------|-----------|----------|---------------|
| Anonymous REST | `www.reddit.com/*.json` | `over_18` | `JSONUtils.NSFW_KEY` | `parseBasicData()` |
| Authenticated REST | `oauth.reddit.com/*.json` | `over_18` | `JSONUtils.NSFW_KEY` | `parseBasicData()` |
| Authenticated GQL | `gql-fed.reddit.com` | `isNsfw` | `JSONUtils.IS_NSFW_KEY` | `parseBasicDataGQL()` |

All three map to the `nsfw` boolean on the `Post` object. Downstream filtering (`PostFilter`, `NsfwAndSpoilerFragment` blur settings) applies uniformly regardless of which path fetched the post.
