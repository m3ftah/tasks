/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.tags;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;

import com.todoroo.andlib.service.ContextManager;
import com.todoroo.andlib.sql.Criterion;
import com.todoroo.andlib.sql.QueryTemplate;
import com.todoroo.astrid.actfm.TagViewFragment;
import com.todoroo.astrid.api.AstridApiConstants;
import com.todoroo.astrid.api.AstridFilterExposer;
import com.todoroo.astrid.api.Filter;
import com.todoroo.astrid.api.FilterListItem;
import com.todoroo.astrid.api.FilterWithCustomIntent;
import com.todoroo.astrid.api.FilterWithUpdate;
import com.todoroo.astrid.dao.TaskDao.TaskCriteria;
import com.todoroo.astrid.data.Metadata;
import com.todoroo.astrid.data.TagData;
import com.todoroo.astrid.tags.TagService.Tag;

import org.tasks.R;
import org.tasks.injection.ForApplication;
import org.tasks.injection.InjectingBroadcastReceiver;
import org.tasks.injection.Injector;
import org.tasks.preferences.Preferences;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Exposes filters based on tags
 *
 * @author Tim Su <tim@todoroo.com>
 *
 */
public class TagFilterExposer extends InjectingBroadcastReceiver implements AstridFilterExposer {

    public static final String TAG = "tag"; //$NON-NLS-1$

    @Inject TagService tagService;
    @Inject @ForApplication Context context;
    @Inject Preferences preferences;

    /** Create filter from new tag object */
    public static FilterWithCustomIntent filterFromTag(Context context, Tag tag, Criterion criterion) {
        String title = tag.tag;
        if (TextUtils.isEmpty(title)) {
            return null;
        }
        QueryTemplate tagTemplate = tag.queryTemplate(criterion);
        ContentValues contentValues = new ContentValues();
        contentValues.put(Metadata.KEY.name, TaskToTagMetadata.KEY);
        contentValues.put(TaskToTagMetadata.TAG_NAME.name, tag.tag);
        contentValues.put(TaskToTagMetadata.TAG_UUID.name, tag.uuid);

        FilterWithUpdate filter = new FilterWithUpdate(tag.tag,
                title, tagTemplate,
                contentValues);

        filter.contextMenuLabels = new String[] {
            context.getString(R.string.tag_cm_rename),
            context.getString(R.string.tag_cm_delete)
        };
        filter.contextMenuIntents = new Intent[] {
                newTagIntent(context, RenameTagActivity.class, tag, tag.uuid),
                newTagIntent(context, DeleteTagActivity.class, tag, tag.uuid)
        };

        filter.customTaskList = new ComponentName(context, TagViewFragment.class);
        Bundle extras = new Bundle();
        extras.putString(TagViewFragment.EXTRA_TAG_NAME, tag.tag);
        extras.putString(TagViewFragment.EXTRA_TAG_UUID, tag.uuid);
        filter.customExtras = extras;

        return filter;
    }

    /** Create a filter from tag data object */
    public static FilterWithCustomIntent filterFromTagData(Context context, TagData tagData) {
        Tag tag = new Tag(tagData);
        return filterFromTag(context, tag, TaskCriteria.activeAndVisible());
    }

    private static Intent newTagIntent(Context context, Class<? extends Activity> activity, Tag tag, String uuid) {
        Intent ret = new Intent(context, activity);
        ret.putExtra(TAG, tag.tag);
        ret.putExtra(TagViewFragment.EXTRA_TAG_UUID, uuid);
        return ret;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        FilterListItem[] listAsArray = prepareFilters();

        Intent broadcastIntent = new Intent(AstridApiConstants.BROADCAST_SEND_FILTERS);
        broadcastIntent.putExtra(AstridApiConstants.EXTRAS_RESPONSE, listAsArray);
        broadcastIntent.putExtra(AstridApiConstants.EXTRAS_ADDON, TagsPlugin.IDENTIFIER);
        context.sendBroadcast(broadcastIntent, AstridApiConstants.PERMISSION_READ);
    }

    private FilterListItem[] prepareFilters() {
        ContextManager.setContext(context);

        ArrayList<FilterListItem> list = new ArrayList<>();

        list.addAll(filterFromTags(tagService.getTagList()));

        // transmit filter list
        return list.toArray(new FilterListItem[list.size()]);
    }

    private List<Filter> filterFromTags(List<Tag> tags) {
        boolean shouldAddUntagged = preferences.getBoolean(R.string.p_show_not_in_list_filter, true);

        List<Filter> filters = new ArrayList<>();

        Resources r = context.getResources();

        // --- untagged
        if (shouldAddUntagged) {
            Filter untagged = new Filter(r.getString(R.string.tag_FEx_untagged),
                    r.getString(R.string.tag_FEx_untagged),
                    tagService.untaggedTemplate(),
                    null);
            filters.add(untagged);
        }

        for (Tag tag : tags) {
            Filter f = constructFilter(context, tag);
            if (f != null) {
                filters.add(f);
            }
        }
        return filters;
    }

    protected Filter constructFilter(Context context, Tag tag) {
        return filterFromTag(context, tag, TaskCriteria.activeAndVisible());
    }

    @Override
    public FilterListItem[] getFilters(Injector injector) {
        injector.inject(this);

        return prepareFilters();
    }

}
