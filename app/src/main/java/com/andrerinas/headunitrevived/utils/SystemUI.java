package com.andrerinas.headunitrevived.utils;

import android.view.View;

public class SystemUI {

    public static void hide(View content) {
        // Set the IMMERSIVE flag. Set the content to appear under the system bars so that the content doesn't resize when the system bars hide and show.
        content.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                | View.SYSTEM_UI_FLAG_IMMERSIVE);//_STICKY);
    }

}
