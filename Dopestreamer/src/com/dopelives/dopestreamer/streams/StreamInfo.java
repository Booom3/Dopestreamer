package com.dopelives.dopestreamer.streams;

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

import com.dopelives.dopestreamer.gui.StreamState;
import com.dopelives.dopestreamer.streams.services.Vacker;
import com.dopelives.dopestreamer.util.Audio;
import com.dopelives.dopestreamer.util.HttpHelper;
import com.dopelives.dopestreamer.util.Pref;

/**
 * A static helper class for getting stream info from the IRC topic.
 */
public class StreamInfo {

    /** The URL at which to get the topic info */
    private static final String URL_TOPIC = "http://goalitium.kapsi.fi/dopelives_status";
    /** The URL at which to get the Hitbox info */
    private static final String URL_HITBOX = "http://api.hitbox.tv/media/live/dopefish";

    /** The amount of time in milliseconds before between each topic request */
    private static final int REQUEST_INTERVAL_TOPIC = 5 * 1000;
    /** The amount of time in milliseconds before between each topic request */
    private static final int REQUEST_INTERVAL_VIEWER_COUNT = 15 * 1000;

    /** The pattern used to match active streams */
    private static final Pattern sTopicParser = Pattern.compile("^(.+) is playing (.+)$");

    /** Whether or not the initial update is processing */
    private static boolean sInitialUpdate = true;
    /** Whether or not a stream is currently active */
    private static boolean sStreamActive = false;
    /** The last detected streamer */
    private static String sStreamer;
    /** The last detected game */
    private static String sGame;

    /** The amount of viewers on Vacker */
    private static int sViewersVacker = 0;
    /** The amount of viewers on Hitbox */
    private static int sViewersHitbox = 0;

    /** The timer used for request intervals */
    private static final Timer sRequestTimer = new Timer();
    /** The timer task used for request topic intervals */
    private static TimerTask sRequestTopicInterval;
    /** The timer task used for request viewer count intervals */
    private static TimerTask sRequestViewerCountInterval;

    /** The listeners that will receive updates of stream info changes */
    private static final List<StreamInfoListener> sListeners = new LinkedList<>();

    /** The updater performing the HTTP request to get the latest topic info */
    private static final Runnable sTopicUpdater = new Runnable() {
        @Override
        public void run() {
            // Check the newest stream info
            final String result = HttpHelper.getContent(URL_TOPIC);
            if (result != null) {
                final Matcher matcher = sTopicParser.matcher(result.trim());
                if (matcher.find()) {
                    // Stream info found, see if it needs to be updated
                    final String streamer = matcher.group(1);
                    final String game = matcher.group(2);

                    if (!sStreamActive || !sStreamer.equals(streamer) || !sGame.equals(game)) {
                        sStreamActive = true;
                        sStreamer = streamer;
                        sGame = game;

                        // Notify all listeners of a change in stream info
                        for (final StreamInfoListener listener : sListeners) {
                            listener.onStreamInfoUpdated(sStreamer, sGame);
                        }

                        // Notify the user if he wants and if a stream isn't already active
                        if (Pref.NOTIFICATIONS.getBoolean()
                                && StreamManager.getInstance().getStreamState() == StreamState.INACTIVE
                                && !sInitialUpdate) {
                            Audio.playNotification();
                        }
                    }

                } else {
                    // No stream info found
                    if (sStreamActive) {
                        sStreamActive = false;

                        for (final StreamInfoListener listener : sListeners) {
                            listener.onStreamInfoRemoved();
                        }
                    }
                }
            }

            sInitialUpdate = false;
        }
    };

    /** The updater performing the HTTP request to get the latest Vacker info */
    private static final Runnable sVackerUpdater = new Runnable() {

        /** The channels to sum the viewers of */
        private final String[] mChannels = { "live", "live_low", "restream", "restream_low" };

        @Override
        public void run() {
            int viewerCount = 0;

            // Retrieve the viewer count for all Vacker servers
            for (final Vacker.Server server : Vacker.Server.values()) {
                // Check the newest stream info
                final String result = HttpHelper.getContent(server.getStatsUrl());

                // Only update the Vacker viewer count if all requests were successful
                if (result == null) {
                    return;
                }

                final JSONObject json = new JSONObject(result);

                // Sum up the viewers of all relevant channels
                for (final String channel : mChannels) {
                    final JSONObject channelInfo = json.getJSONObject(channel);
                    if (channelInfo.has("viewers")) {
                        viewerCount += channelInfo.getInt("viewers");
                    }
                }

            }

            // If the viewer count changed, update it
            if (viewerCount != sViewersVacker) {
                sViewersVacker = viewerCount;
                updateViewerCount();
            }
        }
    };

    /** The updater performing the HTTP request to get the latest Hitbox info */
    private static final Runnable sHitboxUpdater = new Runnable() {
        @Override
        public void run() {
            // Check the newest stream info
            final String result = HttpHelper.getContent(URL_HITBOX);
            if (result != null) {
                final JSONObject json = new JSONObject(result).getJSONArray("livestream").getJSONObject(0);
                int viewerCount = 0;

                // Only add the Hitbox viewer count if it's live
                if (json.getInt("media_is_live") == 1) {
                    viewerCount += json.getInt("media_views");
                }

                // If the viewer count changed, update it
                if (viewerCount != sViewersHitbox) {
                    sViewersHitbox = viewerCount;
                    updateViewerCount();
                }
            }
        }
    };

    /**
     * Updates the topic info.
     */
    private static void executeTopicRefresh() {
        new Thread(sTopicUpdater).start();
    }

    /**
     * Updates the viewer counts.
     */
    private static void executeViewerCountRefresh() {
        new Thread(sVackerUpdater).start();
        new Thread(sHitboxUpdater).start();
    }

    /**
     * Refreshes the latest stream info. If an interval is active, its timer will reset.
     */
    public synchronized static void requestRefresh() {
        if (sRequestTopicInterval != null) {
            startRequestInterval();
        } else {
            executeTopicRefresh();
            executeViewerCountRefresh();
        }
    }

    /**
     * Sets an interval to periodically refresh the latest stream info. If an interval is already active, its timer will
     * reset.
     */
    public synchronized static void startRequestInterval() {
        // Stop any current refresh task
        stopRequestInterval();

        // Create the timer tasks
        sRequestTopicInterval = new TimerTask() {
            @Override
            public void run() {
                executeTopicRefresh();
            }
        };
        sRequestViewerCountInterval = new TimerTask() {
            @Override
            public void run() {
                executeViewerCountRefresh();
            }
        };

        // Schedule the tasks
        sRequestTimer.schedule(sRequestTopicInterval, 0, REQUEST_INTERVAL_TOPIC);
        sRequestTimer.schedule(sRequestViewerCountInterval, 0, REQUEST_INTERVAL_VIEWER_COUNT);
    }

    /**
     * Stops the interval to periodically refresh the latest stream info.
     */
    public synchronized static void stopRequestInterval() {
        if (sRequestTopicInterval != null) {
            sRequestTopicInterval.cancel();
            sRequestTopicInterval = null;
        }
        if (sRequestViewerCountInterval != null) {
            sRequestViewerCountInterval.cancel();
            sRequestViewerCountInterval = null;
        }

        sRequestTimer.purge();
    }

    /**
     * Informs the listeners of an updated viewer count.
     */
    private static void updateViewerCount() {
        final int viewerCount = sViewersVacker + sViewersHitbox;

        for (final StreamInfoListener listener : sListeners) {
            listener.onViewerCountUpdated(viewerCount);
        }
    }

    /**
     * Adds a listener that will be informed of any stream info update.
     *
     * @param listener
     *            The listener to receive updates
     */
    public static void addListener(final StreamInfoListener listener) {
        sListeners.add(listener);
    }

    /**
     * This is a static-only class.
     */
    private StreamInfo() {}

    /**
     * The interface for receiving updates of stream info changes.
     */
    public interface StreamInfoListener {

        /**
         * Called when the stream info is updated for an active stream.
         *
         * @param streamer
         *            The streamer
         * @param game
         *            The game being streamed
         */
        void onStreamInfoUpdated(String streamer, String game);

        /**
         * Called when a streamer has stopped.
         */
        void onStreamInfoRemoved();

        /**
         * Called when the viewer count is updated.
         *
         * @param viewerCount
         *            The new amount of viewers
         */
        void onViewerCountUpdated(int viewerCount);
    }
}
