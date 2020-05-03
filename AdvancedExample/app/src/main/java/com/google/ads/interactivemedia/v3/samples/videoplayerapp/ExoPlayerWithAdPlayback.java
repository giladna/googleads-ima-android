// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.player.AdMediaInfo;
import com.google.ads.interactivemedia.v3.api.player.ContentProgressProvider;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.VideoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/** Video player that can play content video and ads. */
public class ExoPlayerWithAdPlayback extends RelativeLayout {

  // The wrapped video player.
  private VideoPlayer mVideoPlayer;
  private Context context;
  private PlayerView playerView;

  // A Timer to help track media updates
  private Timer timer;

  // Track the currently playing media file. If doing preloading, this will need to be an
  // array or other data structure.
  private AdMediaInfo adMediaInfo;

  // The SDK will render ad playback UI elements into this ViewGroup.
  private ViewGroup mAdUiContainer;

  // Used to track if the current video is an ad (as opposed to a content video).
  private boolean mIsAdDisplayed;

  // Used to track the current content video URL to resume content playback.
  private String mContentVideoUrl;

  // The saved position in the ad to resume if app is backgrounded during ad playback.
  private int mSavedAdPosition;

  // The saved position in the content to resume to after ad playback or if app is backgrounded
  // during content playback.
  private int mSavedContentPosition;

  // Used to track if the content has completed.
  private boolean contentHasCompleted;

  // VideoAdPlayer interface implementation for the SDK to send ad play/pause type events.
  private VideoAdPlayer mVideoAdPlayer;

  // ContentProgressProvider interface implementation for the SDK to check content progress.
  private ContentProgressProvider mContentProgressProvider;

  private final List<VideoAdPlayer.VideoAdPlayerCallback> mAdCallbacks =
      new ArrayList<VideoAdPlayer.VideoAdPlayerCallback>(1);

  public ExoPlayerWithAdPlayback(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    this.context = context;
  }

  public ExoPlayerWithAdPlayback(Context context, AttributeSet attrs) {
      super(context, attrs);
      this.context = context;

  }

  public ExoPlayerWithAdPlayback(Context context) {
      super(context);
      this.context = context;

  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    init();
  }

  private void startTracking() {
    if (timer != null) {
      return;
    }
    timer = new Timer();
    TimerTask updateTimerTask =
        new TimerTask() {
          @Override
          public void run() {
            // Tell IMA the current video progress. A better implementation would be
            // reactive to events from the media player, instead of polling.
            for (VideoAdPlayer.VideoAdPlayerCallback callback : mAdCallbacks) {
                if (playerView.getPlayer().isPlaying()) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onAdProgress(adMediaInfo, mVideoAdPlayer.getAdProgress());
                        }
                    });
                }
            }
          }
        };
    int initialDelayMs = 250;
    int pollingTimeMs = 250;
    timer.schedule(updateTimerTask, pollingTimeMs, initialDelayMs);
  }

  private void stopTracking() {
    if (timer != null) {
      timer.cancel();
      timer = null;
    }
  }

  private void init() {
    mIsAdDisplayed = false;
    contentHasCompleted = false;
    mSavedAdPosition = 0;
    mSavedContentPosition = 0;
    playerView =  this.getRootView().findViewById(R.id.videoPlayer);
    mAdUiContainer =  this.getRootView().findViewById(R.id.adUiContainer);
    mVideoPlayer = new SampleVideoPlayer(context, playerView);

    // Define VideoAdPlayer connector.
    mVideoAdPlayer =
        new VideoAdPlayer() {
          @Override
          public int getVolume() {
            return mVideoPlayer.getVolume();
          }

          @Override
          public void playAd(AdMediaInfo info) {
            startTracking();
            if (mIsAdDisplayed) {
              mVideoPlayer.resume();
            } else {
              mIsAdDisplayed = true;
              mVideoPlayer.play();
            }
          }

          @Override
          public void loadAd(AdMediaInfo info, AdPodInfo api) {
            adMediaInfo = info;
            mIsAdDisplayed = false;
            mVideoPlayer.setVideoPath(info.getUrl());
          }

          @Override
          public void stopAd(AdMediaInfo info) {
            stopTracking();
            mVideoPlayer.stopPlayback();
          }

          @Override
          public void pauseAd(AdMediaInfo info) {
            stopTracking();
            mVideoPlayer.pause();
          }

          @Override
          public void release() {
            // any clean up that needs to be done
          }

          @Override
          public void addCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
            mAdCallbacks.add(videoAdPlayerCallback);
        }

      @Override
      public void removeCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
          mAdCallbacks.remove(videoAdPlayerCallback);
      }

          @Override
          public VideoProgressUpdate getAdProgress() {
            if (!mIsAdDisplayed || mVideoPlayer.getDuration() <= 0) {
              return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
            }
            return new VideoProgressUpdate(
                    (long)(Math.ceil(mVideoPlayer.getCurrentPosition() / 1000.0) *1000), mVideoPlayer.getDuration());
          }
        };

    mContentProgressProvider =
        new ContentProgressProvider() {
          @Override
          public VideoProgressUpdate getContentProgress() {
            if (mIsAdDisplayed || mVideoPlayer.getDuration() <= 0) {
              return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
            }
            return new VideoProgressUpdate(
                    (long)(Math.ceil(mVideoPlayer.getCurrentPosition() / 1000.0) *1000), mVideoPlayer.getDuration());
          }
        };

    // Set player callbacks for delegating major video events.
    mVideoPlayer.addPlayerCallback(
        new VideoPlayer.PlayerCallback() {
          @Override
          public void onPlay() {
            if (mIsAdDisplayed) {
              for (VideoAdPlayer.VideoAdPlayerCallback callback : mAdCallbacks) {
                callback.onPlay(adMediaInfo);
              }
            }
          }

          @Override
          public void onPause() {
            if (mIsAdDisplayed) {
              for (VideoAdPlayer.VideoAdPlayerCallback callback : mAdCallbacks) {
                callback.onPause(adMediaInfo);
              }
            }
          }

          @Override
          public void onResume() {
            if (mIsAdDisplayed) {
              for (VideoAdPlayer.VideoAdPlayerCallback callback : mAdCallbacks) {
                callback.onResume(adMediaInfo);
              }
            }
          }

          @Override
          public void onError() {
            if (mIsAdDisplayed) {
              for (VideoAdPlayer.VideoAdPlayerCallback callback : mAdCallbacks) {
                callback.onError(adMediaInfo);
              }
            }
          }

          @Override
          public void onCompleted() {
            if (mIsAdDisplayed) {
              for (VideoAdPlayer.VideoAdPlayerCallback callback : mAdCallbacks) {
                callback.onEnded(adMediaInfo);
              }
            } else {
              contentHasCompleted = true;
              // Alert an external listener that our content video is complete.
              for (VideoAdPlayer.VideoAdPlayerCallback callback : mAdCallbacks) {
                //callback.onContentComplete();
              }
            }
          }
        });
  }

  /** Set the path of the video to be played as content. */
  public void setContentVideoPath(String contentVideoUrl) {
    mContentVideoUrl = contentVideoUrl;
    contentHasCompleted = false;
  }

  /**
   * Save the playback progress state of the currently playing video. This is called when content is
   * paused to prepare for ad playback or when app is backgrounded.
   */
  public void savePosition() {
    if (mIsAdDisplayed) {
      mSavedAdPosition = mVideoPlayer.getCurrentPosition();
    } else {
      mSavedContentPosition = mVideoPlayer.getCurrentPosition();
    }
  }

  /**
   * Restore the currently loaded video to its previously saved playback progress state. This is
   * called when content is resumed after ad playback or when focus has returned to the app.
   */
  public void restorePosition() {
    if (mIsAdDisplayed) {
      mVideoPlayer.seekTo(mSavedAdPosition);
    } else {
      mVideoPlayer.seekTo(mSavedContentPosition);
    }
  }

  /** Pauses the content video. */
  public void pause() {
    mVideoPlayer.pause();
  }

  /** Plays the content video. */
  public void play() {
    mVideoPlayer.play();
  }

  /** Seeks the content video. */
  public void seek(int time) {
    // Seek only if an ad is not playing. Save the content position either way.
    if (!mIsAdDisplayed) {
      mVideoPlayer.seekTo(time);
    }
    mSavedContentPosition = time;
  }

  /** Returns current content video play time. */
  public int getCurrentContentTime() {
    if (mIsAdDisplayed) {
      return mSavedContentPosition;
    } else {
      return mVideoPlayer.getCurrentPosition();
    }
  }

  /**
   * Pause the currently playing content video in preparation for an ad to play, and disables the
   * media controller.
   */
  public void pauseContentForAdPlayback() {
    mVideoPlayer.disablePlaybackControls();
    savePosition();
    mVideoPlayer.stopPlayback();
  }

  /**
   * Resume the content video from its previous playback progress position after an ad finishes
   * playing. Re-enables the media controller.
   */
  public void resumeContentAfterAdPlayback() {
    if (mContentVideoUrl == null || mContentVideoUrl.isEmpty()) {
      Log.w("ImaExample", "No content URL specified.");
      return;
    }
    mIsAdDisplayed = false;
    mVideoPlayer.setVideoPath(mContentVideoUrl);
    mVideoPlayer.enablePlaybackControls();
    mVideoPlayer.seekTo(mSavedContentPosition);
    mVideoPlayer.play();

    if (contentHasCompleted) {
      mVideoPlayer.pause();
    }
  }

  /** Returns the UI element for rendering video ad elements. */
  public ViewGroup getAdUiContainer() {
    return mAdUiContainer;
  }

  /** Returns an implementation of the SDK's VideoAdPlayer interface. */
  public VideoAdPlayer getVideoAdPlayer() {
    return mVideoAdPlayer;
  }

  /** Returns if an ad is displayed. */
  public boolean getIsAdDisplayed() {
    return mIsAdDisplayed;
  }

  public ContentProgressProvider getContentProgressProvider() {
    return mContentProgressProvider;
  }
}
