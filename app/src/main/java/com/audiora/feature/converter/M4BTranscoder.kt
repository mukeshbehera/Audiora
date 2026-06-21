package com.audiora.feature.converter

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import timber.log.Timber

object M4BTranscoder {

    private const val TIMEOUT_US = 5000L
    private const val TARGET_SAMPLE_RATE = 44100
    private const val TARGET_CHANNELS = 2
    private const val TARGET_BITRATE = 128000

    interface ProgressListener {
        fun onProgress(percentage: Float)
    }

    /**
     * Transcodes multiple input audio files into a single, high-fidelity M4B file (AAC audio code).
     * Decodes source files to PCM, feeds them to an AAC encoder, and muxes them into an MPEG-4 container.
     */
    fun transcode(
        context: Context,
        inputUris: List<Uri>,
        outputFile: File,
        listener: ProgressListener
    ): Boolean {
        if (inputUris.isEmpty()) return false

        var muxer: MediaMuxer? = null
        var encoder: MediaCodec? = null
        var trackIndex = -1
        var muxerStarted = false

        // Presentation timestamp offset to stitch tracks together smoothly
        var ptsOffsetUs = 0L

        try {
            // Configure Output Format for the AAC Encoder
            val encoderFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                TARGET_SAMPLE_RATE,
                TARGET_CHANNELS
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, TARGET_BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 256)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // Output Muxer targeting MPEG_4 container format (playable as M4B)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val totalUris = inputUris.size
            val info = MediaCodec.BufferInfo()

            for (index in inputUris.indices) {
                val uri = inputUris[index]
                Timber.d("Transcoding track $index: $uri")

                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(context, uri, null)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to set data source for track $index")
                    extractor.release()
                    continue
                }

                // Find audio track
                var audioTrackIdx = -1
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIdx = i
                        break
                    }
                }

                if (audioTrackIdx == -1) {
                    Timber.e("No audio track found in $uri")
                    extractor.release()
                    continue
                }

                extractor.selectTrack(audioTrackIdx)
                val inputFormat = extractor.getTrackFormat(audioTrackIdx)
                
                // Configure source decoder
                val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: ""
                val decoder = MediaCodec.createDecoderByType(mime)
                decoder.configure(inputFormat, null, null, 0)
                decoder.start()

                var isExtractorEOS = false
                var isDecoderEOS = false
                var isEncoderEOS = false

                val decoderInfo = MediaCodec.BufferInfo()
                var lastDecodedPtsUs = 0L

                while (!isDecoderEOS || !isEncoderEOS) {
                    
                    // 1. Feed Extractor search into Decoder
                    if (!isExtractorEOS) {
                        val inputBufIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                        if (inputBufIdx >= 0) {
                            val byteBuffer = decoder.getInputBuffer(inputBufIdx)
                            if (byteBuffer != null) {
                                byteBuffer.clear()
                                val sampleSize = extractor.readSampleData(byteBuffer, 0)
                                if (sampleSize < 0) {
                                    decoder.queueInputBuffer(
                                        inputBufIdx,
                                        0,
                                        0,
                                        0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    isExtractorEOS = true
                                } else {
                                    decoder.queueInputBuffer(
                                        inputBufIdx,
                                        0,
                                        sampleSize,
                                        extractor.sampleTime,
                                        0
                                    )
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    // 2. Dequeue from Decoder and Feed into Encoder
                    if (!isDecoderEOS) {
                        val decoderOutBufIdx = decoder.dequeueOutputBuffer(decoderInfo, TIMEOUT_US)
                        if (decoderOutBufIdx >= 0) {
                            val pcmBuffer = decoder.getOutputBuffer(decoderOutBufIdx)
                            
                            // Re-route PCM data to Encoder
                            if (pcmBuffer != null && decoderInfo.size > 0) {
                                val encoderInBufIdx = encoder.dequeueInputBuffer(TIMEOUT_US)
                                if (encoderInBufIdx >= 0) {
                                    val encInputBuffer = encoder.getInputBuffer(encoderInBufIdx)
                                    if (encInputBuffer != null) {
                                        encInputBuffer.clear()
                                        
                                        // Limit chunk size
                                        val sizeToCopy = Math.min(decoderInfo.size, encInputBuffer.remaining())
                                        pcmBuffer.position(decoderInfo.offset)
                                        val tempBytes = ByteArray(sizeToCopy)
                                        pcmBuffer.get(tempBytes, 0, sizeToCopy)
                                        encInputBuffer.put(tempBytes)

                                        // Retain and slide presentation timestamps cleanly across stitched files
                                        lastDecodedPtsUs = decoderInfo.presentationTimeUs
                                        val mappedPtsUs = ptsOffsetUs + lastDecodedPtsUs

                                        encoder.queueInputBuffer(
                                            encoderInBufIdx,
                                            0,
                                            sizeToCopy,
                                            mappedPtsUs,
                                            0
                                        )
                                    }
                                }
                            }

                            decoder.releaseOutputBuffer(decoderOutBufIdx, false)

                            if ((decoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                isDecoderEOS = true
                            }
                        } else if (decoderOutBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            // Format changed (usually sample rates/channel count)
                        }
                    }

                    // 3. Dequeue from Encoder and Mux to output container file
                    val encoderOutBufIdx = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (encoderOutBufIdx >= 0) {
                        val encodedBuffer = encoder.getOutputBuffer(encoderOutBufIdx)

                        if (encodedBuffer != null) {
                            if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                info.size = 0
                            }

                            if (info.size > 0 && muxerStarted) {
                                encodedBuffer.position(info.offset)
                                encodedBuffer.limit(info.offset + info.size)
                                muxer.writeSampleData(trackIndex, encodedBuffer, info)
                            }
                        }

                        encoder.releaseOutputBuffer(encoderOutBufIdx, false)

                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isEncoderEOS = true
                        }
                    } else if (encoderOutBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            val newFormat = encoder.outputFormat
                            trackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                }

                // Clean intermediate decoder
                decoder.stop()
                decoder.release()
                extractor.release()

                // Cumulative PTS shift with safe gap padding
                ptsOffsetUs += lastDecodedPtsUs + 100000L // 100ms smooth gap segment boundary
                
                // Track progress
                val fraction = (index + 1).toFloat() / totalUris
                listener.onProgress(fraction)
            }

            // Signal and flush encoder final stream
            val finalInBufIdx = encoder.dequeueInputBuffer(TIMEOUT_US)
            if (finalInBufIdx >= 0) {
                encoder.queueInputBuffer(
                    finalInBufIdx,
                    0,
                    0,
                    ptsOffsetUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }

            // Flush remaining buffers inside muxer
            var finishedMuxing = false
            while (!finishedMuxing) {
                val outIdx = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIdx >= 0) {
                    val encodedBuffer = encoder.getOutputBuffer(outIdx)
                    if (encodedBuffer != null) {
                        if (info.size > 0 && muxerStarted) {
                            encodedBuffer.position(info.offset)
                            encodedBuffer.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, encodedBuffer, info)
                        }
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        finishedMuxing = true
                    }
                } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER || outIdx < 0) {
                    finishedMuxing = true
                }
            }

            return true

        } catch (e: Exception) {
            Timber.e(e, "Crucial error transcoding audio file stream to M4B")
            return false
        } finally {
            try {
                encoder?.stop()
                encoder?.release()
                if (muxerStarted) {
                    muxer?.stop()
                }
                muxer?.release()
            } catch (e: Exception) {
                Timber.e(e, "Error releasing encoders")
            }
        }
    }
}
