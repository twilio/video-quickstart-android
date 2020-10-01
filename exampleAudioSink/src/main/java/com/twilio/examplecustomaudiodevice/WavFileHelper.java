package com.twilio.examplecustomaudiodevice;

import android.content.Context;
import android.media.AudioFormat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WavFileHelper {

    private static final String fileName = "/audio_sink.wav";
    private boolean didWriteWavHeader;
    private boolean didCompleteWavHeader;
    private final String fullFilePath;
    private FileOutputStream fileOutputStream;
    private File outputFile;

    WavFileHelper(Context context) {
        fullFilePath = context.getFilesDir().getPath() + fileName;
    }

    /**
     * Writes the proper 44-byte RIFF/WAVE header to/for the given stream Two size fields are left
     * empty/null since we do not yet know the final stream size
     *
     * @param out         The stream to write the header to
     * @param channelMask An AudioFormat.CHANNEL_* mask
     * @param sampleRate  The sample rate in hertz
     * @param encoding    An AudioFormat.ENCODING_PCM_* value
     * @throws IOException
     */
    private static void writeWavHeader(
            OutputStream out, int channelMask, int sampleRate, int encoding) throws IOException {
        short channels;
        switch (channelMask) {
            case AudioFormat.CHANNEL_IN_MONO:
                channels = 1;
                break;
            case AudioFormat.CHANNEL_IN_STEREO:
                channels = 2;
                break;
            default:
                throw new IllegalArgumentException("Unacceptable channel mask");
        }

        short bitDepth;
        switch (encoding) {
            case AudioFormat.ENCODING_PCM_8BIT:
                bitDepth = 8;
                break;
            case AudioFormat.ENCODING_PCM_16BIT:
                bitDepth = 16;
                break;
            case AudioFormat.ENCODING_PCM_FLOAT:
                bitDepth = 32;
                break;
            default:
                throw new IllegalArgumentException("Unacceptable encoding");
        }

        writeWavHeader(out, channels, sampleRate, bitDepth);
    }

    /**
     * Writes the proper 44-byte RIFF/WAVE header to/for the given stream Two size fields are left
     * empty/null since we do not yet know the final stream size
     *
     * @param out        The stream to write the header to
     * @param channels   The number of channels
     * @param sampleRate The sample rate in hertz
     * @param bitDepth   The bit depth
     * @throws IOException
     */
    private static void writeWavHeader(
            OutputStream out, short channels, int sampleRate, short bitDepth) throws IOException {
        // Convert the multi-byte integers to raw bytes in little endian format as required by the
        // spec
        byte[] littleBytes =
                ByteBuffer.allocate(14)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putShort(channels)
                        .putInt(sampleRate)
                        .putInt(sampleRate * channels * (bitDepth / 8))
                        .putShort((short) (channels * (bitDepth / 8)))
                        .putShort(bitDepth)
                        .array();

        // Not necessarily the best, but it's very easy to visualize this way
        out.write(
                new byte[]{
                        // RIFF header
                        'R',
                        'I',
                        'F',
                        'F', // ChunkID
                        0,
                        0,
                        0,
                        0, // ChunkSize (must be updated later)
                        'W',
                        'A',
                        'V',
                        'E', // Format
                        // fmt subchunk
                        'f',
                        'm',
                        't',
                        ' ', // Subchunk1ID
                        16,
                        0,
                        0,
                        0, // Subchunk1Size
                        1,
                        0, // AudioFormat
                        littleBytes[0],
                        littleBytes[1], // NumChannels
                        littleBytes[2],
                        littleBytes[3],
                        littleBytes[4],
                        littleBytes[5], // SampleRate
                        littleBytes[6],
                        littleBytes[7],
                        littleBytes[8],
                        littleBytes[9], // ByteRate
                        littleBytes[10],
                        littleBytes[11], // BlockAlign
                        littleBytes[12],
                        littleBytes[13], // BitsPerSample
                        // data subchunk
                        'd',
                        'a',
                        't',
                        'a', // Subchunk2ID
                        0,
                        0,
                        0,
                        0, // Subchunk2Size (must be updated later)
                });
    }

    /**
     * Updates the given wav file's header to include the final chunk sizes
     *
     * @param wav The wav file to update
     * @throws IOException
     */
    private static void updateWavHeader(File wav) throws IOException {
        byte[] sizes =
                ByteBuffer.allocate(8)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        // There are probably a bunch of different/better ways to calculate
                        // these two given your circumstances. Cast should be safe since if the WAV
                        // is
                        // > 4 GB we've already made a terrible mistake.
                        .putInt((int) (wav.length() - 8)) // ChunkSize
                        .putInt((int) (wav.length() - 44)) // Subchunk2Size
                        .array();

        RandomAccessFile accessWave = null;
        //noinspection CaughtExceptionImmediatelyRethrown
        try {
            accessWave = new RandomAccessFile(wav, "rw");
            // ChunkSize
            accessWave.seek(4);
            accessWave.write(sizes, 0, 4);

            // Subchunk2Size
            accessWave.seek(40);
            accessWave.write(sizes, 4, 4);
        } catch (IOException ex) {
            // Rethrow but we still close accessWave in our finally
            throw ex;
        } finally {
            if (accessWave != null) {
                try {
                    accessWave.close();
                } catch (IOException ex) {
                    //
                }
            }
        }
    }

    public File getOutputFile() {
        return outputFile;
    }

    public String getFullFilePath() {
        return fullFilePath;
    }

    boolean doesFileExist() {
        if (outputFile == null) return false;
        return outputFile.exists();
    }

    boolean isFileWriteInProgress() {
        return didWriteWavHeader && !didCompleteWavHeader;
    }


    /*
     * The following wav header helper functions are from this gist: https://gist.github.com/kmark/d8b1b01fb0d2febf5770
     */

    void createFile() throws IOException {
        outputFile = new File(fullFilePath);
        if (outputFile.exists()) {
            outputFile.delete();
        }
        outputFile.createNewFile();
        fileOutputStream = new FileOutputStream(outputFile, true);
        didWriteWavHeader = false;
        didCompleteWavHeader = false;
    }

    void writeBytesToFile(ByteBuffer byteBuffer, int encoding, int sampleRate, int channels) throws IOException {
        if (!didWriteWavHeader) {
            writeWavHeader(fileOutputStream, getChannelMask(channels), sampleRate, encoding);
            didWriteWavHeader = true;
        }
        fileOutputStream.write(byteBuffer.array());
    }

    void finish() throws IOException {
        fileOutputStream.close();
        outputFile.setReadable(true);
        updateWavHeader(outputFile);
        didCompleteWavHeader = true;
    }

    private int getChannelMask(int channels) {
        switch (channels) {
            case 1:
                return AudioFormat.CHANNEL_IN_MONO;
            case 2:
                return AudioFormat.CHANNEL_IN_STEREO;
        }
        return AudioFormat.CHANNEL_IN_STEREO;
    }

}
