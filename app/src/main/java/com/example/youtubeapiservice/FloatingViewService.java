package com.example.youtubeapiservice;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import com.google.android.youtube.player.YouTubePlayerView;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


public class FloatingViewService extends Service {

    private WindowManager mWindowManager;
    private View floatingView;
    private EditText etSearch;
    private YouTubePlayerView playerView;
    private String API_KEY = "KEY";
    private String VIDEO_CODE = "GdNwaa1m1Yo";
    private YouTubePlayer youTubePlayer;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        //Inflate the floating view layout we created
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_item, null);

        //Use different flag for older Android versions
        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }
        //Add the view to the window.
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                LAYOUT_FLAG, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        //Specify the view position
        params.gravity = Gravity.TOP | Gravity.START;        //Initially view will be added to top-left corner
        params.x = 0;
        params.y = 100;

        //Add the view to the window
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(floatingView, params);

        com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView youTubePlayerView = floatingView.findViewById(R.id.ytPlayerView);
        youTubePlayerView.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NotNull YouTubePlayer player) {
                player.loadVideo(VIDEO_CODE, 0);
                youTubePlayer = player;
            }
        });

        etSearch = floatingView.findViewById(R.id.etSearch);
        /*etSearch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                System.out.println("focus changed");
                params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                mWindowManager.updateViewLayout(floatingView, params);
            }
        });*/

        etSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                mWindowManager.updateViewLayout(floatingView, params);
            }
        });

        floatingView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if(etSearch.hasFocus()) {
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                } else {
                    params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                }
                mWindowManager.updateViewLayout(floatingView, params);
            }
            return false;
        });

        floatingView.findViewById(R.id.btnSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText etSearch = floatingView.findViewById(R.id.etSearch);
                String query = etSearch.getText().toString();
                etSearch.clearFocus();
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                mWindowManager.updateViewLayout(floatingView, params);

                new Thread(() -> {
                    try {
                        HttpURLConnection connection;
                        URL url = new URL("https://www.googleapis.com/youtube/v3/search?part=snippet&q=" +
                                query + "&maxResults=1&type=video&key=" + API_KEY);
                        connection = (HttpURLConnection) url.openConnection();
                        connection.connect();

                        if (connection.getResponseCode() == 200) {
                            InputStream inputStream = connection.getInputStream();
                            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

                            String line;
                            StringBuilder result = new StringBuilder();

                            while ((line = bufferedReader.readLine()) != null) {
                                result.append(line).append("\n");
                            }

                            JSONObject object = new JSONObject(result.toString());
                            String key = object.getJSONArray("items").
                                    getJSONObject(0).getJSONObject("id").
                                    getString("videoId");
                            youTubePlayer.loadVideo(key, 0);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        });

        //Move view around screen
        floatingView.findViewById(R.id.btnLaunch).setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            double deltaTime, startTime;
            boolean moved = false;
            boolean hidden = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        moved = false;

                        //Remember the initial position.
                        initialX = params.x;
                        initialY = params.y;

                        startTime = System.currentTimeMillis();

                        //Get the touch location
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        int XDiff = (int) (event.getRawX() - initialTouchX);
                        int YDiff = (int) (event.getRawY() - initialTouchY);

                        //The check for Xdiff <10 && YDiff< 10 because sometime elements moves a little while clicking.
                        if (XDiff < 10 && YDiff < 10) {

                            if (hidden) {
                                floatingView.findViewById(R.id.ytPlayerView).setVisibility(View.VISIBLE);
                            } else {
                                floatingView.findViewById(R.id.ytPlayerView).setVisibility(View.GONE);
                            }
                            hidden = !hidden;
                        }
                        //return false;
                    case MotionEvent.ACTION_MOVE:
                        System.out.println("move called");

                        //Calculate the X and Y coordinates of the view.
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        mWindowManager.updateViewLayout(floatingView, params);

                        XDiff = (int) (event.getRawX() - initialTouchX);
                        YDiff = (int) (event.getRawY() - initialTouchY);
                        System.out.println("YDiff: " + YDiff);
                        if (XDiff > 10 || YDiff > 10) {
                            moved = true;
                        }
                        // return false;
                }

                deltaTime = (System.currentTimeMillis() - startTime);
                if (deltaTime > 500 && !moved) {
                    stopSelf();
                }
                return false;
            }
        });

        floatingView.findViewById(R.id.btnResize).setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            double deltaTime, startTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        //Remember the initial position.
                        initialX = params.x;
                        initialY = params.y;

                        //Get the touch location
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        int XDiff = (int) (event.getRawX() - initialTouchX);
                        int YDiff = (int) (event.getRawY() - initialTouchY);

                        //The check for Xdiff <10 && YDiff< 10 because sometime elements moves a little while clicking.
                        if (XDiff < 10 && YDiff < 10) {

                            Intent startMain = new Intent(Intent.ACTION_MAIN);
                            startMain.addCategory(Intent.CATEGORY_HOME);
                            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(startMain);

                        }
                        //return false;
                    case MotionEvent.ACTION_MOVE:
                        XDiff = (int) (event.getRawX() - initialTouchX);
                        YDiff = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(XDiff) > 9 || Math.abs(YDiff) > 9) {
                            params.width = floatingView.getWidth() + XDiff;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            mWindowManager.updateViewLayout(floatingView, params);
                        }
                }
                return false;
            }
        });

        /*btnClose.setOnLongClickListener(
                v -> {
                    stopSelf();
                    return false;
                }
        );*/

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) mWindowManager.removeView(floatingView);
    }
}