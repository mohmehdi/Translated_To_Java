

package io.plaidapp.designernews.data.api;

import io.plaidapp.core.data.api.EnvelopePayload;
import io.plaidapp.core.designernews.data.login.model.AccessToken;
import io.plaidapp.core.designernews.data.login.model.LoggedInUserResponse;
import io.plaidapp.core.designernews.data.poststory.model.NewStoryRequest;
import io.plaidapp.core.designernews.data.stories.model.Story;
import io.plaidapp.core.designernews.data.stories.model.StoryResponse;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.data.votes.model.UpvoteCommentRequest;
import io.plaidapp.core.designernews.data.votes.model.UpvoteStoryRequest;
import io.plaidapp.designernews.data.comments.model.CommentResponse;
import io.plaidapp.designernews.data.comments.model.NewCommentRequest;
import io.plaidapp.designernews.data.comments.model.PostCommentResponse;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.http.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface DNService {

    @EnvelopePayload("stories")
    @GET("api/v2/stories")
    Call<Response<List<StoryResponse>>> getStories(@Query("page") Integer page);

    @EnvelopePayload("stories")
    @GET("api/v2/stories/{ids}")
    Call<Response<List<StoryResponse>>> getStories(@Path("ids") String commaSeparatedIds);

    @EnvelopePayload("users")
    @GET("api/v2/users/{ids}")
    Call<Response<List<User>>> getUsers(@Path("ids") String userids);

    @EnvelopePayload("users")
    @GET("api/v2/me")
    Call<Response<List<LoggedInUserResponse>>> getAuthedUser();

    @FormUrlEncoded
    @POST("oauth/token")
    Call<Response<AccessToken>> login(@FieldMap Map<String, String> loginParams);

    @GET("search?t=story")
    Call<Response<List<String>>> search(@Query("q") String query, @Query("p") Integer page);

    @EnvelopePayload("story")
    @POST("api/v2/stories/{id}/upvote")
    Call<Story> upvoteStory(@Path("id") Long storyId);

    @POST("api/v2/upvotes")
    Call<Response<Void>> upvoteStoryV2(@Body RequestBody request);

    @EnvelopePayload("stories")
    @POST("api/v2/stories")
    Call<List<Story>> postStory(@Body NewStoryRequest story);

    @EnvelopePayload("comments")
    @GET("api/v2/comments/{ids}")
    Call<Response<List<CommentResponse>>> getComments(@Path("ids") String commentIds);

    @POST("api/v2/comments")
    Call<Response<PostCommentResponse>> comment(@Body RequestBody comment);

    @POST("api/v2/comment_upvotes")
    Call<Response<Void>> upvoteComment(@Body RequestBody request);

    public static final String ENDPOINT = "https:";
}