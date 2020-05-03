// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.ads.interactivemedia.v3.samples.samplevideoplayer;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.MediaController;
import android.widget.VideoView;

import androidx.annotation.NonNull;

import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;
import java.util.List;

/** A VideoView that intercepts various methods and reports them back via a PlayerCallback. */
public class SampleVideoPlayer implements VideoPlayer, Player.EventListener {

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    switch (playbackState) {
      case Player.STATE_IDLE:
        break;
      case Player.STATE_BUFFERING:
        break;
      case Player.STATE_READY:

        break;
      case Player.STATE_ENDED:
        player.stop(false); // true?
        mPlaybackState = PlaybackState.STOPPED;

        for (PlayerCallback callback : mVideoPlayerCallbacks) {
          callback.onCompleted();
        }
        break;
      default:
        break;
    }
  }

  @Override
  public void onPlayerError(ExoPlaybackException error) {
    mPlaybackState = PlaybackState.STOPPED;
    for (PlayerCallback callback : mVideoPlayerCallbacks) {
      callback.onError();
    }
  }

  private enum PlaybackState {
    STOPPED,
    PAUSED,
    PLAYING
  }

  private Context context;
  private PlayerView playerView;
  private MediaController mMediaController;
  private PlaybackState mPlaybackState;
  private SimpleExoPlayer player;
  private DefaultTrackSelector trackSelector;
  private EventLogger eventLogger;
  private DefaultRenderersFactory renderersFactory;
  private final List<PlayerCallback> mVideoPlayerCallbacks = new ArrayList<PlayerCallback>(1);

  public SampleVideoPlayer(Context context, PlayerView playerView, AttributeSet attrs, int defStyle) {
    this.context = context;
    this.playerView = playerView;
    init();
  }

  public SampleVideoPlayer(Context context, AttributeSet attrs, PlayerView playerView) {
    this.context = context;
    this.playerView = playerView;
    init();
  }

  public SampleVideoPlayer(Context context, PlayerView playerView) {
    this.context = context;
    this.playerView = playerView;
    init();
  }

  private MediaSource buildMediaSource(Uri uri) {

    switch (Util.inferContentType(uri)) {
      case C.TYPE_DASH:
        return new DashMediaSource.Factory(
                new DefaultDashChunkSource.Factory(buildDataSourceFactory()),
                buildDataSourceFactory())
                .createMediaSource(uri);
      case C.TYPE_HLS:
        return new HlsMediaSource.Factory(buildDataSourceFactory())
                .createMediaSource(uri);
      case C.TYPE_OTHER:
        return new ProgressiveMediaSource.Factory(buildDataSourceFactory())
                .createMediaSource(uri);
      default: {
        throw new IllegalStateException("Unsupported type: " + Util.inferContentType(uri));
      }
    }
  }

  private DataSource.Factory buildDataSourceFactory() {
    return new DefaultDataSourceFactory(context,
            buildHttpDataSourceFactory());
  }

  private HttpDataSource.Factory buildHttpDataSourceFactory() {
    return new DefaultHttpDataSourceFactory(Util.getUserAgent(context, "AdPlayKit"),
            8000,
            8000, true);
  }

  private EventLogger getEventLogger() {
    if (eventLogger == null) {
      eventLogger = new EventLogger(getTrackSelector());
    }
    return eventLogger;
  }

  private DefaultTrackSelector getTrackSelector() {
    if (trackSelector == null) {
      trackSelector = new DefaultTrackSelector(context, new AdaptiveTrackSelection.Factory());
      DefaultTrackSelector.ParametersBuilder builder = new DefaultTrackSelector.ParametersBuilder(context);
      trackSelector.setParameters(builder.build());
    }
    return trackSelector;
  }

  @NonNull
  private DefaultRenderersFactory getRenderersFactory() {
    if (renderersFactory == null) {
      renderersFactory = new DefaultRenderersFactory(context);
    }
    return renderersFactory;
  }

  private void init() {
    mPlaybackState = PlaybackState.STOPPED;
    player = new SimpleExoPlayer.Builder(context, getRenderersFactory())
            .setTrackSelector(getTrackSelector()).build();
    player.addAnalyticsListener(getEventLogger());
    playerView.setPlayer(player);
    playerView.setUseController(false);

    //mMediaController = new MediaController(context);
    //mMediaController.setAnchorView(this);

    // Set OnCompletionListener to notify our callbacks when the video is completed.
//    super.setOnCompletionListener(
//        new OnCompletionListener() {
//
//          @Override
//          public void onCompletion(MediaPlayer mediaPlayer) {
//            // Reset the MediaPlayer.
//            mediaPlayer.reset();
//            mediaPlayer.setDisplay(getHolder());
//            mPlaybackState = PlaybackState.STOPPED;
//
//            for (PlayerCallback callback : mVideoPlayerCallbacks) {
//              callback.onCompleted();
//            }
//          }
//        });
//
//
  }

  @Override
  public int getDuration() {
    return mPlaybackState == PlaybackState.STOPPED ? 0 : (int) player.getDuration();
  }

  @Override
  public int getVolume() {
    // Get the system's audio service and get media volume from it.
    AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    if (audioManager != null) {
      double volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
      double max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
      if (max <= 0) {
        return 0;
      }
      // Return a range from 0-100.
      return (int) ((volume / max) * 100.0f);
    }
    return 0;
  }

  // Methods implementing the VideoPlayer interface.
  @Override
  public void play() {
    player.setPlayWhenReady(true);
    for (PlayerCallback callback : mVideoPlayerCallbacks) {
      callback.onPlay();
    }
    mPlaybackState = PlaybackState.PLAYING;
  }

  @Override
  public void resume() {
    player.setPlayWhenReady(true);
    for (PlayerCallback callback : mVideoPlayerCallbacks) {
      callback.onResume();
    }
    mPlaybackState = PlaybackState.PLAYING;
  }

  @Override
  public int getCurrentPosition() {
    return (int) player.getCurrentPosition();
  }

  @Override
  public void seekTo(int videoPosition) {
    player.seekTo(videoPosition);
  }

  @Override
  public void pause() {
    player.setPlayWhenReady(false);
    mPlaybackState = PlaybackState.PAUSED;
    for (PlayerCallback callback : mVideoPlayerCallbacks) {
      callback.onPause();
    }
  }

  @Override
  public void stopPlayback() {
    if (mPlaybackState == PlaybackState.STOPPED) {
      return;
    }
    player.stop(false);
    mPlaybackState = PlaybackState.STOPPED;
  }

  @Override
  public void disablePlaybackControls() {
    //setMediaController(null);
  }

  @Override
  public void enablePlaybackControls() {
    //setMediaController(mMediaController);
  }

  @Override
  public void setVideoPath(String videoUrl) {
    Uri currentAdUri = Uri.parse(videoUrl);
    MediaSource mediaSource = buildMediaSource(currentAdUri);
    player.stop(false);
    player.prepare(mediaSource);
  }

  @Override
  public void addPlayerCallback(PlayerCallback callback) {
    mVideoPlayerCallbacks.add(callback);
  }

  @Override
  public void removePlayerCallback(PlayerCallback callback) {
    mVideoPlayerCallbacks.remove(callback);
  }
}
