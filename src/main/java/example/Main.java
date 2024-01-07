package example;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVOutputFormat;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

/**
 * Based on https://github.com/FFmpeg/FFmpeg/blob/master/doc/examples/remux.c
 */
public class Main implements Runnable {
    private final String in_filename;
    private final String out_filename;

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

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

        AVIOContext pb = new AVIOContext();

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

            ofmt_ctx.pb(pb);
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

            avio_open_dyn_buf(pb);
            ofmt_ctx.pb(pb);

            ret = avformat_write_header(ofmt_ctx, (AVDictionary) null);
            if (ret < 0) {
                System.err.print("Error occurred when opening output file\n");
                return;
            }

            BytePointer bufHeader = new BytePointer();
            int num_bytes = avio_close_dyn_buf(pb, bufHeader);
            if(num_bytes>0) {
                byte[] buf_arr = new byte[num_bytes];
                bufHeader.get(buf_arr);
                System.out.printf("Header Buf bytes: %d, array length: %d\n", num_bytes, buf_arr.length);
                try {
                    outputStream.write(buf_arr);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            av_free(bufHeader);


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

//                if(pkt.pos()<513782) {
//                    av_packet_unref(pkt);
//                    continue;
//                }
//
//                if(pkt.pos()> 1929659) {
//                    av_packet_unref(pkt);
//                    break;
//                }

                pkt.stream_index(stream_mapping.get(pkt.stream_index()));
                out_stream = ofmt_ctx.streams(pkt.stream_index());
//                System.out.printf("(in) Stream %d Pos %d PTS %d, DTS %d, Duration %d\n",
//                        pkt.stream_index(), pkt.pos(), pkt.pts(), pkt.dts(), pkt.duration());

                av_packet_rescale_ts(pkt, in_stream.time_base(), out_stream.time_base());
//                pkt.pos(-1);

//                System.out.printf("(out) Stream %d Pos %d PTS %d, DTS %d, Duration %d\n",
//                        pkt.stream_index(), pkt.pos(), pkt.pts(), pkt.dts(), pkt.duration());

                // init dyn buf
                avio_open_dyn_buf(pb);
                ofmt_ctx.pb(pb);

                ret = av_interleaved_write_frame(ofmt_ctx, pkt);
//                ret = av_write_frame(ofmt_ctx, pkt);
                /* pkt is now blank (av_interleaved_write_frame() takes ownership of
                 * its contents and resets pkt), so that no unreferencing is necessary.
                 * This would be different if one used av_write_frame(). */
                if (ret < 0) {
                    System.out.print("Error muxing packet\n");
                    break;
                }

                // We can do it all the time even with trailer
                // get written bytes data
                BytePointer buf = new BytePointer();
                num_bytes = avio_close_dyn_buf(pb, buf);
                if(num_bytes>0) {
                    byte[] buf_arr = new byte[num_bytes];
                    buf.get(buf_arr);
                    System.out.printf("Buf bytes: %d, array length: %d\n", num_bytes, buf_arr.length);
                    try {
                        outputStream.write(buf_arr);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                av_free(buf);
            }

            // init dyn buf
            avio_open_dyn_buf(pb);
            ofmt_ctx.pb(pb);

            av_write_trailer(ofmt_ctx);

            BytePointer buf = new BytePointer();
            int num_bytes_trailer = avio_close_dyn_buf(pb, buf);
            if(num_bytes_trailer>0) {
                byte[] buf_arr = new byte[num_bytes_trailer];
                buf.get(buf_arr);
                System.out.printf("Buf trailer bytes: %d, array length: %d\n", num_bytes_trailer, buf_arr.length);
                try {
                    outputStream.write(buf_arr);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            av_free(buf);

        } finally {
            av_packet_free(pkt);
            avformat_close_input(ifmt_ctx);

            /* close output */
//            if (ofmt_ctx != null && ofmt != null && ((ofmt.flags() & AVFMT_NOFILE) == 0)) {
//                avio_closep(ofmt_ctx.pb());
//            }
            avformat_free_context(ofmt_ctx);

            if (ret < 0 && ret != AVERROR_EOF) {
                System.err.printf("Error occurred: %d\n", ret);
            }
        }

        try(FileOutputStream fileOutputStream = new FileOutputStream(out_filename, false)) {
            fileOutputStream.write(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static void main(String[] args) {
        Main main = new Main(
                "media/sample-1.mp4",
                "media/sample-1.ts"
        );

        main.run();
    }

}