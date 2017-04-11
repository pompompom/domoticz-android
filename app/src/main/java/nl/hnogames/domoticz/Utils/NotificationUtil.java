/*
 * Copyright (C) 2015 Domoticz - Mark Heinis
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package nl.hnogames.domoticz.Utils;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.RemoteInput;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import nl.hnogames.domoticz.MainActivity;
import nl.hnogames.domoticz.R;
import nl.hnogames.domoticz.Service.RingtonePlayingService;
import nl.hnogames.domoticz.Service.StopAlarmButtonListener;

public class NotificationUtil {

    public static final String MESSAGE_CONVERSATION_ID_KEY = "conversaton_key";
    public static final String VOICE_REPLY_KEY = "voice_reply_key";
    private final static String GROUP_KEY_NOTIFICATIONS = "domoticz_notifications";
    private static final String MESSAGE_READ_ACTION = "nl.hnogames.domoticz.Service.ACTION_MESSAGE_READ";
    private static final String MESSAGE_REPLY_ACTION = "nl.hnogames.domoticz.Service.ACTION_MESSAGE_REPLY";
    private static final String UNREAD_CONVERSATION_BUILDER_NAME = "Domoticz -";
    private static SharedPrefUtil prefUtil;
    private static int NOTIFICATION_ID = 12345;

    public static void sendSimpleNotification(int idx, String title, String text, int priority, final Context context) {
        if (UsefulBits.isEmpty(title) || UsefulBits.isEmpty(text) || context == null)
            return;

        if (prefUtil == null)
            prefUtil = new SharedPrefUtil(context);

        String loggedNotification = title;
        if (title.equals(context.getString(R.string.app_name_domoticz)))
            loggedNotification = text;

        prefUtil.addUniqueReceivedNotification(loggedNotification);
        prefUtil.addLoggedNotification(new SimpleDateFormat("yyyy-MM-dd hh:mm ").format(new Date()) + loggedNotification);

        int prio = Notification.PRIORITY_DEFAULT;
        switch (priority) {
            case 1:
                prio = Notification.PRIORITY_HIGH;
                break;
            case 2:
                prio = Notification.PRIORITY_MAX;
                break;
            case -1:
                prio = Notification.PRIORITY_LOW;
                break;
            case -2:
                prio = Notification.PRIORITY_MIN;
                break;
        }

        List<String> suppressedNot = prefUtil.getSuppressedNotifications();
        List<String> alarmNot = prefUtil.getAlarmNotifications();
        try {
            if (prefUtil.isNotificationsEnabled() && suppressedNot != null && !suppressedNot.contains(text)) {
                NotificationCompat.Builder builder =
                        new NotificationCompat.Builder(context)
                                .setSmallIcon(R.drawable.domoticz_white)
                                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher))
                                .setContentTitle(alarmNot != null && alarmNot.contains(loggedNotification) ? context.getString(R.string.alarm) + ": " + title : title)
                                .setContentText(alarmNot != null && alarmNot.contains(loggedNotification) ? context.getString(R.string.alarm) + ": " + text : text)
                                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                                .setGroupSummary(true)
                                .setGroup(GROUP_KEY_NOTIFICATIONS)
                                .setPriority(prio)
                                .setAutoCancel(true);

                if (!prefUtil.OverWriteNotifications())
                    NOTIFICATION_ID = text.hashCode();

                if (prefUtil.getNotificationVibrate())
                    builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE);

                if (!UsefulBits.isEmpty(prefUtil.getNotificationSound()))
                    builder.setSound(Uri.parse(prefUtil.getNotificationSound()));

                Intent targetIntent = new Intent(context, MainActivity.class);
                if (idx > -1)
                    targetIntent.putExtra("TARGETIDX", idx);
                PendingIntent contentIntent = PendingIntent.getActivity(context, 0, targetIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                builder.setContentIntent(contentIntent);

                if (prefUtil.isNotificationsEnabled() && alarmNot != null && alarmNot.contains(loggedNotification)) {
                    Intent stopAlarmIntent = new Intent(context, StopAlarmButtonListener.class);
                    PendingIntent pendingAlarmIntent = PendingIntent.getBroadcast(context, 78578, stopAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    builder.addAction(android.R.drawable.ic_delete, "Stop", pendingAlarmIntent);
                }

                if (prefUtil.showAutoNotifications()) {
                    builder.extend(new NotificationCompat.CarExtender()
                            .setUnreadConversation(getUnreadConversation(context, text)));
                }

                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
                if (prefUtil.isNotificationsEnabled() && alarmNot != null && alarmNot.contains(loggedNotification)) {
                    Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                    if (alert == null) {
                        alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                        if (alert == null)
                            alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                    }

                    if (alert != null) {
                        Intent ringtoneServiceStartIntent = new Intent(context, RingtonePlayingService.class);
                        ringtoneServiceStartIntent.putExtra("ringtone-uri", alert.toString());
                        context.startService(ringtoneServiceStartIntent);

                        if (prefUtil.getAlarmTimer() > 0) {
                            Thread.sleep(prefUtil.getAlarmTimer() * 1000);
                            Intent stopIntent = new Intent(context, RingtonePlayingService.class);
                            context.stopService(stopIntent);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Log.i("NOTIFY", ex.getMessage());
        }
    }

    public static void sendSimpleNotification(String title, String text, int priority, final Context context) {
        sendSimpleNotification(-1, title, text, priority, context);
    }

    private static Intent getMessageReadIntent() {
        return new Intent()
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .setAction(MESSAGE_READ_ACTION)
                .putExtra(MESSAGE_CONVERSATION_ID_KEY, NOTIFICATION_ID);
    }

    private static PendingIntent getMessageReadPendingIntent(Context context) {
        return PendingIntent.getBroadcast(context,
                NOTIFICATION_ID,
                getMessageReadIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static Intent getMessageReplyIntent() {
        return new Intent()
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .setAction(MESSAGE_REPLY_ACTION)
                .putExtra(MESSAGE_CONVERSATION_ID_KEY, NOTIFICATION_ID);
    }

    private static PendingIntent getMessageReplyPendingIntent(Context context) {
        return PendingIntent.getBroadcast(context,
                NOTIFICATION_ID,
                getMessageReplyIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static RemoteInput getVoiceReplyRemoteInput() {
        return new RemoteInput.Builder(VOICE_REPLY_KEY)
                .setLabel("Reply")
                .build();
    }

    private static NotificationCompat.CarExtender.UnreadConversation getUnreadConversation(Context context, String text) {
        NotificationCompat.CarExtender.UnreadConversation.Builder unreadConversationBuilder =
                new NotificationCompat.CarExtender.UnreadConversation.Builder(UNREAD_CONVERSATION_BUILDER_NAME + text);
        unreadConversationBuilder.setReadPendingIntent(getMessageReadPendingIntent(context));
        unreadConversationBuilder.setReplyAction(getMessageReplyPendingIntent(context), getVoiceReplyRemoteInput());
        unreadConversationBuilder.addMessage(text);
        unreadConversationBuilder.setLatestTimestamp(Calendar.getInstance().get(Calendar.SECOND));
        return unreadConversationBuilder.build();
    }
}
