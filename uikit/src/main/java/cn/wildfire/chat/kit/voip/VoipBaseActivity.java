/*
 * Copyright (c) 2020 WildFireChat. All rights reserved.
 */

/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package cn.wildfire.chat.kit.voip;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import org.webrtc.StatsReport;

import java.util.List;

import cn.wildfirechat.avenginekit.AVEngineKit;
import cn.wildfirechat.client.NotInitializedExecption;

/**
 * Activity for peer connection call setup, call waiting
 * and call view.
 */
public abstract class VoipBaseActivity extends FragmentActivity implements AVEngineKit.CallSessionCallback {

    // List of mandatory application permissions.
    private static final String[] MANDATORY_PERMISSIONS = {
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.INTERNET
    };

    protected AVEngineKit gEngineKit;
    protected PowerManager.WakeLock wakeLock;
    private Handler handler = new Handler();

    protected boolean isInvitingNewParticipant;
    private String focusVideoUserId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//    Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));

        // Set window styles for fullscreen-window size. Needs to be done before
        // adding content.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
            PowerManager.ACQUIRE_CAUSES_WAKEUP |
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "bright");
        if (wakeLock != null) {
            wakeLock.acquire();
        }

        //Todo 把标题栏改成黑色
//        getWindow().addFlags(LayoutParams.FLAG_FULLSCREEN | LayoutParams.FLAG_KEEP_SCREEN_ON
//            | LayoutParams.FLAG_SHOW_WHEN_LOCKED | LayoutParams.FLAG_TURN_SCREEN_ON);
//        getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            //设置修改状态栏
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            //设置状态栏的颜色，和你的app主题或者标题栏颜色设置一致就ok了
            window.setStatusBarColor(getResources().getColor(android.R.color.black));
        }

        try {
            gEngineKit = AVEngineKit.Instance();
        } catch (NotInitializedExecption notInitializedExecption) {
            notInitializedExecption.printStackTrace();
            finishFadeout();
        }

        // Check for mandatory permissions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : MANDATORY_PERMISSIONS) {
                if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(MANDATORY_PERMISSIONS, 100);
                    break;
                }
            }
        }

        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
        if (session == null || session.getState() == AVEngineKit.CallState.Idle) {
            finishFadeout();
            return;
        }
        session.setCallback(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "需要录音和摄像头权限，才能进行语音通话", Toast.LENGTH_SHORT).show();
                finishFadeout();
                return;
            }
        }
    }

    @TargetApi(19)
    private static int getSystemUiVisibility() {
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        return flags;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isInvitingNewParticipant) {
            return;
        }
        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
        if (session == null || session.getState() == AVEngineKit.CallState.Idle) {
            finishFadeout();
            return;
        }
        session.setCallback(this);
        hideFloatingView();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isInvitingNewParticipant) {
            return;
        }
        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
        if (session != null && session.getState() != AVEngineKit.CallState.Idle) {
            session.resetRenderer();
            session.setCallback(null);
            if (!isChangingConfigurations()) {
                showFloatingView(focusVideoUserId);
            }
        }
    }

    @Override
    protected void onDestroy() {
//        Thread.setDefaultUncaughtExceptionHandler(null);
        super.onDestroy();
        if (wakeLock != null) {
            wakeLock.release();
        }
    }

    public AVEngineKit getEngineKit() {
        return gEngineKit;
    }

    @Override
    public void didCallEndWithReason(AVEngineKit.CallEndReason reason) {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_NORMAL);
        audioManager.setSpeakerphoneOn(false);
        finishFadeout();
    }

    @Override
    public void didError(String error) {
    }

    @Override
    public void didGetStats(StatsReport[] reports) {
    }

    @Override
    public void didVideoMuted(String s, boolean b) {

    }

    @Override
    public void didReportAudioVolume(String userId, int volume) {

    }

    @Override
    public void didChangeMode(boolean audioOnly) {
    }

    @Override
    public void didChangeState(AVEngineKit.CallState state) {
    }

    @Override
    public void didParticipantJoined(String s) {

    }

    @Override
    public void didParticipantConnected(String userId) {

    }

    @Override
    public void didParticipantLeft(String s, AVEngineKit.CallEndReason callEndReason) {

    }

    @Override
    public void didCreateLocalVideoTrack() {
    }

    @Override
    public void didReceiveRemoteVideoTrack(String s) {
    }

    @Override
    public void didRemoveRemoteVideoTrack(String s) {

    }

    protected void postAction(Runnable action) {
        handler.post(action);
    }

    protected boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));

                List<ResolveInfo> infos = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                if (infos == null || infos.isEmpty()) {
                    return true;
                }
                startActivity(intent);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        // do nothing
    }

    public void showFloatingView(String focusTargetId) {
        if (!checkOverlayPermission()) {
            return;
        }

        Intent intent = new Intent(this, VoipCallService.class);
        intent.putExtra("showFloatingView", true);
        if (!TextUtils.isEmpty(focusTargetId)) {
            intent.putExtra("focusTargetId", focusTargetId);
        }
        startService(intent);
        finishFadeout();
    }

    public void hideFloatingView() {
        Intent intent = new Intent(this, VoipCallService.class);
        intent.putExtra("showFloatingView", false);
        startService(intent);
    }

    protected void finishFadeout() {
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    public void setFocusVideoUserId(String focusVideoUserId){
        this.focusVideoUserId = focusVideoUserId;
    }

    public String getFocusVideoUserId() {
        return focusVideoUserId;
    }
}
