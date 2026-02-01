package com.andrerinas.headunitrevived.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.andrerinas.headunitrevived.R

abstract class SurfaceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_headunit)
    }
}
