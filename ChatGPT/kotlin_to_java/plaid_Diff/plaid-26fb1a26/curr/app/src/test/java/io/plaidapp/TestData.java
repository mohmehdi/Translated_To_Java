package io.plaidapp;

import io.plaidapp.core.designernews.data.DesignerNewsSearchSourceItem;
import io.plaidapp.core.designernews.data.stories.model.Story;
import io.plaidapp.core.designernews.data.stories.model.StoryLinks;
import io.plaidapp.core.dribbble.data.DribbbleSourceItem;
import io.plaidapp.core.dribbble.data.api.model.Images;
import io.plaidapp.core.dribbble.data.api.model.Shot;
import io.plaidapp.core.dribbble.data.api.model.User;
import io.plaidapp.core.producthunt.data.api.model.Post;
import io.plaidapp.core.ui.filter.SourceUiModel;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

public class TestData {

    public static final DesignerNewsSearchSourceItem designerNewsSource = new DesignerNewsSearchSourceItem(
            "query",
            true
    );
    public static final SourceUiModel designerNewsSourceUiModel = new SourceUiModel(
            "id",
            designerNewsSource.getKey(),
            designerNewsSource.getName(),
            designerNewsSource.isActive(),
            designerNewsSource.getIconRes(),
            designerNewsSource.isSwipeDismissable(),
            () -> {},
            () -> {}
    );
    public static final DribbbleSourceItem dribbbleSource = new DribbbleSourceItem("dribbble", true);

    public static final Post post = new Post(
            345L,
            "Plaid",
            "www.plaid.amazing",
            "amazing",
            "www.disc.plaid",
            "www.d.plaid",
            5,
            100
    );

    public static final User player = new User(
            1L,
            "Nick Butcher",
            "nickbutcher",
            "www.prettyplaid.nb"
    );

    public static final Shot shot = new Shot(
            1L,
            "Foo Nick",
            "",
            new Images(),
            player
    );
    static {
        shot.setDataSource(dribbbleSource.getKey());
    }

    public static final long userId = 5L;
    public static final long storyId = 1345L;
    public static final Date createdDate = new GregorianCalendar(2018, 1, 13).getTime();
    public static final List<Long> commentIds = List.of(11L, 12L);
    public static final StoryLinks storyLinks = new StoryLinks(
            userId,
            commentIds,
            List.of(),
            List.of()
    );

    public static final Story story = new Story(
            storyId,
            "Plaid 2.0 was released",
            createdDate,
            userId,
            storyLinks
    );
    static {
        story.setDataSource(designerNewsSource.getKey());
    }
}