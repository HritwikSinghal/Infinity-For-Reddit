package ml.docilealligator.infinityforreddit.comment;

import static ml.docilealligator.infinityforreddit.comment.Comment.VOTE_TYPE_DOWNVOTE;
import static ml.docilealligator.infinityforreddit.comment.Comment.VOTE_TYPE_NO_VOTE;
import static ml.docilealligator.infinityforreddit.comment.Comment.VOTE_TYPE_UPVOTE;

import android.os.Handler;
import android.text.Html;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import ml.docilealligator.infinityforreddit.commentfilter.CommentFilter;
import ml.docilealligator.infinityforreddit.post.ParsePost;
import ml.docilealligator.infinityforreddit.thing.MediaMetadata;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;

public class ParseComment {
    public static void parseComment(Executor executor, Handler handler, String response,
                                    boolean expandChildren, CommentFilter commentFilter,
                                    ParseCommentListener parseCommentListener) {
        executor.execute(() -> {
            try {
                JSONArray childrenArray = new JSONArray(response);
                String parentId = childrenArray.getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY)
                        .getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY).getString(JSONUtils.NAME_KEY);
                childrenArray = childrenArray.getJSONObject(1).getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY);

                ArrayList<Comment> expandedNewComments = new ArrayList<>();
                ArrayList<String> moreChildrenIds = new ArrayList<>();
                ArrayList<Comment> newComments = new ArrayList<>();

                parseCommentRecursion(childrenArray, newComments, moreChildrenIds, 0, commentFilter);
                expandChildren(newComments, expandedNewComments, expandChildren);

                ArrayList<Comment> commentData;
                if (expandChildren) {
                    commentData = expandedNewComments;
                } else {
                    commentData = newComments;
                }

                handler.post(() -> parseCommentListener.onParseCommentSuccess(newComments, commentData, parentId, moreChildrenIds));
            } catch (JSONException e) {
                e.printStackTrace();
                handler.post(parseCommentListener::onParseCommentFailed);
            }
        });
    }

    static void parseMoreComment(Executor executor, Handler handler, String response, boolean expandChildren,
                                 ParseCommentListener parseCommentListener) {
        executor.execute(() -> {
            try {
                JSONArray childrenArray = new JSONObject(response).getJSONObject(JSONUtils.JSON_KEY)
                        .getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.THINGS_KEY);

                ArrayList<Comment> newComments = new ArrayList<>();
                ArrayList<Comment> expandedNewComments = new ArrayList<>();
                ArrayList<String> moreChildrenIds = new ArrayList<>();

                // api response is a flat list of comments tree
                // process it in order and rebuild the tree
                for (int i = 0; i < childrenArray.length(); i++) {
                    JSONObject child = childrenArray.getJSONObject(i);
                    JSONObject childData = child.getJSONObject(JSONUtils.DATA_KEY);
                    if (child.getString(JSONUtils.KIND_KEY).equals(JSONUtils.KIND_VALUE_MORE)) {
                        String parentFullName = childData.getString(JSONUtils.PARENT_ID_KEY);
                        JSONArray childrenIds = childData.getJSONArray(JSONUtils.CHILDREN_KEY);

                        if (childrenIds.length() != 0) {
                            ArrayList<String> localMoreChildrenIds = new ArrayList<>(childrenIds.length());
                            for (int j = 0; j < childrenIds.length(); j++) {
                                localMoreChildrenIds.add(childrenIds.getString(j));
                            }

                            Comment parentComment = findCommentByFullName(newComments, parentFullName);
                            if (parentComment != null) {
                                parentComment.setHasReply(true);
                                parentComment.setMoreChildrenIds(localMoreChildrenIds);
                                parentComment.addChildren(new ArrayList<>()); // ensure children list is not null
                            } else {
                                // assume that it is parent of this call
                                moreChildrenIds.addAll(localMoreChildrenIds);
                            }
                        } else {
                            Comment continueThreadPlaceholder = new Comment(
                                    parentFullName,
                                    childData.getInt(JSONUtils.DEPTH_KEY),
                                    Comment.PLACEHOLDER_CONTINUE_THREAD
                            );

                            Comment parentComment = findCommentByFullName(newComments, parentFullName);
                            if (parentComment != null) {
                                parentComment.setHasReply(true);
                                parentComment.addChild(continueThreadPlaceholder, parentComment.getChildCount());
                                parentComment.setChildCount(parentComment.getChildCount() + 1);
                            } else {
                                // assume that it is parent of this call
                                newComments.add(continueThreadPlaceholder);
                            }
                        }
                    } else {
                        try {
                            Comment comment = parseSingleComment(childData, 0);
                            String parentFullName = comment.getParentId();

                            Comment parentComment = findCommentByFullName(newComments, parentFullName);
                            if (parentComment != null) {
                                parentComment.setHasReply(true);
                                parentComment.addChild(comment, parentComment.getChildCount());
                                parentComment.setChildCount(parentComment.getChildCount() + 1);
                            } else {
                                // assume that it is parent of this call
                                newComments.add(comment);
                            }
                        } catch (JSONException e) {
                            // Well we need to catch and ignore the exception to not show "error loading comments" to users
                            e.printStackTrace();
                        }
                    }
                }

                updateChildrenCount(newComments);
                expandChildren(newComments, expandedNewComments, expandChildren);

                ArrayList<Comment> commentData;
                if (expandChildren) {
                    commentData = expandedNewComments;
                } else {
                    commentData = newComments;
                }

                handler.post(() -> parseCommentListener.onParseCommentSuccess(newComments, commentData, null, moreChildrenIds));
            } catch (JSONException e) {
                e.printStackTrace();
                handler.post(parseCommentListener::onParseCommentFailed);
            }
        });
    }

    static void parseSentComment(Executor executor, Handler handler, String response, int depth,
                                 ParseSentCommentListener parseSentCommentListener) {
        executor.execute(() -> {
            try {
                JSONObject sentCommentData = new JSONObject(response);
                Comment comment = parseSingleComment(sentCommentData, depth);

                handler.post(() -> parseSentCommentListener.onParseSentCommentSuccess(comment));
            } catch (JSONException e) {
                e.printStackTrace();
                String errorMessage = parseSentCommentErrorMessage(response);
                handler.post(() -> parseSentCommentListener.onParseSentCommentFailed(errorMessage));
            }
        });
    }

    public static void parseCommentGQL(Executor executor, Handler handler, String response, String authorName,
                                       boolean expandChildren,
                                       ParseCommentListener parseCommentListener) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject(response).getJSONObject("data");

                String postId = data.getJSONObject("postInfoById").getString("id");
                String postType = data.getJSONObject("postInfoById").getString("__typename");
                String subredditName;
                if (postType.equals("ProfilePost")) {
                    subredditName = data.getJSONObject("postInfoById").getJSONObject("profile").getJSONObject("redditorInfo").getString("name");
                } else {
                    subredditName = data.getJSONObject("postInfoById").getJSONObject("subreddit").getString("name");
                }

                JSONArray childrenArray = data.getJSONObject("postInfoById").getJSONObject("commentForest").getJSONArray("trees");

                ArrayList<Comment> expandedNewComments = new ArrayList<>();
                ArrayList<Comment> newComments = new ArrayList<>();
                ArrayList<String> moreChildrenIds = new ArrayList<>();

                parseCommentRecursionGQL(childrenArray, newComments, moreChildrenIds, postId, subredditName, authorName);
                expandChildren(newComments, expandedNewComments, expandChildren);

                ArrayList<Comment> commentData;
                if (expandChildren) {
                    commentData = expandedNewComments;
                } else {
                    commentData = newComments;
                }

                handler.post(() -> parseCommentListener.onParseCommentSuccess(newComments, commentData, postId, moreChildrenIds));
            } catch (JSONException e) {
                e.printStackTrace();
                handler.post(parseCommentListener::onParseCommentFailed);
            }
        });
    }

    static void parseMoreCommentGQL(Executor executor, Handler handler, String response, boolean expandChildren,
                                    String authorName, ParseCommentListener parseCommentListener) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject(response).getJSONObject("data");

                String postId = data.getJSONObject("postInfoById").getString("id");
                String postType = data.getJSONObject("postInfoById").getString("__typename");
                String subredditName;
                if (postType.equals("ProfilePost")) {
                    subredditName = data.getJSONObject("postInfoById").getJSONObject("profile").getJSONObject("redditorInfo").getString("name");
                } else {
                    subredditName = data.getJSONObject("postInfoById").getJSONObject("subreddit").getString("name");
                }
                JSONArray childrenArray = data.getJSONObject("postInfoById").getJSONObject("commentForest").getJSONArray("trees");

                ArrayList<Comment> newComments = new ArrayList<>();
                ArrayList<Comment> expandedNewComments = new ArrayList<>();
                ArrayList<String> moreChildrenIds = new ArrayList<>();

                parseMoreCommentRecursionGQL(childrenArray, newComments, moreChildrenIds, postId, subredditName, authorName);

                updateChildrenCount(newComments);
                expandChildren(newComments, expandedNewComments, expandChildren);

                ArrayList<Comment> commentData;
                if (expandChildren) {
                    commentData = expandedNewComments;
                } else {
                    commentData = newComments;
                }

                handler.post(() -> parseCommentListener.onParseCommentSuccess(newComments, commentData, null, moreChildrenIds));
            } catch (JSONException e) {
                e.printStackTrace();
                handler.post(parseCommentListener::onParseCommentFailed);
            }
        });
    }

    private static void parseCommentRecursionGQL(JSONArray comments, ArrayList<Comment> newCommentData,
                                                 ArrayList<String> moreChildrenIds, String postId,
                                                 String subredditName, String authorName) throws JSONException {
        if (comments.length() == 0) {
            return;
        }

        JSONObject last = comments.getJSONObject(comments.length() - 1);
        int actualCommentLength;

        if (!last.isNull("more")) {
            moreChildrenIds.add(last.getJSONObject("more").getString("cursor"));
            actualCommentLength = comments.length() - 1;

            if (moreChildrenIds.isEmpty() && !comments.getJSONObject(comments.length() - 1).isNull("more")) {
                newCommentData.add(new Comment(last.getString("parentId"), last.getInt(JSONUtils.DEPTH_KEY), Comment.PLACEHOLDER_CONTINUE_THREAD));
                return;
            }
        } else {
            actualCommentLength = comments.length();
        }

        HashMap<String, Comment> commentMap = new HashMap<>();
        JSONObject lastDeleted = new JSONObject();

        for (int i = 0; i < actualCommentLength; i++) {
            JSONObject data = comments.getJSONObject(i);
            boolean isHiddenChild = data.isNull("node");
            boolean isVisibleChild = !data.isNull("parentId") && !data.isNull("node");

            if (isHiddenChild) {
                String parentId = data.getString("parentId");
                Comment parentComment = commentMap.get(parentId);
                if (parentComment == null) {
                    continue;
                }
                String cursor = data.getJSONObject("more").getString("cursor");
                parentComment.addMoreChildrenId(cursor);
            } else if (isVisibleChild) {
                String parentId = data.getString("parentId");
                if (!commentMap.containsKey(parentId)) {
                    Comment deletedComment = createDeletedComment(lastDeleted, parentId, postId, subredditName);
                    commentMap.put(parentId, deletedComment);
                    if (deletedComment.getDepth() > 0) {
                        Comment grandParent = commentMap.get(deletedComment.getParentId());
                        if (grandParent != null) {
                            grandParent.addChildEnd(deletedComment);
                        }
                    } else {
                        newCommentData.add(deletedComment);
                    }
                }

                if (data.getJSONObject("node").getString("__typename").equals("DeletedComment")) {
                    lastDeleted = data;
                    continue;
                }

                String id = data.getJSONObject("node").getString("id");
                Comment singleComment = parseSingleCommentGQL(data, postId, subredditName, authorName);
                commentMap.put(id, singleComment);
                Comment parentComment = commentMap.get(parentId);
                if (parentComment != null) {
                    parentComment.addChildEnd(singleComment);
                }
            } else {
                if (data.getJSONObject("node").getString("__typename").equals("DeletedComment")) {
                    lastDeleted = data;
                    continue;
                }

                String id = data.getJSONObject("node").getString("id");
                Comment singleComment = parseSingleCommentGQL(data, postId, subredditName, authorName);
                commentMap.put(id, singleComment);
                newCommentData.add(singleComment);
            }
        }
    }

    private static void parseMoreCommentRecursionGQL(JSONArray comments, ArrayList<Comment> newCommentData,
                                                     ArrayList<String> moreChildrenIds, String postId,
                                                     String subredditName, String authorName) throws JSONException {
        if (comments.length() == 0) {
            return;
        }

        int actualCommentLength = comments.length();
        int topDepth = comments.getJSONObject(0).getInt(JSONUtils.DEPTH_KEY);
        HashMap<String, Comment> commentMap = new HashMap<>();
        JSONObject lastDeleted = new JSONObject();

        for (int i = 0; i < actualCommentLength; i++) {
            JSONObject data = comments.getJSONObject(i);
            boolean isHiddenChild = data.isNull("node");
            boolean isVisibleChild = !data.isNull("parentId") && !data.isNull("node");

            if (isHiddenChild) {
                String parentId = data.getString("parentId");
                Comment parentComment = commentMap.get(parentId);
                if (parentComment == null) {
                    continue;
                }
                String cursor = data.getJSONObject("more").getString("cursor");
                parentComment.addMoreChildrenId(cursor);
            } else if (isVisibleChild) {
                String parentId = data.getString("parentId");
                boolean isTopLevel = topDepth == data.getInt(JSONUtils.DEPTH_KEY);

                if (!isTopLevel && !commentMap.containsKey(parentId)) {
                    Comment deletedComment = createDeletedComment(lastDeleted, parentId, postId, subredditName);
                    commentMap.put(parentId, deletedComment);
                    if (deletedComment.getDepth() > topDepth) {
                        Comment grandParent = commentMap.get(deletedComment.getParentId());
                        if (grandParent != null) {
                            grandParent.addChildEnd(deletedComment);
                        }
                    } else {
                        newCommentData.add(deletedComment);
                    }
                }

                if (data.getJSONObject("node").getString("__typename").equals("DeletedComment")) {
                    lastDeleted = data;
                    continue;
                }

                String id = data.getJSONObject("node").getString("id");
                Comment singleComment = parseSingleCommentGQL(data, postId, subredditName, authorName);
                commentMap.put(id, singleComment);
                if (isTopLevel) {
                    newCommentData.add(singleComment);
                } else {
                    Comment parentComment = commentMap.get(parentId);
                    if (parentComment != null) {
                        parentComment.addChildEnd(singleComment);
                    }
                }
            } else {
                if (data.getJSONObject("node").getString("__typename").equals("DeletedComment")) {
                    lastDeleted = data;
                    continue;
                }

                String id = data.getJSONObject("node").getString("id");
                Comment singleComment = parseSingleCommentGQL(data, postId, subredditName, authorName);
                commentMap.put(id, singleComment);
                newCommentData.add(singleComment);
            }
        }
    }

    static Comment parseSingleCommentGQL(JSONObject singleCommentData, String postId, String subredditName,
                                         String authorName) throws JSONException {
        JSONObject node = singleCommentData.getJSONObject("node");

        boolean isRemoved = node.getBoolean("isRemoved");
        String id = node.getString(JSONUtils.ID_KEY).substring(3);
        String fullName = node.getString(JSONUtils.ID_KEY);
        String author = "[deleted]";
        String authorIconUrl = "";

        if (!node.isNull("authorInfo")) {
            JSONObject authorObj = node.getJSONObject("authorInfo");
            String authorType = authorObj.getString("__typename");
            if (authorType.equals("UnavailableRedditor") || authorType.equals("DeletedRedditor")) {
                double r = Math.ceil(Math.random() * 7);
                authorIconUrl = String.format("https://www.redditstatic.com/avatars/defaults/v2/avatar_default_%d.png", (int) r);
            } else {
                authorIconUrl = authorObj.getJSONObject("iconSmall").getString(JSONUtils.URL_KEY);
            }
            author = authorObj.getString("name");
        }

        JSONObject authorFlairObj = node.isNull("authorFlair") ? null : node.getJSONObject("authorFlair");
        StringBuilder authorFlairHTMLBuilder = new StringBuilder();
        if (authorFlairObj != null && !authorFlairObj.isNull("richtext")) {
            JSONArray flairArray = new JSONArray(authorFlairObj.getString("richtext"));
            for (int i = 0; i < flairArray.length(); i++) {
                JSONObject flairObject = flairArray.getJSONObject(i);
                String e = flairObject.getString(JSONUtils.E_KEY);
                if (e.equals("text")) {
                    authorFlairHTMLBuilder.append(Html.escapeHtml(flairObject.getString(JSONUtils.T_KEY)));
                } else if (e.equals("emoji")) {
                    authorFlairHTMLBuilder.append("<img src=\"").append(Html.escapeHtml(flairObject.getString(JSONUtils.U_KEY))).append("\">");
                }
            }
        }
        String authorFlair = authorFlairObj == null ? "" : authorFlairObj.getString("text");

        String linkId = postId.substring(3);
        String parentId = postId;
        if (!singleCommentData.isNull("parentId")) {
            parentId = singleCommentData.getString("parentId");
        }

        boolean isSubmitter = author.equals(authorName);
        String distinguished = node.isNull("distinguishedAs") ? null : node.getString("distinguishedAs").toLowerCase();
        String commentMarkdown = "";
        String commentRawText = "";
        if (!node.isNull("content")) {
            JSONObject content = node.getJSONObject("content");
            String body = content.getString("markdown");
            commentMarkdown = Utils.modifyMarkdown(Utils.trimTrailingWhitespace(body));
            if (!isRemoved) {
                JSONArray richtextMedia = content.getJSONArray("richtextMedia");
                for (int i = 0; i < richtextMedia.length(); i++) {
                    JSONObject mediaObj = richtextMedia.getJSONObject(i);
                    String typename = mediaObj.getString("__typename");
                    if (typename.equals("ImageAsset")) {
                        String mediaId = mediaObj.getString("id");
                        String mediaUrl = mediaObj.getString(JSONUtils.URL_KEY);
                        commentMarkdown = commentMarkdown.replace(mediaId, mediaUrl);
                    } else if (typename.equals("ExpressionMediaAsset")) {
                        String mediaId = mediaObj.getString("id");
                        commentMarkdown = commentMarkdown.replace(String.format("![img](%s)", mediaId),
                                "*This comment contains a Collectible Expression which are not available on old Reddit.*\n");
                    }
                }
            }
            commentRawText = Utils.trimTrailingWhitespace(Html.fromHtml(content.getString("html"))).toString();
        }

        String permalink = Html.fromHtml(node.getString(JSONUtils.PERMALINK_KEY)).toString();
        int score = node.getInt(JSONUtils.SCORE_KEY);
        String voteState = node.getString("voteState");
        int voteType;
        if (voteState.equals("UP")) {
            voteType = VOTE_TYPE_UPVOTE;
        } else if (voteState.equals("DOWN")) {
            voteType = VOTE_TYPE_DOWNVOTE;
        } else {
            voteType = VOTE_TYPE_NO_VOTE;
        }
        if (voteType != VOTE_TYPE_NO_VOTE) {
            score -= voteType;
        }

        long submitTime = ParsePost.getUnixTime(node.getString("createdAt"));
        boolean scoreHidden = node.getBoolean("isScoreHidden");
        boolean saved = node.getBoolean("isSaved");
        int depth = singleCommentData.getInt(JSONUtils.DEPTH_KEY);
        boolean collapsed = node.getBoolean("isInitiallyCollapsed");
        boolean hasReply = singleCommentData.getInt("childCount") > 0;
        long edited = 0;
        if (!node.isNull("editedAt")) {
            edited = ParsePost.getUnixTime(node.getString("editedAt"));
        }

        Comment newComment = new Comment(id, fullName, author, "", authorFlair, authorFlairHTMLBuilder.toString(),
                null, submitTime, commentMarkdown, commentRawText,
                linkId, subredditName, parentId, score, voteType, isSubmitter, distinguished,
                permalink, depth, collapsed, hasReply, scoreHidden, saved,
                false, false, false, false, 0L, null, isRemoved, false, edited, null);
        newComment.addChildren(new ArrayList<>());
        newComment.setAuthorIconUrl(authorIconUrl);
        return newComment;
    }

    private static Comment createDeletedComment(JSONObject data, String id, String postId,
                                                String subredditName) throws JSONException {
        String linkId = postId.substring(3);
        String parentId = data.isNull("parentId") ? postId : data.getString("parentId");
        long createdAt = data.isNull("node") ? 0 : ParsePost.getUnixTime(data.getJSONObject("node").getString("createdAt"));
        int depth = data.optInt(JSONUtils.DEPTH_KEY, 0);
        int childCount = data.optInt("childCount", 0);

        Comment newComment = new Comment(id, "deleted", "[deleted]", "", "", "",
                null, createdAt, "[deleted]", "[deleted]",
                linkId, subredditName, parentId, 0, VOTE_TYPE_NO_VOTE, false, null,
                "", depth, true, childCount > 0,
                true, false, false, false, false, false, 0L, null, false, false, 0, null);
        newComment.addChildren(new ArrayList<>());
        newComment.setAuthorIconUrl("https://www.redditstatic.com/avatars/defaults/v2/avatar_default_1.png");
        return newComment;
    }

    private static void parseCommentRecursion(JSONArray comments, ArrayList<Comment> newCommentData,
                                              ArrayList<String> moreChildrenIds, int depth,
                                              CommentFilter commentFilter) throws JSONException {
        int actualCommentLength;

        if (comments.length() == 0) {
            return;
        }

        JSONObject more = comments.getJSONObject(comments.length() - 1).getJSONObject(JSONUtils.DATA_KEY);

        //Maybe moreChildrenIds contain only commentsJSONArray and no more info
        if (more.has(JSONUtils.COUNT_KEY)) {
            JSONArray childrenArray = more.getJSONArray(JSONUtils.CHILDREN_KEY);

            for (int i = 0; i < childrenArray.length(); i++) {
                moreChildrenIds.add(childrenArray.getString(i));
            }

            actualCommentLength = comments.length() - 1;

            if (moreChildrenIds.isEmpty() && comments.getJSONObject(comments.length() - 1).getString(JSONUtils.KIND_KEY).equals(JSONUtils.KIND_VALUE_MORE)) {
                newCommentData.add(new Comment(more.getString(JSONUtils.PARENT_ID_KEY), more.getInt(JSONUtils.DEPTH_KEY), Comment.PLACEHOLDER_CONTINUE_THREAD));
                return;
            }
        } else {
            actualCommentLength = comments.length();
        }

        for (int i = 0; i < actualCommentLength; i++) {
            JSONObject data = comments.getJSONObject(i).getJSONObject(JSONUtils.DATA_KEY);
            Comment singleComment = parseSingleComment(data, depth);
            boolean isFilteredOut = false;
            if (!CommentFilter.isCommentAllowed(singleComment, commentFilter)) {
                if (commentFilter.displayMode == CommentFilter.DisplayMode.REMOVE_COMMENT) {
                    continue;
                }

                isFilteredOut = true;
            }

            if (data.get(JSONUtils.REPLIES_KEY) instanceof JSONObject) {
                JSONArray childrenArray = data.getJSONObject(JSONUtils.REPLIES_KEY)
                        .getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY);
                ArrayList<Comment> children = new ArrayList<>();
                ArrayList<String> nextMoreChildrenIds = new ArrayList<>();
                parseCommentRecursion(childrenArray, children, nextMoreChildrenIds, singleComment.getDepth(),
                        commentFilter);
                singleComment.addChildren(children);
                singleComment.setMoreChildrenIds(nextMoreChildrenIds);
                singleComment.setChildCount(getChildCount(singleComment));
            }

            singleComment.setIsFilteredOut(isFilteredOut);
            newCommentData.add(singleComment);
        }
    }

    private static int getChildCount(Comment comment) {
        if (comment.getChildren() == null) {
            return 0;
        }
        int count = 0;
        for (Comment c : comment.getChildren()) {
            count += getChildCount(c);
        }
        return comment.getChildren().size() + count;
    }

    private static void expandChildren(ArrayList<Comment> comments, ArrayList<Comment> visibleComments,
                                       boolean setExpanded) {
        for (Comment c : comments) {
            visibleComments.add(c);
            if (!c.isFilteredOut()) {
                if (c.hasReply()) {
                    if (setExpanded) {
                        c.setExpanded(true);
                    }
                    expandChildren(c.getChildren(), visibleComments, setExpanded);
                } else {
                    c.setExpanded(true);
                }
            }
            if (c.hasMoreChildrenIds() && !c.getMoreChildrenIds().isEmpty()) {
                //Add a load more placeholder
                Comment placeholder = new Comment(c.getFullName(), c.getDepth() + 1, Comment.PLACEHOLDER_LOAD_MORE_COMMENTS);
                if (!c.isFilteredOut()) {
                    visibleComments.add(placeholder);
                }
                c.addChild(placeholder, c.getChildren().size());
            }
        }
    }

    public static Comment parseSingleComment(JSONObject singleCommentData, int depth) throws JSONException {
        String id = singleCommentData.getString(JSONUtils.ID_KEY);
        String fullName = singleCommentData.getString(JSONUtils.NAME_KEY);
        String author = singleCommentData.getString(JSONUtils.AUTHOR_KEY);

        String authorFullname = "";

        if (singleCommentData.has(JSONUtils.AUTHOR_FULLNAME_KEY)) {
            authorFullname = singleCommentData.getString(JSONUtils.AUTHOR_FULLNAME_KEY);
        }

        StringBuilder authorFlairHTMLBuilder = new StringBuilder();
        if (singleCommentData.has(JSONUtils.AUTHOR_FLAIR_RICHTEXT_KEY)) {
            JSONArray flairArray = singleCommentData.getJSONArray(JSONUtils.AUTHOR_FLAIR_RICHTEXT_KEY);
            for (int i = 0; i < flairArray.length(); i++) {
                JSONObject flairObject = flairArray.getJSONObject(i);
                String e = flairObject.getString(JSONUtils.E_KEY);
                if (e.equals("text")) {
                    authorFlairHTMLBuilder.append(Html.escapeHtml(flairObject.getString(JSONUtils.T_KEY)));
                } else if (e.equals("emoji")) {
                    authorFlairHTMLBuilder.append("<img src=\"").append(Html.escapeHtml(flairObject.getString(JSONUtils.U_KEY))).append("\">");
                }
            }
        }
        String authorFlair = singleCommentData.isNull(JSONUtils.AUTHOR_FLAIR_TEXT_KEY) ? "" : singleCommentData.getString(JSONUtils.AUTHOR_FLAIR_TEXT_KEY);
        String linkAuthor = singleCommentData.has(JSONUtils.LINK_AUTHOR_KEY) ? singleCommentData.getString(JSONUtils.LINK_AUTHOR_KEY) : null;
        String linkId = singleCommentData.getString(JSONUtils.LINK_ID_KEY).substring(3);
        String subredditName = singleCommentData.getString(JSONUtils.SUBREDDIT_KEY);
        String parentId = singleCommentData.getString(JSONUtils.PARENT_ID_KEY);
        boolean isSubmitter = singleCommentData.getBoolean(JSONUtils.IS_SUBMITTER_KEY);
        String distinguished = singleCommentData.getString(JSONUtils.DISTINGUISHED_KEY);
        Map<String, MediaMetadata> mediaMetadataMap = JSONUtils.parseMediaMetadata(singleCommentData);
        String commentMarkdown = "";
        if (!singleCommentData.isNull(JSONUtils.BODY_KEY)) {
            commentMarkdown = Utils.parseRedditImagesBlock(
                    Utils.modifyMarkdown(
                    Utils.trimTrailingWhitespace(singleCommentData.getString(JSONUtils.BODY_KEY))), mediaMetadataMap);
        }
        String commentRawText = Utils.trimTrailingWhitespace(
                Html.fromHtml(singleCommentData.getString(JSONUtils.BODY_HTML_KEY))).toString();
        String permalink = Html.fromHtml(singleCommentData.getString(JSONUtils.PERMALINK_KEY)).toString();
        int score = singleCommentData.getInt(JSONUtils.SCORE_KEY);
        int voteType;
        if (singleCommentData.isNull(JSONUtils.LIKES_KEY)) {
            voteType = VOTE_TYPE_NO_VOTE;
        } else {
            voteType = singleCommentData.getBoolean(JSONUtils.LIKES_KEY) ? VOTE_TYPE_UPVOTE : VOTE_TYPE_DOWNVOTE;
            score -= voteType;
        }
        long submitTime = singleCommentData.getLong(JSONUtils.CREATED_UTC_KEY) * 1000;
        boolean scoreHidden = singleCommentData.getBoolean(JSONUtils.SCORE_HIDDEN_KEY);
        boolean saved = singleCommentData.getBoolean(JSONUtils.SAVED_KEY);
        boolean sendReplies = singleCommentData.getBoolean(JSONUtils.SEND_REPLIES_KEY);
        boolean locked = singleCommentData.getBoolean(JSONUtils.LOCKED_KEY);
        boolean canModComment = singleCommentData.getBoolean(JSONUtils.CAN_MOD_POST_KEY);
        boolean approved = singleCommentData.has(JSONUtils.APPROVED_KEY) && singleCommentData.getBoolean(JSONUtils.APPROVED_KEY);
        long approvedAtUTC = singleCommentData.has(JSONUtils.APPROVED_AT_UTC_KEY) ? (singleCommentData.isNull(JSONUtils.APPROVED_AT_UTC_KEY) ? 0 : singleCommentData.getLong(JSONUtils.APPROVED_AT_UTC_KEY) * 1000) : 0;
        String approvedBy = singleCommentData.has(JSONUtils.APPROVED_BY_KEY) ? singleCommentData.getString(JSONUtils.APPROVED_BY_KEY) : null;
        boolean removed = singleCommentData.has(JSONUtils.REMOVED_KEY) && singleCommentData.getBoolean(JSONUtils.REMOVED_KEY);
        boolean spam = singleCommentData.has(JSONUtils.SPAM_KEY) && singleCommentData.getBoolean(JSONUtils.SPAM_KEY);

        if (singleCommentData.has(JSONUtils.DEPTH_KEY)) {
            depth = singleCommentData.getInt(JSONUtils.DEPTH_KEY);
        }

        boolean collapsed = singleCommentData.getBoolean(JSONUtils.COLLAPSED_KEY);
        boolean hasReply = !(singleCommentData.get(JSONUtils.REPLIES_KEY) instanceof String);

        // this key can either be a bool (false) or a long (edited timestamp)
        long edited = singleCommentData.optLong(JSONUtils.EDITED_KEY) * 1000;

        return new Comment(id, fullName, author, authorFullname, authorFlair, authorFlairHTMLBuilder.toString(),
                linkAuthor, submitTime, commentMarkdown, commentRawText,
                linkId, subredditName, parentId, score, voteType, isSubmitter, distinguished,
                permalink, depth, collapsed, hasReply, scoreHidden, saved, sendReplies, locked, canModComment,
                approved, approvedAtUTC, approvedBy, removed, spam, edited, mediaMetadataMap);
    }

    @Nullable
    private static String parseSentCommentErrorMessage(String response) {
        try {
            JSONObject responseObject = new JSONObject(response).getJSONObject(JSONUtils.JSON_KEY);

            if (responseObject.getJSONArray(JSONUtils.ERRORS_KEY).length() != 0) {
                JSONArray error = responseObject.getJSONArray(JSONUtils.ERRORS_KEY)
                        .getJSONArray(responseObject.getJSONArray(JSONUtils.ERRORS_KEY).length() - 1);
                if (error.length() != 0) {
                    String errorString;
                    if (error.length() >= 2) {
                        errorString = error.getString(1);
                    } else {
                        errorString = error.getString(0);
                    }
                    return errorString.substring(0, 1).toUpperCase() + errorString.substring(1);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Nullable
    private static Comment findCommentByFullName(@NonNull List<Comment> comments, @NonNull String fullName) {
        for (Comment comment: comments) {
            if (comment.getFullName().equals(fullName) &&
                    comment.getPlaceholderType() == Comment.NOT_PLACEHOLDER) {
                return comment;
            }
            if (comment.getChildren() != null) {
                Comment result = findCommentByFullName(comment.getChildren(), fullName);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static void updateChildrenCount(@NonNull List<Comment> comments) {
        for (Comment comment: comments) {
            comment.setChildCount(getChildCount(comment));
            if (comment.getChildren() != null) {
                updateChildrenCount(comment.getChildren());
            }
        }
    }

    public interface ParseCommentListener {
        void onParseCommentSuccess(ArrayList<Comment> topLevelComments, ArrayList<Comment> expandedComments, String parentId,
                                   ArrayList<String> moreChildrenIds);

        void onParseCommentFailed();
    }

    interface ParseSentCommentListener {
        void onParseSentCommentSuccess(Comment comment);

        void onParseSentCommentFailed(@Nullable String errorMessage);
    }
}
