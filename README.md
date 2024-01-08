# bytedeco ffmpeg issue example (Solved)

This code  is based on ffmpeg example [remux.c](https://github.com/FFmpeg/FFmpeg/blob/master/doc/examples/remux.c)
Program blocks on writing to output file (I guess) with `av_interleaved_write_frame`.

It creates file but there is no any bytes in it. It prints info (see below) for detected input and output,
then it starts to collect packets and when it has video + audio and ready to write it hangs. 

```shell
Input #0, mov,mp4,m4a,3gp,3g2,mj2, from 'media/sample-1.mp4':
  Metadata:
    major_brand     : isom
    minor_version   : 512
    compatible_brands: isomiso2avc1mp41
    title           : x
    comment         : x
    copyright       : x
    description     : x
  Duration: 00:00:21.48, start: 0.000000, bitrate: 2066 kb/s
  Stream #0:0[0x1](und): Video: h264 (High) (avc1 / 0x31637661), yuv420p(tv, bt709, progressive), 1920x960 [SAR 1:1 DAR 2:1], 1870 kb/s, 24 fps, 24 tbr, 12288 tbn (default)
    Metadata:
      handler_name    : VideoHandler
      vendor_id       : [0][0][0][0]
      encoder         : Lavc libx264
  Stream #0:1[0x2](und): Audio: aac (LC) (mp4a / 0x6134706D), 44100 Hz, stereo, fltp, 194 kb/s (default)
    Metadata:
      handler_name    : SoundHandler
      vendor_id       : [0][0][0][0]
Output #0, mpegts, to 'media/sample-1.ts':
  Stream #0:0: Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x960 [SAR 1:1 DAR 2:1], q=2-31, 1870 kb/s
  Stream #0:1: Audio: aac (LC), 44100 Hz, stereo, fltp, 194 kb/s
(in) Stream 0 PTS 0, DTS -1024, Duration 512
(out) Stream 0 PTS 0, DTS -7500, Duration 3750
(in) Stream 0 PTS 1536, DTS -512, Duration 512
(out) Stream 0 PTS 11250, DTS -3750, Duration 3750
(in) Stream 1 PTS -1024, DTS -1024, Duration 1024
(out) Stream 1 PTS -2090, DTS -2090, Duration 2090


Process finished with exit code 130 (interrupted by signal 2:SIGINT)
```

## Compile and Run with mvn wrapper

```shell
./mvnw compile exec:java
```

## Issue

[https://github.com/bytedeco/javacpp-presets/issues/1457#event-11410663172](https://github.com/bytedeco/javacpp-presets/issues/1457#event-11410663172)




## Links

Custom IO

https://groups.google.com/g/javacpp-project/c/kqEH8PwC1-Q
https://github.com/bytedeco/javacpp/issues/539