package ml.docilealligator.infinityforreddit.thing;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import ml.docilealligator.infinityforreddit.apis.GqlAPI;
import ml.docilealligator.infinityforreddit.apis.GqlRequestBody;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;

/**
 * Created by alex on 3/14/18.
 */

public class VoteThing {

    public static boolean isPost(String id) {
        return id.startsWith("t3_");
    }

    public static void voteThing(Context context, final Retrofit gqlRetrofit, String accessToken,
                                 final VoteThingListener voteThingListener, final String fullName,
                                 final String point, final int position) {
        GqlAPI api = gqlRetrofit.create(GqlAPI.class);
        Call<String> voteThingCall;

        if (isPost(fullName)) {
            RequestBody body = GqlRequestBody.updatePostVoteStateBody(fullName, point);
            voteThingCall = api.updatePostVoteState(APIUtils.getOAuthHeader(accessToken), body);
        } else {
            RequestBody body = GqlRequestBody.updateCommentVoteStateBody(fullName, point);
            voteThingCall = api.updateCommentVoteState(APIUtils.getOAuthHeader(accessToken), body);
        }

        voteThingCall.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull retrofit2.Response<String> response) {
                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(response.body());
                        if (!json.isNull("errors")) {
                            voteThingListener.onVoteThingFail(position);
                            Toast.makeText(context, json.getJSONArray("errors").getJSONObject(0).getString("message"), Toast.LENGTH_LONG).show();
                            return;
                        }
                    } catch (Exception ignored) {
                    }
                    voteThingListener.onVoteThingSuccess(position);
                } else {
                    voteThingListener.onVoteThingFail(position);
                    Toast.makeText(context, "Code " + response.code() + " Body: " + response.body(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                voteThingListener.onVoteThingFail(position);
                Toast.makeText(context, "Network error " + "Body: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    public static void voteThing(Context context, final Retrofit gqlRetrofit, String accessToken,
                                 final VoteThingWithoutPositionListener voteThingWithoutPositionListener,
                                 final String fullName, final String point) {
        GqlAPI api = gqlRetrofit.create(GqlAPI.class);
        Call<String> voteThingCall;

        if (isPost(fullName)) {
            RequestBody body = GqlRequestBody.updatePostVoteStateBody(fullName, point);
            voteThingCall = api.updatePostVoteState(APIUtils.getOAuthHeader(accessToken), body);
        } else {
            RequestBody body = GqlRequestBody.updateCommentVoteStateBody(fullName, point);
            voteThingCall = api.updateCommentVoteState(APIUtils.getOAuthHeader(accessToken), body);
        }

        voteThingCall.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull retrofit2.Response<String> response) {
                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(response.body());
                        if (!json.isNull("errors")) {
                            voteThingWithoutPositionListener.onVoteThingFail();
                            Toast.makeText(context, json.getJSONArray("errors").getJSONObject(0).getString("message"), Toast.LENGTH_LONG).show();
                            return;
                        }
                    } catch (Exception ignored) {
                    }
                    voteThingWithoutPositionListener.onVoteThingSuccess();
                } else {
                    voteThingWithoutPositionListener.onVoteThingFail();
                    Toast.makeText(context, "Code " + response.code() + " Body: " + response.body(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                voteThingWithoutPositionListener.onVoteThingFail();
                Toast.makeText(context, "Network error " + "Body: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    public interface VoteThingListener {
        void onVoteThingSuccess(int position);

        void onVoteThingFail(int position);
    }

    public interface VoteThingWithoutPositionListener {
        void onVoteThingSuccess();

        void onVoteThingFail();
    }
}
