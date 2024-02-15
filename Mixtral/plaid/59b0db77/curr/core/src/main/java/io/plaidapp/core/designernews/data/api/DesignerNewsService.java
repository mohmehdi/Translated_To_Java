

package io.plaidapp.core.designernews.data.api;

import io.plaidapp.core.data.api.EnvelopePayload;
import io.plaidapp.core.designernews.data.login.model.AccessToken;
import io.plaidapp.core.designernews.data.login.model.LoggedInUserResponse;
import io.plaidapp.core.designernews.data.poststory.model.NewStoryRequest;
import io.plaidapp.core.designernews.data.stories.model.Story;
import io.plaidapp.core.designernews.data.stories.model.StoryResponse;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.data.votes.model.UpvoteCommentRequest;
import io.plaidapp.core.designernews.data.votes.model.UpvoteStoryRequest;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.Map;
import java.util.List;

public interface DesignerNewsService {

    @EnvelopePayload("stories")
    @GET("api/v2/stories")
    retrofit2.Call<List<StoryResponse>> getStories(@Query("page") Integer page);

    @EnvelopePayload("stories")
    @GET("api/v2/stories/{ids}")
    retrofit2.Call<List<StoryResponse>> getStories(@Path("ids") String commaSeparatedIds);

    @EnvelopePayload("users")
    @GET("api/v2/users/{ids}")
    retrofit2.Call<List<User>> getUsers(@Path("ids") String userids);

    @EnvelopePayload("users")
    @GET("api/v2/me")
    retrofit2.Call<List<LoggedInUserResponse>> getAuthedUser();

    @FormUrlEncoded
    @POST("oauth/token")
    retrofit2.Call<AccessToken> login(@FieldMap Map<String, String> loginParams);

    @GET("search?t=story")
    retrofit2.Call<List<String>> search(
            @Query("q") String query,
            @Query("p") Integer page);

    @EnvelopePayload("story")
    @POST("api/v2/stories/{id}/upvote")
    Call<Story> upvoteStory(@Path("id") Long storyId);

    @Headers("Content-Type: application/vnd.api+json")
    @POST("api/v2/upvotes")
    retrofit2.Call<Void> upvoteStoryV2(@Body UpvoteStoryRequest request);

    @EnvelopePayload("stories")
    @Headers("Content-Type: application/vnd.api+json")
    @POST("api/v2/stories")
    Call<List<Story>> postStory(@Body NewStoryRequest story);

    @Headers("Content-Type: application/vnd.api+json")
    @POST("api/v2/comment_upvotes")
    retrofit2.Call<Void> upvoteComment(@Body UpvoteCommentRequest request);

    public static final String ENDPOINT = "https:";
}