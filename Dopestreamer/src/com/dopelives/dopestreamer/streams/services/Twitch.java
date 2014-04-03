package com.dopelives.dopestreamer.streams.services;

import com.dopelives.dopestreamer.streams.StreamService;

/**
 * The service for Twitch streams.
 */
public class Twitch extends StreamService {

    /**
     * {@inheritDoc}
     */
    @Override
    public String getKey() {
        return "twitch";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLabel() {
        return "Twitch";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getIconUrl() {
        return "twitch.png";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUrl() {
        return "twitch.tv/";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDefaultChannel() {
        return "dopelives";
    }

}