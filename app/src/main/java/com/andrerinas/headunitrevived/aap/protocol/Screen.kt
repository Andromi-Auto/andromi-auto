package com.andrerinas.headunitrevived.aap.protocol

import com.andrerinas.headunitrevived.aap.protocol.proto.Control

/**
 * @author algavris
 * @date 22/07/2017
 */

class Screen(val width: Int, val height: Int)
{
    companion object {
        private val _480 = Screen(800, 480)
        private val _720 = Screen(1280, 720)
        private val _1080 = Screen(1920, 1080)

        fun forResolution(resolutionType: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType): Screen {
                return when(resolutionType.number) {
                    Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._800x480_VALUE -> _480
                    Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1280x720_VALUE -> _720
                    Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1920x1080_VALUE -> _1080
                    else -> _720
                }
        }

        fun forResolution(width: Int, height: Int): Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType {
            return when {
                width >= _1080.width && height >= _1080.height -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1920x1080
                width >= _720.width && height >= _720.height -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1280x720
                else -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._800x480
            }
        }
    }
}