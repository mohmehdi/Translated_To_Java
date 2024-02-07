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

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface DNService {

    @EnvelopePayload("stories")
    @GET("api/v2/stories")
    CompletableFuture<Response<List<StoryResponse>>> getStories(@Query("page") Integer page);

    @EnvelopePayload("stories")
    @GET("api/v2/stories/{ids}")
    CompletableFuture<Response<List<StoryResponse>>> getStories(@Path("ids") String commaSeparatedIds);

    @EnvelopePayload("users")
    @GET("api/v2/users/{ids}")
    CompletableFuture<Response<List<User>>> getUsers(@Path("ids") String userids);

    @EnvelopePayload("users")
    @GET("api/v2/me")
    CompletableFuture<Response<List<LoggedInUserResponse>>> getAuthedUser();

    @FormUrlEncoded
    @POST("oauth/token")
    CompletableFuture<Response<AccessToken>> login(@FieldMap Map<String, String> loginParams);

    @GET("search?t=story")
    CompletableFuture<Response<List<String>>> search(
            @Query("q") String query,
            @Query("p") Integer page
    );

    @EnvelopePayload("story")
    @POST("api/v2/stories/{id}/upvote")
    Call<Story> upvoteStory(@Path("id") Long storyId);

    @Headers("Content-Type: application/vnd.api+json")
    @POST("api/v2/upvotes")
    CompletableFuture<Response<Void>> upvoteStoryV2(@Body RequestBody request);

    @EnvelopePayload("stories")
    @Headers("Content-Type: application/vnd.api+json")
    @POST("api/v2/stories")
    Call<List<Story>> postStory(@Body NewStoryRequest story);

    @EnvelopePayload("comments")
    @GET("api/v2/comments/{ids}")
    CompletableFuture<Response<List<CommentResponse>>> getComments(@Path("ids") String commentIds);

    @Headers("Content-Type: application/vnd.api+json")
    @POST("api/v2/comments")
    CompletableFuture<Response<PostCommentResponse>> comment(@Body RequestBody comment);

    @Headers("Content-Type: application/vnd.api+json")
    @POST("api/v2/comment_upvotes")
    CompletableFuture<Response<Void>> upvoteComment(@Body RequestBody request);

    companion object {
        final String ENDPOINT = "https:";
    }
}