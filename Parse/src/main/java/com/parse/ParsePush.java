/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import bolts.Continuation;
import bolts.Task;

/**
 * The {@code ParsePush} is a local representation of data that can be sent as a push notification.
 * <p/>
 * The typical workflow for sending a push notification from the client is to construct a new
 * {@code ParsePush}, use the setter functions to fill it with data, and then use
 * {@link #sendInBackground()} to send it.
 */
public class ParsePush {

  /* package for test */ static String KEY_DATA_MESSAGE = "alert";
  /* package for test */ static ParsePushController getPushController() {
    return ParseCorePlugins.getInstance().getPushController();
  }

  /* package for test */ static ParsePushChannelsController getPushChannelsController() {
    return ParseCorePlugins.getInstance().getPushChannelsController();
  }

  private static void checkArgument(boolean expression, Object errorMessage) {
    if (!expression) {
      throw new IllegalArgumentException(String.valueOf(errorMessage));
    }
  }

  /* package */ static class State {

    /* package */ static class Builder {

      private Set<String> channelSet;
      private ParseQuery<ParseInstallation> query;
      private Long expirationTime;
      private Long expirationTimeInterval;
      private Boolean pushToIOS;
      private Boolean pushToAndroid;
      private JSONObject data;

      public Builder() {
        // do nothing
      }

      public Builder(State state) {
        this.channelSet = state.channelSet() == null
            ? null
            : Collections.unmodifiableSet(new HashSet<>(state.channelSet()));
        this.query = state.queryState() == null
            ? null
            : new ParseQuery<>(new ParseQuery.State.Builder<ParseInstallation>(state.queryState()));
        this.expirationTime = state.expirationTime();
        this.expirationTimeInterval = state.expirationTimeInterval();
        this.pushToIOS = state.pushToIOS();
        this.pushToAndroid = state.pushToAndroid();
        // Since in state.build() we check data is not null, we do not need to check it again here.
        JSONObject copyData = null;
        try {
          copyData = new JSONObject(state.data().toString());
        } catch (JSONException e) {
          // Swallow this silently since it is impossible to happen
        }
        this.data = copyData;
      }

      public Builder expirationTime(Long expirationTime) {
        this.expirationTime = expirationTime;
        expirationTimeInterval = null;
        return this;
      }

      public Builder expirationTimeInterval(Long expirationTimeInterval) {
        this.expirationTimeInterval = expirationTimeInterval;
        expirationTime = null;
        return this;
      }

      public Builder pushToIOS(Boolean pushToIOS) {
        checkArgument(query == null, "Cannot set push targets (i.e. setPushToAndroid or " +
            "setPushToIOS) when pushing to a query");
        this.pushToIOS = pushToIOS;
        return this;
      }

      public Builder pushToAndroid(Boolean pushToAndroid) {
        checkArgument(query == null, "Cannot set push targets (i.e. setPushToAndroid or " +
            "setPushToIOS) when pushing to a query");
        this.pushToAndroid = pushToAndroid;
        return this;
      }

      public Builder data(JSONObject data) {
        this.data = data;
        return this;
      }

      public Builder channelSet(Collection<String> channelSet) {
        checkArgument(channelSet != null, "channels collection cannot be null");
        for (String channel : channelSet) {
          checkArgument(channel != null, "channel cannot be null");
        }
        this.channelSet = new HashSet<>(channelSet);
        query = null;
        return this;
      }

      public Builder query(ParseQuery<ParseInstallation> query) {
        checkArgument(query != null, "Cannot target a null query");
        checkArgument(pushToIOS == null && pushToAndroid == null, "Cannot set push targets " +
            "(i.e. setPushToAndroid or setPushToIOS) when pushing to a query");
        checkArgument(
            query.getClassName().equals(ParseObject.getClassName(ParseInstallation.class)),
            "Can only push to a query for Installations");
        channelSet = null;
        this.query = query;
        return this;
      }

      public State build() {
        if (data == null) {
          throw new IllegalArgumentException(
              "Cannot send a push without calling either setMessage or setData");
        }
        return new State(this);
      }
    }

    private final Set<String> channelSet;
    private final ParseQuery.State<ParseInstallation> queryState;
    private final Long expirationTime;
    private final Long expirationTimeInterval;
    private final Boolean pushToIOS;
    private final Boolean pushToAndroid;
    private final JSONObject data;

    private State(Builder builder) {
      this.channelSet = builder.channelSet == null ?
          null : Collections.unmodifiableSet(new HashSet<>(builder.channelSet));
      this.queryState = builder.query == null ? null : builder.query.getBuilder().build();
      this.expirationTime = builder.expirationTime;
      this.expirationTimeInterval = builder.expirationTimeInterval;
      this.pushToIOS = builder.pushToIOS;
      this.pushToAndroid = builder.pushToAndroid;
      // Since in builder.build() we check data is not null, we do not need to check it again here.
      JSONObject copyData = null;
      try {
        copyData = new JSONObject(builder.data.toString());
      } catch (JSONException e) {
        // Swallow this silently since it is impossible to happen
      }
      this.data = copyData;
    }

    public Set<String> channelSet() {
      return channelSet;
    }

    public ParseQuery.State<ParseInstallation> queryState() {
      return queryState;
    }

    public Long expirationTime() {
      return expirationTime;
    }

    public Long expirationTimeInterval() {
      return expirationTimeInterval;
    }

    public Boolean pushToIOS() {
      return pushToIOS;
    }

    public Boolean pushToAndroid() {
      return pushToAndroid;
    }

    public JSONObject data() {
      // Since in builder.build() we check data is not null, we do not need to check it again here.
      JSONObject copyData = null;
      try {
        copyData = new JSONObject(data.toString());
      } catch (JSONException e) {
        // Swallow this exception silently since it is impossible to happen
      }
      return copyData;
    }
  }

  private static final String TAG = "com.parse.ParsePush";

  /* package for test */ final State.Builder builder;

  /**
   * Creates a new push notification.
   *
   * The default channel is the empty string, also known as the global broadcast channel, but this
   * value can be overridden using {@link #setChannel(String)}, {@link #setChannels(Collection)} or
   * {@link #setQuery(ParseQuery)}. Before sending the push notification you must call either
   * {@link #setMessage(String)} or {@link #setData(JSONObject)}.
   */
  public ParsePush() {
    this(new State.Builder());
  }

  /**
   * Creates a copy of {@code push}.
   *
   * @param push
   *          The push to copy.
   */
  public ParsePush(ParsePush push) {
    this(new State.Builder(push.builder.build()));
  }

  private ParsePush(State.Builder builder) {
    this.builder = builder;
  }

  /**
   * Adds 'channel' to the 'channels' list in the current {@link ParseInstallation} and saves it in
   * a background thread.
   *
   * @param channel
   *        The channel to subscribe to.
   * @return A Task that is resolved when the the subscription is complete.
   */
  public static Task<Void> subscribeInBackground(String channel) {
    return getPushChannelsController().subscribeInBackground(channel);
  }

  /**
   * Adds 'channel' to the 'channels' list in the current {@link ParseInstallation} and saves it in
   * a background thread.
   *
   * @param channel
   *        The channel to subscribe to.
   * @param callback
   *        The SaveCallback that is called after the Installation is saved.
   */
  public static void subscribeInBackground(String channel, SaveCallback callback) {
    ParseTaskUtils.callbackOnMainThreadAsync(subscribeInBackground(channel), callback);
  }

  /**
   * Removes 'channel' from the 'channels' list in the current {@link ParseInstallation} and saves
   * it in a background thread.
   *
   * @param channel
   *        The channel to unsubscribe from.
   * @return A Task that is resolved when the the unsubscription is complete.
   */
  public static Task<Void> unsubscribeInBackground(String channel) {
    return getPushChannelsController().unsubscribeInBackground(channel);
  }
  /**
   * Removes 'channel' from the 'channels' list in the current {@link ParseInstallation} and saves
   * it in a background thread.
   *
   * @param channel
   *        The channel to unsubscribe from.
   * @param callback
   *        The SaveCallback that is called after the Installation is saved.
   */
  public static void unsubscribeInBackground(String channel, SaveCallback callback) {
    ParseTaskUtils.callbackOnMainThreadAsync(unsubscribeInBackground(channel), callback);
  }

  /**
   * A helper method to concisely send a push message to a query. This method is equivalent to
   * ParsePush push = new ParsePush(); push.setMessage(message); push.setQuery(query);
   * push.sendInBackground();
   * 
   * @param message
   *          The message that will be shown in the notification.
   * @param query
   *          A ParseInstallation query which specifies the recipients of a push.
   * @return A task that is resolved when the message is sent.
   */
  public static Task<Void> sendMessageInBackground(String message,
      ParseQuery<ParseInstallation> query) {
    ParsePush push = new ParsePush();
    push.setQuery(query);
    push.setMessage(message);
    return push.sendInBackground();
  }

  /**
   * A helper method to concisely send a push message to a query. This method is equivalent to
   * ParsePush push = new ParsePush(); push.setMessage(message); push.setQuery(query);
   * push.sendInBackground(callback);
   * 
   * @param message
   *          The message that will be shown in the notification.
   * @param query
   *          A ParseInstallation query which specifies the recipients of a push.
   * @param callback
   *          callback.done(e) is called when the send completes.
   */
  public static void sendMessageInBackground(String message, ParseQuery<ParseInstallation> query,
      SendCallback callback) {
    ParseTaskUtils.callbackOnMainThreadAsync(sendMessageInBackground(message, query), callback);
  }

  /**
   * A helper method to concisely send a push to a query. This method is equivalent to ParsePush
   * push = new ParsePush(); push.setData(data); push.setQuery(query); push.sendInBackground();
   * 
   * @param data
   *          The entire data of the push message. See the push guide for more details on the data
   *          format.
   * @param query
   *          A ParseInstallation query which specifies the recipients of a push.
   * @return A task that is resolved when the data is sent.
   */
  public static Task<Void> sendDataInBackground(JSONObject data,
      ParseQuery<ParseInstallation> query) {
    ParsePush push = new ParsePush();
    push.setQuery(query);
    push.setData(data);
    return push.sendInBackground();
  }

  /**
   * A helper method to concisely send a push to a query. This method is equivalent to ParsePush
   * push = new ParsePush(); push.setData(data); push.setQuery(query);
   * push.sendInBackground(callback);
   * 
   * @param data
   *          The entire data of the push message. See the push guide for more details on the data
   *          format.
   * @param query
   *          A ParseInstallation query which specifies the recipients of a push.
   * @param callback
   *          callback.done(e) is called when the send completes.
   */
  public static void sendDataInBackground(JSONObject data, ParseQuery<ParseInstallation> query,
      SendCallback callback) {
    ParseTaskUtils.callbackOnMainThreadAsync(sendDataInBackground(data, query), callback);
  }

  /**
   * Sets the channel on which this push notification will be sent. The channel name must start with
   * a letter and contain only letters, numbers, dashes, and underscores. A push can either have
   * channels or a query. Setting this will unset the query.
   */
  public void setChannel(String channel) {
    builder.channelSet(Collections.singletonList(channel));
  }

  /**
   * Sets the collection of channels on which this push notification will be sent. Each channel name
   * must start with a letter and contain only letters, numbers, dashes, and underscores. A push can
   * either have channels or a query. Setting this will unset the query.
   */
  public void setChannels(Collection<String> channels) {
    builder.channelSet(channels);
  }

  /**
   * Sets the query for this push for which this push notification will be sent. This query will be
   * executed in the Parse cloud; this push notification will be sent to Installations which this
   * query yields. A push can either have channels or a query. Setting this will unset the channels.
   * 
   * @param query
   *          A query to which this push should target. This must be a ParseInstallation query.
   */
  public void setQuery(ParseQuery<ParseInstallation> query) {
    builder.query(query);
  }

  /**
   * Sets a UNIX epoch timestamp at which this notification should expire, in seconds (UTC). This
   * notification will be sent to devices which are either online at the time the notification is
   * sent, or which come online before the expiration time is reached. Because device clocks are not
   * guaranteed to be accurate, most applications should instead use
   * {@link #setExpirationTimeInterval(long)}.
   */
  public void setExpirationTime(long time) {
    builder.expirationTime(time);
  }

  /**
   * Sets the time interval after which this notification should expire, in seconds. This
   * notification will be sent to devices which are either online at the time the notification is
   * sent, or which come online within the given number of seconds of the notification being
   * received by Parse's server. An interval which is less than or equal to zero indicates that the
   * message should only be sent to devices which are currently online.
   */
  public void setExpirationTimeInterval(long timeInterval) {
    builder.expirationTimeInterval(timeInterval);
  }

  /**
   * Clears both expiration values, indicating that the notification should never expire.
   */
  public void clearExpiration() {
    builder.expirationTime(null);
    builder.expirationTimeInterval(null);
  }

  /**
   * Set whether this push notification will go to iOS devices.
   * <p/>
   * Setting this to {@code true} will set {@link #setPushToAndroid(boolean)} to {@code false}.
   * <p/>
   * <strong>Note:</strong> You must set up iOS push certificates before sending pushes to iOS.
   *
   * @deprecated Please use {@link #setQuery(ParseQuery)} with a {@link ParseQuery} targeting
   * {@link ParseInstallation}s with a constraint on the {@code deviceType} field. If you use
   * {@code #setPushToIOS(boolean)} or {@link #setPushToAndroid(boolean)}, then you will only be
   * able to send to one of these two device types (e.g. and not Windows).
   */
  @Deprecated
  public void setPushToIOS(boolean pushToIOS) {
    builder.pushToIOS(pushToIOS);
  }

  /**
   * Set whether this push notification will go to Android devices.
   * <p/>
   * Setting this to {@code true} will set {@link #setPushToIOS(boolean)} to {@code false}.
   *
   * @deprecated Please use {@link #setQuery(ParseQuery)} with a {@link ParseQuery} targeting
   * {@link ParseInstallation}s with a constraint on the {@code deviceType} field. If you use
   * {@code #setPushToAndroid(boolean)} or {@link #setPushToIOS(boolean)}, then you will only be
   * able to send to one of these two device types (e.g. and not Windows).
   */
  @Deprecated
  public void setPushToAndroid(boolean pushToAndroid) {
    builder.pushToAndroid(pushToAndroid);
  }

  /**
   * Sets the entire data of the push message. See the push guide for more details on the data
   * format. This will overwrite any data specified in {@link #setMessage(String)}.
   */
  public void setData(JSONObject data) {
    builder.data(data);
  }

  /**
   * Sets the message that will be shown in the notification. This will overwrite any data specified
   * in {@link #setData(JSONObject)}.
   */
  public void setMessage(String message) {
    JSONObject data = new JSONObject();
    try {
      data.put(KEY_DATA_MESSAGE, message);
    } catch (JSONException e) {
      PLog.e(TAG, "JSONException in setMessage", e);
    }
    setData(data);
  }

  /**
   * Sends this push notification in a background thread. Use this when you do not have code to run
   * on completion of the push.
   *
   * @return A Task is resolved when the push has been sent.
   */
  public Task<Void> sendInBackground() {
    // Since getCurrentSessionTokenAsync takes time, we build the state before it.
    final State state = builder.build();
    return ParseUser.getCurrentSessionTokenAsync().onSuccessTask(new Continuation<String, Task<Void>>() {
      @Override
      public Task<Void> then(Task<String> task) throws Exception {
        String sessionToken = task.getResult();
        return getPushController().sendInBackground(state, sessionToken);
      }
    });
  }

  /**
   * Sends this push notification while blocking this thread until the push notification has
   * successfully reached the Parse servers. Typically, you should use {@link #sendInBackground()}
   * instead of this, unless you are managing your own threading.
   * 
   * @throws ParseException
   *           Throws an exception if the server is inaccessible.
   */
  public void send() throws ParseException {
    ParseTaskUtils.wait(sendInBackground());
  }

  /**
   * Sends this push notification in a background thread. This is preferable to using
   * <code>send()</code>, unless your code is already running from a background thread.
   * 
   * @param callback
   *          callback.done(e) is called when the send completes.
   */
  public void sendInBackground(SendCallback callback) {
    ParseTaskUtils.callbackOnMainThreadAsync(sendInBackground(), callback);
  }
}
