# ParsePost GQL Methods

## Purpose

Add GQL parsing support to `post/ParsePost.java` so that `PostPagingSource` (Layer 5) can
consume GraphQL feed responses alongside the existing REST path.

## Methods Added

### `parsePostsSyncGQL(response, nPosts, postFilter, readPostsList)`

Entry point for parsing a full page of GQL posts. Mirrors `parsePostsSync` for the REST path.

**Response shapes handled:**
- **Feed:** `data.postFeed.elements.edges[]`
- **Search:** `data.search.general.posts.edges[]`

Each `edge.node` is passed to `parseBasicDataGQL` when `__typename` is `SubredditPost` or
`ProfilePost`. Other types (ads, etc.) are silently skipped.

Returns `LinkedHashSet<Post>` (same as REST path) or `null` on parse failure.

---

### `getLastItemGQL(response)`

Returns the pagination cursor for the next page, or `null` if no more pages.

Reads `pageInfo.endCursor` + `pageInfo.hasNextPage` from:
- `data.postFeed.elements.pageInfo`
- `data.search.general.posts.pageInfo`

Returns `null` when `hasNextPage == false` or `endCursor` is null.

---

### `parseBasicDataGQL(data)` — public

Top-level GQL node parser. Extracts scalar fields, handles flair (richtext JSON-in-string),
awards, vote state, and preview images, then delegates post-type detection to `parseDataGQL`.

**Key GQL→Post field mappings:**

| GQL field | Post field | Notes |
|---|---|---|
| `id` (t3_xxx) | `fullName`, `id` | Strip `t3_` prefix for `id` |
| `permalink` | `subredditName`, `subredditNamePrefixed` | Parse from permalink segments |
| `authorInfo.name` | `author` | |
| `authorFlair.richtext` (JSON string) | `authorFlairHTML` | Parse as JSONArray |
| `authorFlair.text` | `authorFlair` | |
| `distinguishedAs` | `distinguished` | |
| `suggestedCommentSort` | `suggestedSort` | |
| `createdAt` (ISO8601) | `postTimeMillis` | Via `getUnixTime()` |
| `title` | `title` | |
| `score` | `score` | Nullable double → int |
| `commentCount` | `nComments` | |
| `upvoteRatio` | `upvoteRatio` | × 100 → int |
| `isHidden` | `hidden` | |
| `isSpoiler` | `spoiler` | |
| `isNsfw` | `nsfw` | |
| `isStickied` | `stickied` | |
| `isArchived` | `archived` | |
| `isLocked` | `locked` | |
| `isSaved` | `saved` | |
| `flair.richtext` (JSON string) | `flair` (HTML) | |
| `voteState` (`NONE`/`UP`/`DOWN`) | `voteType` (0/1/-1) | |
| `media.still.source` + resolutions | `previews` | Named sizes: small→xxxlarge |
| `media.thumbnail` | `previews` (fallback) | When `still` is null |
| `crosspostRoot.post` | cross-post | Recursively calls `parseBasicDataGQL` |

**Fields not available in GQL (defaults used):**
- `deleted` = `false`
- `removed` = `false`
- `sendReplies` = `false`
- `canModPost` = `false`
- `approved` = `false`
- `approvedAtUTC` = `0`
- `approvedBy` = `null`
- `spam` = `false`

Awards: GQL provides `awardings[]` but this repo's `Post` constructor has no award fields —
awards are silently omitted.

---

### `parseDataGQL(data, ...)` — private

Determines post type from GQL semantic hints instead of URL heuristics:

| Condition | Post type |
|---|---|
| `isSelfPost == true` | `TEXT_TYPE` |
| `postHint == "IMAGE"` | `IMAGE_TYPE` (or `VIDEO_TYPE` if `typeHint == "GIFVIDEO"`) |
| `postHint == "HOSTED_VIDEO"` or `"RICH_VIDEO"` | `VIDEO_TYPE` (or `LINK_TYPE` for YouTube/streamable) |
| `postHint == "LINK"` | `LINK_TYPE` / `NO_PREVIEW_LINK_TYPE` |
| `gallery != null` | `GALLERY_TYPE` |
| fallback | `LINK_TYPE` / `NO_PREVIEW_LINK_TYPE` |

Video source: `media.streaming.dashUrl` + `media.download.url`

Gallery source: `gallery.items[].media` with per-item resolutions.

Self-text: calls `setText()` when `content` is non-null.

---

### `setText(post, data)` — public static

Reads `content.markdown` + `content.html` from the GQL node and populates:
- `post.setSelfText(...)` — markdown with images resolved via `insertImages()`
- `post.setSelfTextPlain(...)` — from HTML
- `post.setSelfTextPlainTrimmed(...)` — first 250 chars; empty for spoiler-tagged posts

Called from `parseDataGQL` whenever `content != null`.

---

### `insertImages(content, richtextMedia)` — public static

Resolves `![img](placeholder)` syntax in GQL markdown by substituting actual URLs from the
`richtextMedia` array. Matches occurrences in order.

Pattern: `!\[img\]\([^)]*\)` → extract optional caption from placeholder, replace URL with
`richtextMedia[i].url`.

---

### `getUnixTime(timestamp)` — public static

Converts an ISO8601 timestamp string (e.g. `2024-01-15T10:30:00.000000+0000`) to unix
milliseconds. Uses `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ")`. Returns current
time on parse failure.

---

## Constructor Adaptation

This repo's `Post` constructors have extra moderation fields not in the fork:

```
sendReplies, canModPost, approved, approvedAtUTC, approvedBy, removed, spam
```

All GQL calls pass: `false, false, false, 0L, null, false, false` for these fields.

The `mediaMetadataMap` field used in REST's `parseData` is not used in GQL — GQL passes its
own embedded media inline.

## File Location

`app/src/main/java/ml/docilealligator/infinityforreddit/post/ParsePost.java`

## Dependencies

- `Post.java` — constructors at lines 88 and 132
- `Utils.modifyMarkdown`, `Utils.trimTrailingWhitespace`
- `JSONUtils` constants (`DATA_KEY`, `TITLE_KEY`, etc.)
- `PostFilter.isPostAllowed`
- `Html.fromHtml`, `Html.escapeHtml`
- `android.text.TextUtils`
- `java.text.SimpleDateFormat`
- `java.util.HashMap`, `java.util.regex.*`
