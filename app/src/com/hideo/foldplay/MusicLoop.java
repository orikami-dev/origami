package com.hideo.foldplay;

import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Boucle musicale sans blanc (0.62.1) : MediaPlayer marque un temps à chaque tour de boucle. La
 * musique est décodée une fois en PCM, puis un fil la réécrit sans fin dans un AudioTrack.
 * Les boucles sont des nombres entiers de secondes (tools/make-sounds.py) : on recoupe à la seconde
 * près pour retirer le rembourrage du codeur.
 */
final class MusicLoop {
    private final short[] pcm;
    private final int rate, channels;
    private final AudioTrack track;
    private volatile boolean running = true, paused;
    private volatile float volume, target;
    private final Thread thread;

    MusicLoop(AssetFileDescriptor fd) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        MediaFormat f = ex.getTrackFormat(0);
        ex.selectTrack(0);
        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        MediaCodec codec = MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME));
        codec.configure(f, null, null, 0);
        codec.start();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(rate * ch * 2 * 70);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inEnd = false, outEnd = false;
        byte[] chunk = new byte[0];
        while (!outEnd) {
            if (!inEnd) {
                int i = codec.dequeueInputBuffer(10000);
                if (i >= 0) {
                    ByteBuffer b = codec.getInputBuffer(i);
                    int n = ex.readSampleData(b, 0);
                    if (n < 0) {
                        codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inEnd = true;
                    } else {
                        codec.queueInputBuffer(i, 0, n, ex.getSampleTime(), 0);
                        ex.advance();
                    }
                }
            }
            int o = codec.dequeueOutputBuffer(info, 10000);
            if (o >= 0) {
                ByteBuffer b = codec.getOutputBuffer(o);
                if (info.size > 0 && b != null) {
                    if (chunk.length < info.size) chunk = new byte[info.size];
                    b.position(info.offset);
                    b.get(chunk, 0, info.size);
                    out.write(chunk, 0, info.size);
                }
                codec.releaseOutputBuffer(o, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outEnd = true;
            } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                ch = codec.getOutputFormat().getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            }
        }
        codec.stop();
        codec.release();
        ex.release();
        channels = ch;
        byte[] raw = out.toByteArray();
        int frames = raw.length / 2 / channels;
        int whole = (frames / rate) * rate;
        if (whole > 0 && frames - whole < rate / 4) frames = whole;
        pcm = new short[frames * channels];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
        int min = AudioTrack.getMinBufferSize(rate,
                channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO)
                        .build())
                .setBufferSizeInBytes(Math.max(min * 2, rate * channels / 5))
                .setTransferMode(AudioTrack.MODE_STREAM).build();
        track.setVolume(0f);
        thread = new Thread(new Runnable() {
            public void run() {
                loop();
            }
        }, "origami-musique");
    }

    /** Volume voulu ; le fil y glisse en douceur (fondu d'environ un quart de seconde). */
    void setVolume(float v) {
        target = v;
    }

    void play(float v) {
        target = v;
        paused = false;
        if (!thread.isAlive()) thread.start();
        track.play();
    }

    void pause() {
        paused = true;
    }

    boolean playing() {
        return !paused;
    }

    void release() {
        running = false;
        thread.interrupt();
    }

    private void loop() {
        int pos = 0, step = rate / 50 * channels;
        try {
            while (running) {
                if (paused && volume <= 0.001f) {
                    track.pause();
                    while (running && paused) {
                        try {
                            Thread.sleep(40);
                        } catch (InterruptedException e) {
                            if (!running) break;
                        }
                    }
                    if (!running) break;
                    track.play();
                }
                float goal = paused ? 0f : target;
                volume += (goal - volume) * 0.18f;
                if (Math.abs(goal - volume) < 0.002f) volume = goal;
                track.setVolume(volume);
                int n = Math.min(step, pcm.length - pos);
                track.write(pcm, pos, n);
                pos += n;
                if (pos >= pcm.length) pos = 0;
            }
        } catch (Exception ignored) {
            // Piste coupée par le système : on s'arrête sans bruit.
        } finally {
            try {
                track.stop();
            } catch (Exception ignored) {
            }
            track.release();
        }
    }
}
