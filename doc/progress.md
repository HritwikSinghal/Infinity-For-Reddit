# GQL Port Progress

## What & Why
Porting GraphQL support from `~/Projects/Infinity-For-Reddit` (fork) into `~/test/Infinity-For-Reddit` (this repo).
Reddit's API changes broke REST for some operations. The fork added GQL via `gql-fed.reddit.com`.
**Pattern:** authenticated users -> GQL; anonymous users -> REST (dual-path).
No new library deps -- GQL is plain Retrofit + manual JSON.

**Branch:** `gql`
**Fork reference:** `~/Projects/Infinity-For-Reddit/app/src/main/java/ml/docilealligator/infinityforreddit/`

---

## Critical Adaptation Notes
These bite you if forgotten when writing new code:

| Fork pattern | This repo pattern |
|---|---|
| `Post` constructor has `awards, nAwards` | This repo has NO `awards` fields |
| `Post` constructor lacks mod fields | This repo adds: `sendReplies, canModPost, approved, approvedAtUTC, approvedBy, removed, spam` |
| GQL: pass `false, false, false, 0L, null, false, false` for those mod fields | -- |
| `SortType` in root pkg | `thing.SortType` |
| No `CommentFilter` in fork | `CommentFilter` exists here -- GQL path skips it, REST keeps it |
| `accessToken == null` = anon | Use `accountName.equals(Account.ANONYMOUS_ACCOUNT)` |
| `FetchComment` takes `authorName` | This repo only has `accountName` -- need to add `authorName` param |
| `SubredditSubscription` takes `subredditId` | This repo doesn't -- add param, get from `SubredditData.getId()` |
| `FetchSubredditData(oauthRetrofit, retrofit)` | Current is `(retrofit, accessToken)` -- add GQL retrofit param |

---

## Layer Status

### [DONE] Layer 1 -- New Files
- [x] 1.1 `apis/GqlAPI.java` -- Retrofit interface, 16 `@POST("/")` methods
- [x] 1.2 `apis/GqlRequestBody.java` -- request body builders + persisted query hashes

### [DONE] Layer 2 -- Infrastructure
- [x] 2.1 `utils/APIUtils.java` -- added `GQL_BASE_URL`, `VOTESTATE_*`, `ACTION_SUB/UNSUB`
- [x] 2.2 `NetworkModule.java` -- added `@Named("gql") Retrofit` provider

### [IN PROGRESS] Layer 3 -- Parsers

#### [DONE] 3.1 `post/ParsePost.java`
Added: `parsePostsSyncGQL`, `getLastItemGQL`, `parseBasicDataGQL`, `parseDataGQL`, `setText`, `insertImages`, `getUnixTime`
- Feed shape: `data.postFeed.elements.edges[]`
- Search shape: `data.search.general.posts.edges[]`
- Post type dispatch via `isSelfPost` / `postHint` (IMAGE, HOSTED_VIDEO, RICH_VIDEO, LINK) / `gallery`
- No gfycat (dead) -- redgifs handled via inline regex `redgifs\.com/watch/([a-z]+)`
- Imports added: `ParseException`, `SimpleDateFormat`, `Date`, `HashMap`
- Reference: fork `post/ParsePost.java` lines 77-1322

#### [ ] 3.2 `comment/ParseComment.java`
Methods to add:
- `parseCommentGQL(executor, handler, response, authorName, expandChildren, listener)` -- entry, parses `data.postInfoById.commentForest.trees[]`
- `parseMoreCommentGQL(...)` -- "load more" responses
- `parseCommentRecursionGQL(comments, newComments, moreChildrenIds, postId, subredditName, authorName)` -- flat tree, `__typename` discrimination (Comment / DeletedComment / more-cursor)
- `parseMoreCommentRecursionGQL(...)` -- same for pagination
- `parseSingleCommentGQL(data, postId, subredditName, authorName)` -- GQL node -> Comment object
- `createDeletedComment(data, id, postId, subredditName)` -- placeholder for deleted comments
- Note: Skip `CommentFilter` in GQL path (REST path keeps it)
- Reference: fork `comment/ParseComment.java` lines 61-719

#### [ ] 3.3 `subreddit/ParseSubredditData.java`
- Add `parseSubredditDataSingle()` -- GQL single subreddit (`data.subredditInfoByName`, nested `styles` for icon/banner)
- Modify `ParseSubredditDataAsyncTask.doInBackground()` -- parse GQL format
- Modify `ParseSubredditListingDataAsyncTask` -- parse `data.search.general.communities.edges[]` and `data.search.typeaheadByType.subreddits[]`
- Reference: fork `subreddit/ParseSubredditData.java`

---

### [IN PROGRESS] Layer 4 -- Fetch/Routing

#### [DONE] 4.3 `thing/VoteThing.java`
Switched from REST `RedditAPI.voteThing()` to GQL `GqlAPI.updatePostVoteState()` / `updateCommentVoteState()`

#### [ ] 4.1 `comment/FetchComment.java`
- Add GQL path in `fetchComments()`: when `accessToken != null && commentId == null` -> `gqlAPI.getPostComments()` -> `ParseComment.parseCommentGQL()`
- Add GQL path in `fetchMoreComment()`: when authenticated -> GQL "load more"
- Add `createCommentVariables(id, sortType, afterKey)` -- builds `PostComments` GQL body
- Add `createExtensionsObject()` helper
- Signature change: add `authorName` param (needed for `isSubmitter` detection)
- Reference: fork `comment/FetchComment.java`

#### [ ] 4.2 `subreddit/FetchSubredditData.java`
- `fetchSubredditData()`: authenticated -> `GqlAPI.getSubredditData()` + `GqlRequestBody.subredditDataBody()`
- `fetchSubredditListingData()`: -> `GqlAPI.searchSubreddit()` + `GqlRequestBody.subredditSearchBody()`
- Signature change: add GQL retrofit param alongside existing retrofit
- Callers to update: `SidebarFragment`, `ViewSubredditDetailActivity`, `SubredditSubscription`
- Reference: fork `subreddit/FetchSubredditData.java`

#### [ ] 4.4 `subreddit/SubredditSubscription.java`
- Switch `subredditSubscription()` to `GqlAPI.subredditSubscription()` + `GqlRequestBody.subscribeBody()`
- Signature change: add `subredditId` param (GQL uses internal ID, not name)
- Callers: `SubredditListingRecyclerViewAdapter`, `ViewSubredditDetailActivity`
- Reference: fork `subreddit/SubredditSubscription.java`

---

### [ ] Layer 5 -- PostPagingSource + ViewModel

#### [ ] 5.1 `post/PostPagingSource.java`
- Add `Retrofit gqlRetrofit` to all 4 constructors
- Add `transformDataGQL(Response<String>)` -- calls `parsePostsSyncGQL` + `getLastItemGQL`
- Add GQL body builders: `createHomePostsVars()`, `createSubredditPostsVars()`, `createUserPostsVariables()`, `createSearchPostsVars()`
- Modify `loadFuture()` -- create `GqlAPI` from `gqlRetrofit`, pass to load methods
- Modify `loadHomePosts()` -- GQL when authenticated (`getBestPostsListenableFuture`)
- Modify `loadSubredditPosts()` -- GQL except r/popular, r/all (`getSubredditBestPostsOauthListenableFuture`)
- Modify `loadUserPosts()` -- GQL when `where == submitted` (`getUserPostsOauthListenableFuture`)
- Modify `loadSearchPosts()` -- GQL when authenticated, except u_ searches (`searchPostsOauthListenableFuture`)
- GQL operation hashes: Home=`fc940c3f`, Subreddit=`dc87cffd`, User=`6b5c7d94`, Search=`0d65275f`
- Reference: fork `post/PostPagingSource.java`

#### [ ] 5.2 `post/PostViewModel.java`
- Add `Retrofit gqlRetrofit` field
- Pass to all `PostPagingSource` constructor calls
- Update all `Factory` inner classes to accept `gqlRetrofit`
- Reference: fork `post/PostViewModel.java`

---

### [ ] Layer 6 -- DI Injection (`@Named("gql") Retrofit` threading)

#### Adapters
- [ ] `adapters/PostRecyclerViewAdapter.java` -- VoteThing calls
- [ ] `adapters/PostDetailRecyclerViewAdapter.java` -- VoteThing calls
- [ ] `adapters/CommentsRecyclerViewAdapter.java` -- VoteThing + FetchComment
- [ ] `adapters/CommentsListingRecyclerViewAdapter.java` -- VoteThing
- [ ] `adapters/SubredditListingRecyclerViewAdapter.java` -- SubredditSubscription

#### Fragments
- [ ] `fragments/PostFragment.java` -- PostViewModel creation
- [ ] `fragments/ViewPostDetailFragment.java` -- FetchComment calls
- [ ] `fragments/CommentsListingFragment.java`
- [ ] `fragments/SubredditListingFragment.java`
- [ ] `fragments/SidebarFragment.java` -- FetchSubredditData
- [ ] `fragments/HistoryPostFragment.java` -- PostViewModel creation

#### Activities
- [ ] `activities/ViewSubredditDetailActivity.java` -- SubredditSubscription + FetchSubredditData
- [ ] `activities/SearchActivity.java` -- PostViewModel for search
- [ ] Audit remaining activities that create PostViewModels

---

## Suggested Next Tasks (in order)

1. 3.2 `ParseComment.java` -- self-contained parser, no callers yet
2. 3.3 `ParseSubredditData.java` -- self-contained parser
3. 4.1 `FetchComment.java` -- needs 3.2 done first
4. 4.2 `FetchSubredditData.java` -- needs 3.3 done first
5. 4.4 `SubredditSubscription.java` -- standalone, easy
6. 5.1 + 5.2 `PostPagingSource` + `PostViewModel` -- needs 3.1 done (done)
7. Layer 6 -- DI threading, do last once all logic layers compile

## Build Note
JDK toolchain mismatch: `/usr/lib/jvm/java-21-openjdk` lacks `JAVA_COMPILER` capability.
Pre-existing env issue. Fix: install JDK 17 or adjust `compileOptions` in `app/build.gradle`.
