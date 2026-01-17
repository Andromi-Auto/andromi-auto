package com.andrerinas.headunitrevived.app

import android.app.Activity
import android.os.Bundle

import com.andrerinas.headunitrevived.R
import com.andrerinas.headunitrevived.utils.SystemUI


abstract class SurfaceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_headunit)
    }
}
