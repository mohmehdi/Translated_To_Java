




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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

public class TestData {
    public static final DesignerNewsSearchSourceItem designerNewsSource =
            new DesignerNewsSearchSourceItem(
                    "query",
                    true
            );

    public static final SourceUiModel designerNewsSourceUiModel =
            new SourceUiModel(
                    "id",
                    designerNewsSource.getKey(),
                    designerNewsSource.getName(),
                    designerNewsSource.isActive(),
                    designerNewsSource.getIconRes(),
                    designerNewsSource.isSwipeDismissable(),
                    new SourceUiModel.OnSourceClicked() {

                    },
                    new SourceUiModel.OnSourceDismissed() {
  
                    }
            );

    public static final DribbbleSourceItem dribbbleSource =
            new DribbbleSourceItem(
                    "dribbble",
                    true
            );

    public static final Post post = new Post(
            345,
            "Plaid",
            "www.plaid.amazing",
            "amazing",
            "www.disc.plaid",
            "www.d.plaid",
            5,
            100
    );

    public static final User player = new User(
            1,
            "Nick Butcher",
            "nickbutcher",
            "www.prettyplaid.nb"
    );

    public static final Shot shot = new Shot(
            1,
            "Foo Nick",
            "",
            new Images(),
            player
    ).apply {
        setDataSource(dribbbleSource.getKey());
    };

    public static final long userId = 5;
    public static final long storyId = 1345;
    public static final Date createdDate =
            new GregorianCalendar(2018, 1, 13).getTime();
    public static final List<Long> commentIds = new ArrayList<Long>() {{
        add(11L);
        add(12L);
    }};

    public static final StoryLinks storyLinks = new StoryLinks(
            userId,
            commentIds,
            new ArrayList<Long>(),
            new ArrayList<Long>()
    );

    public static final Story story = new Story(
            storyId,
            "Plaid 2.0 was released",
            createdDate,
            userId,
            storyLinks
    ).apply {
        setDataSource(designerNewsSource.getKey());
    };
}
