package com.twilio.exampleservice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private var service: CallServiceAPI? = null
    private var bound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        start_call_button.setOnClickListener {
            startActivity(Intent(this, VideoActivity::class.java))
        }
    }

    private var serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            val binder = service as CallService.LocalBinder
            this@MainActivity.service = binder.getService()
            bound = true
            onServiceBound()
        }
    }

    private fun onServiceBound() {
        service?.let {
            if (it.isCallInProgress()) {
                start_call_button.text = getString(R.string.return_to_call)
            } else {
                start_call_button.text = getString(R.string.start_call)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to CallService
        Intent(this, CallService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        unbindService(serviceConnection)
        bound = false
        super.onStop()
    }
}
