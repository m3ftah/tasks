/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.alarms;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.todoroo.andlib.data.TodorooCursor;
import com.todoroo.andlib.sql.Criterion;
import com.todoroo.andlib.sql.Join;
import com.todoroo.andlib.sql.Order;
import com.todoroo.andlib.sql.Query;
import com.todoroo.andlib.utility.DateUtilities;
import com.todoroo.astrid.dao.MetadataDao.MetadataCriteria;
import com.todoroo.astrid.dao.TaskDao.TaskCriteria;
import com.todoroo.astrid.data.Metadata;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.reminders.Notifications;
import com.todoroo.astrid.reminders.ReminderService;
import com.todoroo.astrid.service.MetadataService;
import com.todoroo.astrid.service.MetadataService.SynchronizeMetadataCallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tasks.injection.ForApplication;

import java.util.ArrayList;
import java.util.LinkedHashSet;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides operations for working with alerts
 *
 * @author Tim Su <tim@todoroo.com>
 *
 */
@Singleton
public class AlarmService {

    private static final Logger log = LoggerFactory.getLogger(AlarmService.class);

    // --- data retrieval

    public static final String IDENTIFIER = "alarms"; //$NON-NLS-1$

    private final MetadataService metadataService;
    private final Context context;

    @Inject
    public AlarmService(MetadataService metadataService, @ForApplication Context context) {
        this.metadataService = metadataService;
        this.context = context;
    }

    /**
     * Return alarms for the given task. PLEASE CLOSE THE CURSOR!
     */
    public TodorooCursor<Metadata> getAlarms(long taskId) {
        return metadataService.query(Query.select(
                Metadata.PROPERTIES).where(MetadataCriteria.byTaskAndwithKey(
                taskId, AlarmFields.METADATA_KEY)).orderBy(Order.asc(AlarmFields.TIME)));
    }

    /**
     * Save the given array of alarms into the database
     * @return true if data was changed
     */
    public boolean synchronizeAlarms(final long taskId, LinkedHashSet<Long> alarms) {
        ArrayList<Metadata> metadata = new ArrayList<>();
        for(Long alarm : alarms) {
            Metadata item = new Metadata();
            item.setKey(AlarmFields.METADATA_KEY);
            item.setValue(AlarmFields.TIME, alarm);
            item.setValue(AlarmFields.TYPE, AlarmFields.TYPE_SINGLE);
            metadata.add(item);
        }

        final AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);

        boolean changed = metadataService.synchronizeMetadata(taskId, metadata, Metadata.KEY.eq(AlarmFields.METADATA_KEY), new SynchronizeMetadataCallback() {
            @Override
            public void beforeDeleteMetadata(Metadata m) {
                // Cancel the alarm before the metadata is deleted
                PendingIntent pendingIntent = pendingIntentForAlarm(m, taskId);
                am.cancel(pendingIntent);
            }
        });

        if(changed) {
            scheduleAlarms(taskId);
        }
        return changed;
    }

    // --- alarm scheduling

    /**
     * Gets a listing of all alarms that are active
     * @return todoroo cursor. PLEASE CLOSE THIS CURSOR!
     */
    private TodorooCursor<Metadata> getActiveAlarms() {
        return metadataService.query(Query.select(Metadata.ID, Metadata.TASK, AlarmFields.TIME).
                join(Join.inner(Task.TABLE, Metadata.TASK.eq(Task.ID))).
                where(Criterion.and(TaskCriteria.isActive(), MetadataCriteria.withKey(AlarmFields.METADATA_KEY))));
    }

    /**
     * Gets a listing of alarms by task
     * @return todoroo cursor. PLEASE CLOSE THIS CURSOR!
     */
    private TodorooCursor<Metadata> getActiveAlarmsForTask(long taskId) {
        return metadataService.query(Query.select(Metadata.ID, Metadata.TASK, AlarmFields.TIME).
                join(Join.inner(Task.TABLE, Metadata.TASK.eq(Task.ID))).
                where(Criterion.and(TaskCriteria.isActive(),
                        MetadataCriteria.byTaskAndwithKey(taskId, AlarmFields.METADATA_KEY))));
    }

    /**
     * Schedules all alarms
     */
    public void scheduleAllAlarms() {
        TodorooCursor<Metadata> cursor = getActiveAlarms();
        try {
            Metadata alarm = new Metadata();
            for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                alarm.readFromCursor(cursor);
                scheduleAlarm(alarm);
            }
        } catch (Exception e) {
            // suppress
            log.error(e.getMessage(), e);
        } finally {
            cursor.close();
        }
    }

    private static final long NO_ALARM = Long.MAX_VALUE;

    /**
     * Schedules alarms for a single task
     */
    public void scheduleAlarms(long taskId) {
        TodorooCursor<Metadata> cursor = getActiveAlarmsForTask(taskId);
        try {
            Metadata alarm = new Metadata();
            for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                alarm.readFromCursor(cursor);
                scheduleAlarm(alarm);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            cursor.close();
        }
    }

    private PendingIntent pendingIntentForAlarm(Metadata alarm, long taskId) {
        Intent intent = new Intent(context, Notifications.class);
        intent.setAction("ALARM" + alarm.getId()); //$NON-NLS-1$
        intent.putExtra(Notifications.ID_KEY, taskId);
        intent.putExtra(Notifications.EXTRAS_TYPE, ReminderService.TYPE_ALARM);

        return PendingIntent.getBroadcast(context, (int)alarm.getId(),
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Schedules alarms for a single task
     */
    private void scheduleAlarm(Metadata alarm) {
        if(alarm == null) {
            return;
        }

        long taskId = alarm.getTask();

        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = pendingIntentForAlarm(alarm, taskId);

        long time = alarm.getValue(AlarmFields.TIME);
        if(time == 0 || time == NO_ALARM) {
            am.cancel(pendingIntent);
        } else if(time > DateUtilities.now()) {
            am.set(AlarmManager.RTC_WAKEUP, time, pendingIntent);
        }
    }
}
