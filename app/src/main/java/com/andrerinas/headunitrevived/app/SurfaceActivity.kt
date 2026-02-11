package com.andrerinas.headunitrevived.app

import android.os.Bundle
import com.andrerinas.headunitrevived.R

abstract class SurfaceActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_headunit)
    }
}
