package example;

import org.bytedeco.ffmpeg.avformat.AVInputFormat;
import org.bytedeco.ffmpeg.avformat.AVOutputFormat;
import org.bytedeco.javacpp.Pointer;

import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.ffmpeg.global.avformat.av_demuxer_iterate;
import static org.bytedeco.ffmpeg.global.avformat.av_muxer_iterate;

public class ListFeatures {
    public static void listMuxers() {
        try(Pointer opaque=new Pointer()) {
            opaque.retainReference();
            AVOutputFormat avOutputFormat;

            List<String> list = new ArrayList<>();
            while ((avOutputFormat = av_muxer_iterate(opaque)) != null) {
                list.add(String.format("%s (v.%d:a.%d:s.%d): %s",
                        avOutputFormat.name().getString(),
                        avOutputFormat.video_codec(),
                        avOutputFormat.audio_codec(),
                        avOutputFormat.subtitle_codec(),
                        avOutputFormat.long_name().getString()
                ));
            }
            list.stream().sorted().forEach(System.out::println);
        }
    }

    public static void listDemuxers() {
        try(Pointer opaque=new Pointer()) {
            opaque.retainReference();
            AVInputFormat avInputFormat;

            List<String> list = new ArrayList<>();
            while ((avInputFormat = av_demuxer_iterate(opaque)) != null) {
                list.add(String.format("%s (%d): %s",
                        avInputFormat.name().getString(),
                        avInputFormat.raw_codec_id(),
                        avInputFormat.long_name().getString()
                ));
            }
            list.stream().sorted().forEach(System.out::println);
        }
    }

    public static void main(String[] args) {
//        ListFeatures.listMuxers();
//        ListFeatures.listDemuxers();
    }
}
