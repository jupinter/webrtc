/*
 * libjingle
 * Copyright 2015 Google Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.webrtc;

import android.annotation.TargetApi;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Build;
import android.test.ActivityTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import org.webrtc.MediaCodecVideoEncoder.OutputBufferInfo;

import java.nio.ByteBuffer;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
public final class MediaCodecVideoEncoderTest extends ActivityTestCase {
  final static String TAG = "MediaCodecVideoEncoderTest";

  @SmallTest
  public static void testInitializeUsingByteBuffer() {
    if (!MediaCodecVideoEncoder.isVp8HwSupported()) {
      Log.i(TAG,
            "Hardware does not support VP8 encoding, skipping testInitReleaseUsingByteBuffer");
      return;
    }
    MediaCodecVideoEncoder encoder = new MediaCodecVideoEncoder();
    assertTrue(encoder.initEncode(
        MediaCodecVideoEncoder.VideoCodecType.VIDEO_CODEC_VP8, 640, 480, 300, 30, null));
    encoder.release();
  }

  @SmallTest
  public static void testInitilizeUsingTextures() {
    if (!MediaCodecVideoEncoder.isVp8HwSupportedUsingTextures()) {
      Log.i(TAG, "hardware does not support VP8 encoding, skipping testEncoderUsingTextures");
      return;
    }
    EglBase eglBase = EglBase.create();
    MediaCodecVideoEncoder encoder = new MediaCodecVideoEncoder();
    assertTrue(encoder.initEncode(
        MediaCodecVideoEncoder.VideoCodecType.VIDEO_CODEC_VP8, 640, 480, 300, 30,
        eglBase.getEglBaseContext()));
    encoder.release();
    eglBase.release();
  }

  @SmallTest
  public static void testInitializeUsingByteBufferReInitilizeUsingTextures() {
    if (!MediaCodecVideoEncoder.isVp8HwSupportedUsingTextures()) {
      Log.i(TAG, "hardware does not support VP8 encoding, skipping testEncoderUsingTextures");
      return;
    }
    MediaCodecVideoEncoder encoder = new MediaCodecVideoEncoder();
    assertTrue(encoder.initEncode(
        MediaCodecVideoEncoder.VideoCodecType.VIDEO_CODEC_VP8, 640, 480, 300, 30,
        null));
    encoder.release();
    EglBase eglBase = EglBase.create();
    assertTrue(encoder.initEncode(
        MediaCodecVideoEncoder.VideoCodecType.VIDEO_CODEC_VP8, 640, 480, 300, 30,
        eglBase.getEglBaseContext()));
    encoder.release();
    eglBase.release();
  }

  @SmallTest
  public static void testEncoderUsingByteBuffer() throws InterruptedException {
    if (!MediaCodecVideoEncoder.isVp8HwSupported()) {
      Log.i(TAG, "Hardware does not support VP8 encoding, skipping testEncoderUsingByteBuffer");
      return;
    }

    final int width = 640;
    final int height = 480;
    final int min_size = width * height * 3 / 2;
    final long presentationTimestampUs = 2;

    MediaCodecVideoEncoder encoder = new MediaCodecVideoEncoder();

    assertTrue(encoder.initEncode(
        MediaCodecVideoEncoder.VideoCodecType.VIDEO_CODEC_VP8, width, height, 300, 30, null));
    ByteBuffer[] inputBuffers = encoder.getInputBuffers();
    assertNotNull(inputBuffers);
    assertTrue(min_size <= inputBuffers[0].capacity());

    int bufferIndex;
    do {
      Thread.sleep(10);
      bufferIndex = encoder.dequeueInputBuffer();
    } while (bufferIndex == -1); // |-1| is returned when there is no buffer available yet.

    assertTrue(bufferIndex >= 0);
    assertTrue(bufferIndex < inputBuffers.length);
    assertTrue(encoder.encodeBuffer(true, bufferIndex, min_size, presentationTimestampUs));

    OutputBufferInfo info;
    do {
      info = encoder.dequeueOutputBuffer();
      Thread.sleep(10);
    } while (info == null);
    assertTrue(info.index >= 0);
    assertEquals(presentationTimestampUs, info.presentationTimestampUs);
    assertTrue(info.buffer.capacity() > 0);
    encoder.releaseOutputBuffer(info.index);

    encoder.release();
  }

  @SmallTest
  public static void testEncoderUsingTextures() throws InterruptedException {
    if (!MediaCodecVideoEncoder.isVp8HwSupportedUsingTextures()) {
      Log.i(TAG, "Hardware does not support VP8 encoding, skipping testEncoderUsingTextures");
      return;
    }

    final int width = 640;
    final int height = 480;
    final long presentationTs = 2;

    final EglBase eglOesBase = EglBase.create(null, EglBase.CONFIG_PIXEL_BUFFER);
    eglOesBase.createDummyPbufferSurface();
    eglOesBase.makeCurrent();
    int oesTextureId = GlUtil.generateTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);

    // TODO(perkj): This test is week since we don't fill the texture with valid data with correct
    // width and height and verify the encoded data. Fill the OES texture and figure out a way to
    // verify that the output make sense.

    MediaCodecVideoEncoder encoder = new MediaCodecVideoEncoder();

    assertTrue(encoder.initEncode(
        MediaCodecVideoEncoder.VideoCodecType.VIDEO_CODEC_VP8, width, height, 300, 30,
        eglOesBase.getEglBaseContext()));
    assertTrue(encoder.encodeTexture(true, oesTextureId, RendererCommon.identityMatrix(),
        presentationTs));
    GlUtil.checkNoGLES2Error("encodeTexture");

    // It should be Ok to delete the texture after calling encodeTexture.
    GLES20.glDeleteTextures(1, new int[] {oesTextureId}, 0);

    OutputBufferInfo info = encoder.dequeueOutputBuffer();
    while (info == null) {
      info = encoder.dequeueOutputBuffer();
      Thread.sleep(20);
    }
    assertTrue(info.index != -1);
    assertTrue(info.buffer.capacity() > 0);
    encoder.releaseOutputBuffer(info.index);

    encoder.release();
    eglOesBase.release();
  }
}
