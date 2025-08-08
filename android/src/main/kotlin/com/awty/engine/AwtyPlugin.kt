package com.awty.engine

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodChannel
import android.content.Context

class AwtyPlugin: FlutterPlugin {
    private lateinit var channel: MethodChannel

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "awty_engine")
        val platformChannel = AwtyPlatformChannel(binding.applicationContext)
        channel.setMethodCallHandler(platformChannel)
        
        // Set the method channel reference so the platform channel can send callbacks
        AwtyPlatformChannel.setMethodChannel(channel)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
} 