/*
 * Copyright 2012 Mike Wakerly <opensource@hoho.com>.
 *
 * This file is part of the Kegtab package from the Kegbot project. For
 * more information on Kegtab or Kegbot, see <http://kegbot.org/>.
 *
 * Kegtab is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, version 2.
 *
 * Kegtab is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with Kegtab. If not, see <http://www.gnu.org/licenses/>.
 */
package org.kegbot.core;

import java.util.List;

import android.os.SystemClock;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class Flow {

  public static Predicate<Flow> PREDICATE_ACTIVE = new Predicate<Flow>() {
    @Override
    public boolean apply(Flow flow) {
      return flow.getState() == State.ACTIVE;
    }
  };

  public static Predicate<Flow> PREDICATE_IDLE = new Predicate<Flow>() {
    @Override
    public boolean apply(Flow flow) {
      return flow.isIdle();
    }
  };

  public static Predicate<Flow> PREDICATE_COMPLETED = new Predicate<Flow>() {
    @Override
    public boolean apply(Flow flow) {
      return flow.getState() == State.COMPLETED;
    }
  };

  public static enum State {
    INITIAL, ACTIVE, IDLE, COMPLETED,
  }

  /**
   * Global for next flow id.
   */
  private static int sFlowId = 0;

  /**
   * Flow id for this instance.
   */
  private final int mFlowId;

  /**
   * Tap for this flow.
   */
  private final Tap mTap;

  /**
   * Authenticated user for this flow. If unset, the flow is anonymous.
   */
  private String mUsername;

  /**
   * Current volume record, in flow meter ticks.
   */
  private int mTicks = 0;

  /**
   * The flow's state.
   */
  private State mState;

  /**
   * Time the flow was started, in milliseconds since the epoch.
   */
  private long mStartTime;

  private long mEndTime;

  /**
   * Time the flow was last updated, or ended, in milliseconds since the epoch.
   */
  private long mUpdateTime;

  /**
   * Maximum idle time, in milliseconds. If zero, flow may remain idle
   * indefinitely.
   */
  private long mMaxIdleTimeMs;

  /**
   * Shout text from the user.
   */
  private String mShout = "";

  private final List<String> mImages = Lists.newArrayList();

  private Flow(int flowId, Tap tap, long maxIdleTimeMs) {
    mState = State.INITIAL;
    mFlowId = flowId;
    mTap = tap;
    mMaxIdleTimeMs = maxIdleTimeMs;
    mUsername = "";
    mTicks = 0;
    mStartTime = SystemClock.uptimeMillis();
    mUpdateTime = SystemClock.uptimeMillis();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("Flow")
        .append(" id=").append(mFlowId)
        .append(" state=").append(mState)
        .append(" tap=").append(mTap)
        .append(" user=").append(mUsername)
        .append(" ticks=").append(getTicks())
        .append(" volume_ml=").append(getVolumeMl());
    if (!mImages.isEmpty()) {
      builder.append(" numImages=").append(mImages.size());
    }
    if (!Strings.isNullOrEmpty(mShout)) {
      builder.append(" shout='").append(mShout).append("'");
    }
    return builder.toString();
  }

  /**
   * Factory method. Generates a new Flow object with a unique flow id.
   *
   * @param tap
   * @param maxIdleTimeMs
   * @return
   */
  public static Flow build(Tap tap, long maxIdleTimeMs) {
    return new Flow(++sFlowId, tap, maxIdleTimeMs);
  }

  /**
   * Sets the flow's last activity time to now.
   */
  public void pokeActivity() {
    mUpdateTime = SystemClock.uptimeMillis();
  }

  /**
   * Increments the flow by the specified number of ticks. The recorded volume
   * is incremented by the correspond volume, as returned by
   * {@link Tap#getVolumeMlForTicks(int)}.
   *
   * @param ticks
   */
  public void addTicks(int ticks) {
    mTicks += ticks;
    pokeActivity();
  }

  public String getUsername() {
    return mUsername;
  }

  public void setUsername(String username) {
    mUsername = username;
  }

  public int getFlowId() {
    return mFlowId;
  }

  public Tap getTap() {
    return mTap;
  }

  public double getVolumeMl() {
    return mTap.getVolumeMlForTicks(mTicks);
  }

  public int getTicks() {
    return mTicks;
  }

  public long getMaxIdleTimeMs() {
    return mMaxIdleTimeMs;
  }

  public void setState(State state) {
    if (mState == State.COMPLETED) {
      throw new IllegalStateException("Flow already completed, cannot set " + state);
    }
    mState = state;
    if (mState == State.COMPLETED) {
      mEndTime = SystemClock.uptimeMillis();
    }
  }

  public State getState() {
    return mState;
  }

  public long getDurationMs() {
    if (mState != State.COMPLETED) {
      return SystemClock.uptimeMillis() - mStartTime;
    }
    return mEndTime - mStartTime;
  }

  public long getIdleTimeMs() {
    return SystemClock.uptimeMillis() - mUpdateTime;
  }

  public long getMsUntilIdle() {
    return Math.max(mMaxIdleTimeMs - getIdleTimeMs(), 0);
  }

  public void addImage(String image) {
    mImages.add(image);
  }

  public boolean removeImage(String image) {
    return mImages.remove(image);
  }

  public List<String> getImages() {
    return ImmutableList.copyOf(mImages);
  }

  public void setShout(String shout) {
    if (shout == null) {
      mShout = "";
    } else {
      mShout = shout;
    }
  }

  public String getShout() {
    return mShout;
  }

  /**
   * Returns true if the flow has exceeded the maximum allowable idle time.
   *
   * @return
   */
  public boolean isIdle() {
    if (mState != State.ACTIVE) {
      return false;
    }
    if (mMaxIdleTimeMs <= 0) {
      return false;
    }
    return getIdleTimeMs() > mMaxIdleTimeMs;
  }

  public boolean isAuthenticated() {
    return !Strings.isNullOrEmpty(mUsername);
  }

  public boolean isAnonymous() {
    return !isAuthenticated();
  }

}
