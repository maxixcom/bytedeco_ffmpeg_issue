package example;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.*;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

/**
 * Based on https://github.com/FFmpeg/FFmpeg/blob/master/doc/examples/remux.c
 */
public class Main implements Runnable {
    private final String in_filename;
    private final String out_filename;

    public Main(String inFilename, String outFilename) {
        in_filename = inFilename;
        out_filename = outFilename;
    }

    @Override
    public void run() {
        AVPacket pkt = null;
        AVFormatContext ifmt_ctx = new AVFormatContext((Pointer) null);
        AVFormatContext ofmt_ctx = new AVFormatContext((Pointer) null);

        AVOutputFormat ofmt = null;
        int ret = 0;

        int stream_index = 0;
        Map<Integer, Integer> stream_mapping = new HashMap<>();

        AVIOContext pb;

        pkt = av_packet_alloc();
        if (pkt == null) {
            System.err.print("Could not allocate AVPacket\n");
            return;
        }
        try {
            if ((ret = avformat_open_input(ifmt_ctx, in_filename, null, null)) < 0) {
                System.err.printf("Could not open input file '%s'", in_filename);
                return;
            }

            if ((ret = avformat_find_stream_info(ifmt_ctx, (AVDictionary) null)) < 0) {
                System.err.print("Failed to retrieve input stream information\n");
                return;
            }

            av_dump_format(ifmt_ctx, 0, in_filename, 0);

            avformat_alloc_output_context2(ofmt_ctx, null, null, out_filename);
            if (ofmt_ctx == null) {
                System.err.print("Could not create output context\n");
                ret = AVERROR_UNKNOWN;
                return;
            }

            ofmt = ofmt_ctx.oformat();

            for (int i = 0; i < ifmt_ctx.nb_streams(); i++) {
                AVStream out_stream;
                AVStream in_stream = ifmt_ctx.streams(i);
                AVCodecParameters in_codecpar = in_stream.codecpar();

                if (in_codecpar.codec_type() != AVMEDIA_TYPE_AUDIO &&
                        in_codecpar.codec_type() != AVMEDIA_TYPE_VIDEO &&
                        in_codecpar.codec_type() != AVMEDIA_TYPE_SUBTITLE) {
                    continue;
                }

                stream_mapping.put(i, stream_index++);

                out_stream = avformat_new_stream(ofmt_ctx, null);
                if (out_stream == null) {
                    System.err.print("Failed allocating output stream\n");
                    ret = AVERROR_UNKNOWN;
                    return;
                }

                ret = avcodec_parameters_copy(out_stream.codecpar(), in_codecpar);
                if (ret < 0) {
                    System.err.print("Failed to copy codec parameters\n");
                    return;
                }
                out_stream.codecpar().codec_tag(0);
            }
            av_dump_format(ofmt_ctx, 0, out_filename, 1);

//            if ((ofmt.flags() & AVFMT_NOFILE) == 0) {
//                ret = avio_open(pb, out_filename, AVIO_FLAG_WRITE);
//                if (ret < 0) {
//                    System.err.printf("Could not open output file '%s'\n", out_filename);
//                    return;
//                }
//            }
//            ofmt_ctx.pb(pb);

            outputStreams.put(ofmt_ctx, new ByteArrayOutputStream());
            pb = this.getAVIOContextForWrite(ofmt_ctx);
            ofmt_ctx.pb(pb);

            ret = avformat_write_header(ofmt_ctx, (AVDictionary) null);
            if (ret < 0) {
                System.err.print("Error occurred when opening output file\n");
                return;
            }

            while (true) {
                AVStream in_stream, out_stream;

                ret = av_read_frame(ifmt_ctx, pkt);
                if (ret < 0) {
                    break;
                }

                in_stream = ifmt_ctx.streams(pkt.stream_index());
                if (!stream_mapping.containsKey(pkt.stream_index())) {
                    av_packet_unref(pkt);
                    continue;
                }


                pkt.stream_index(stream_mapping.get(pkt.stream_index()));
                out_stream = ofmt_ctx.streams(pkt.stream_index());
                System.out.printf("(in) Stream %d PTS %d, DTS %d, Duration %d\n",
                        pkt.stream_index(), pkt.pts(), pkt.dts(), pkt.duration());

                av_packet_rescale_ts(pkt, in_stream.time_base(), out_stream.time_base());
                pkt.pos(-1);

                System.out.printf("(out) Stream %d PTS %d, DTS %d, Duration %d\n",
                        pkt.stream_index(), pkt.pts(), pkt.dts(), pkt.duration());

                ret = av_interleaved_write_frame(ofmt_ctx, pkt);
                /* pkt is now blank (av_interleaved_write_frame() takes ownership of
                 * its contents and resets pkt), so that no unreferencing is necessary.
                 * This would be different if one used av_write_frame(). */
                if (ret < 0) {
                    System.out.print("Error muxing packet\n");
                    break;
                }
            }

            av_write_trailer(ofmt_ctx);


            ByteArrayOutputStream outputStream = outputStreams.get(ofmt_ctx);
            try (FileOutputStream fileOutputStream = new FileOutputStream(out_filename)) {
                fileOutputStream.write(outputStream.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } finally {
            av_packet_free(pkt);
            avformat_close_input(ifmt_ctx);

            /* close output */
            // TODO: clean pb
//            if (ofmt_ctx != null && ofmt != null && ((ofmt.flags() & AVFMT_NOFILE) == 0)) {
//                avio_closep(ofmt_ctx.pb());
//            }
            avformat_free_context(ofmt_ctx);

            if (ret < 0 && ret != AVERROR_EOF) {
                System.err.printf("Error occurred: %d\n", ret);
            }
        }
    }

    private AVIOContext getAVIOContextForWrite(AVFormatContext fmt_ctx) {
        final int BUFFER_SIZE=0x1000;
        BytePointer bytePointer = new BytePointer(av_malloc(BUFFER_SIZE));

        AVIOContext avioContext = avio_alloc_context(
                bytePointer,
                BUFFER_SIZE,
                1,
                fmt_ctx,
                null,
                writeCallback,
                null
        );


        return avioContext;
    }


    public static void main(String[] args) {
        Main main = new Main(
                "media/sample-1.mp4",
                "media/sample-1.ts"
        );

        main.run();
        writeCallback.releaseReference();
    }

    static Map<Pointer, ByteArrayOutputStream> outputStreams = new ConcurrentHashMap<>();
    static WriteCallback writeCallback = new WriteCallback().retainReference();

    static class WriteCallback extends Write_packet_Pointer_BytePointer_int {
        @Override
        public int call(Pointer opaque, BytePointer buf, int buf_size) {
            byte[] byte_buf = new byte[buf_size];
            buf.get(byte_buf);
            OutputStream outputStream = outputStreams.get(opaque);
            try {
                outputStream.write(byte_buf);
            } catch (IOException e) {
                System.err.printf("Error writing to outpu stream: %s", e.getMessage());
                return -1;
            }
            System.out.printf("Write block size %d\n", buf_size);
            return buf_size;
        }
    }


}