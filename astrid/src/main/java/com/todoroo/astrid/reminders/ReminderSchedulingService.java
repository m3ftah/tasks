/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.reminders;

import android.content.Intent;
import android.os.IBinder;

import com.todoroo.andlib.service.ContextManager;
import com.todoroo.andlib.utility.AndroidUtilities;
import com.todoroo.astrid.alarms.AlarmService;
import com.todoroo.astrid.dao.TaskDao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tasks.injection.InjectingService;
import org.tasks.scheduling.RefreshScheduler;

import javax.inject.Inject;

/**
 * Schedules reminders in the background to prevent ANR's
 *
 * @author Tim Su
 *
 */
public class ReminderSchedulingService extends InjectingService {

    private static final Logger log = LoggerFactory.getLogger(ReminderSchedulingService.class);

    @Inject RefreshScheduler refreshScheduler;
    @Inject AlarmService alarmService;
    @Inject ReminderService reminderService;
    @Inject TaskDao taskDao;

    /** Receive the alarm - start the synchronize service! */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ContextManager.setContext(ReminderSchedulingService.this);

        new Thread(new Runnable() {
            @Override
            public void run() {
                delaySchedulingToPreventANRs();
                scheduleReminders();
                stopSelf();
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void scheduleReminders() {
        try {
            reminderService.scheduleAllAlarms(taskDao);
            alarmService.scheduleAllAlarms();
            refreshScheduler.scheduleAllAlarms();

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private void delaySchedulingToPreventANRs() {
        AndroidUtilities.sleepDeep(5000L);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
